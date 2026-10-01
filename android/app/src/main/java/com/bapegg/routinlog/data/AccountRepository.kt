package com.bapegg.routinlog.data

import android.app.Activity
import android.content.Context
import com.bapegg.routinlog.auth.EncryptedSessionStore
import com.bapegg.routinlog.auth.GoogleCredentialSignIn
import com.bapegg.routinlog.auth.SessionStore
import com.bapegg.routinlog.auth.StoredSession
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** ViewModel-facing interface. Production uses AccountRepository; fakes belong only in test sources. */
interface AccountDataSource {
    val identity: StateFlow<AccountIdentity?>
    suspend fun login(activity: Activity): AccountIdentity
    suspend fun restoreSession(): AccountIdentity?
    suspend fun logout()
    suspend fun deleteAccount(activity: Activity)
    suspend fun getProfile(): ProfileDto?
    suspend fun saveProfile(profile: ProfileDto): ProfileDto
    suspend fun listBody(from: String? = null, to: String? = null): List<BodyMeasurementDto>
    suspend fun saveBody(date: String, measurement: BodyMeasurementWriteDto): BodyMeasurementDto
    suspend fun deleteBody(date: String, version: Long)
}

/** Opaque server sessions only. Google ID tokens are exchanged once and never persisted. */
class AccountRepository internal constructor(
    private val api: AccountApi,
    private val store: SessionStore,
    private val google: GoogleCredentialSignIn,
    private val googleConfigured: Boolean,
    private val clearProviderState: suspend () -> Unit = {},
    private val now: () -> Long = { Instant.now().epochSecond },
) : AccountDataSource, MealDataSource, WorkoutDataSource {
    private val mutex = Mutex()
    @Volatile private var session: StoredSession? = null
    private val identityState = MutableStateFlow<AccountIdentity?>(null)
    override val identity: StateFlow<AccountIdentity?> = identityState.asStateFlow()

    override suspend fun login(activity: Activity): AccountIdentity {
        if (!googleConfigured) throw AccountException(AccountErrorKind.CONFIGURATION, "Google 로그인 설정이 아직 준비되지 않았어요.")
        val challenge = request { api.challenge() }.required()
        val challengeId = challenge.challengeId?.takeIf { it.isNotBlank() } ?: malformed()
        val nonce = challenge.nonce?.takeIf { it.isNotBlank() } ?: malformed()
        val idToken = google.idToken(activity, nonce)
        val tokens = request { api.login(GoogleLoginDto(idToken, challengeId)) }.required()
        return withContext(Dispatchers.IO) { mutex.withLock { install(tokens) } }
    }

    override suspend fun restoreSession(): AccountIdentity? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val restored = try { store.read() } catch (_: Exception) { throw storageError() }
            if (restored == null) { session = null; identityState.value = null; return@withLock null }
            session = restored
            // Validate with the server and rotate the refresh token on each process restoration.
            refreshLocked(restored)
            identityState.value
        }
    }

    override suspend fun logout() {
        if (session == null) return
        var sent: StoredSession? = null
        val response = authorized { current ->
            sent = current
            api.logout("Bearer ${current.accessToken}", LogoutRequestDto(current.refreshToken))
        }
        response.checkStatus()
        withContext(NonCancellable) {
            val cleared = withContext(Dispatchers.IO) {
                mutex.withLock {
                    // A concurrently completed sign-in must not be erased by the old logout response.
                    if (session?.refreshToken == sent?.refreshToken) { clearLocked(); true } else false
                }
            }
            if (cleared) clearProviderAfterRevocation()
        }
    }

    override suspend fun deleteAccount(activity: Activity) {
        if (!googleConfigured) throw AccountException(AccountErrorKind.CONFIGURATION, "Google 로그인 설정이 아직 준비되지 않았어요.")
        deleteAccountWithCredential { nonce -> google.idToken(activity, nonce) }
    }

    /** Credential UI is injected only at this internal transport boundary for JVM contract tests. */
    internal suspend fun deleteAccountWithCredential(credential: suspend (nonce: String) -> String) {
        val deleting = session ?: throw expired()
        val challenge = request { api.challenge() }.required()
        val challengeId = challenge.challengeId?.takeIf { it.isNotBlank() } ?: malformed()
        val nonce = challenge.nonce?.takeIf { it.isNotBlank() } ?: malformed()
        // A new server nonce and Google reauthentication are required for every deletion attempt.
        val idToken = credential(nonce).takeIf { it.isNotBlank() } ?: malformed()
        authorized { current ->
            if (current.userId != deleting.userId) throw changedAccount()
            api.deleteAccount("Bearer ${current.accessToken}", GoogleLoginDto(idToken, challengeId))
        }.checkStatus()
        // Once server deletion succeeds, cancellation must not leave a deleted account signed in locally.
        withContext(NonCancellable) {
            val cleared = withContext(Dispatchers.IO) {
                mutex.withLock {
                    if (session?.userId == deleting.userId) { clearLocked(); true } else false
                }
            }
            if (cleared) clearProviderAfterRevocation()
        }
    }

    private suspend fun clearProviderAfterRevocation() {
        // Provider picker cleanup is best effort; the server and local app session are already revoked.
        try { clearProviderState() } catch (_: Exception) { }
    }

    override suspend fun getProfile(): ProfileDto? {
        val response = authorized { api.getProfile("Bearer ${it.accessToken}") }
        if (response.code() == 404) {
            val error = response.error()
            if (error.code == "PROFILE_NOT_FOUND") return null
            throw error
        }
        return response.required()
    }

    override suspend fun saveProfile(profile: ProfileDto): ProfileDto =
        authorized { api.saveProfile("Bearer ${it.accessToken}", profile) }.required()

    override suspend fun listBody(from: String?, to: String?): List<BodyMeasurementDto> {
        val last = to ?: LocalDate.now().toString()
        val first = from ?: runCatching { LocalDate.parse(last).minusDays(30).toString() }.getOrElse {
            throw AccountException(AccountErrorKind.VALIDATION, "조회할 날짜를 확인해주세요.")
        }
        return authorized { api.listBody("Bearer ${it.accessToken}", first, last) }.required().items
    }

    override suspend fun saveBody(date: String, measurement: BodyMeasurementWriteDto): BodyMeasurementDto =
        authorized { api.saveBody("Bearer ${it.accessToken}", date, measurement) }.required()

    override suspend fun deleteBody(date: String, version: Long) {
        authorized { api.deleteBody("Bearer ${it.accessToken}", date, version) }.checkStatus()
    }

    override suspend fun listFoods(): List<FoodDto> = authorized { api.listFoods("Bearer ${it.accessToken}") }.required().items

    override suspend fun saveFood(id: String, food: FoodWrite): FoodDto =
        authorized { api.saveFood("Bearer ${it.accessToken}", id, food) }.required()

    override suspend fun deleteFood(id: String, version: Long) {
        authorized { api.deleteFood("Bearer ${it.accessToken}", id, version) }.checkStatus()
    }

    override suspend fun listMealTemplates(): List<MealTemplateDto> =
        authorized { api.listMealTemplates("Bearer ${it.accessToken}") }.required().items

    override suspend fun saveMealTemplate(id: String, template: MealTemplateWrite): MealTemplateDto =
        authorized { api.saveMealTemplate("Bearer ${it.accessToken}", id, template) }.required()

    override suspend fun deleteMealTemplate(id: String, version: Long) {
        authorized { api.deleteMealTemplate("Bearer ${it.accessToken}", id, version) }.checkStatus()
    }

    override suspend fun getMealPlan(): MealPlanDto = authorized { api.getMealPlan("Bearer ${it.accessToken}") }.required()

    override suspend fun saveMealPlan(plan: MealPlanWrite): MealPlanDto =
        authorized { api.saveMealPlan("Bearer ${it.accessToken}", plan) }.required()

    override suspend fun getMealDay(date: String): MealDayDto = authorized { api.getMealDay("Bearer ${it.accessToken}", date) }.required()

    override suspend fun saveMeal(id: String, meal: MealWrite): MealDto = authorized { api.saveMeal("Bearer ${it.accessToken}", id, meal) }.required()

    override suspend fun deleteMeal(id: String, version: Long) {
        authorized { api.deleteMeal("Bearer ${it.accessToken}", id, version) }.checkStatus()
    }

    override suspend fun listExercises(): List<ExerciseDto> = authorized { api.listExercises("Bearer ${it.accessToken}") }.required().items
    override suspend fun saveExercise(id: String, exercise: ExerciseWrite): ExerciseDto = authorized { api.saveExercise("Bearer ${it.accessToken}", id, exercise) }.required()
    override suspend fun deleteExercise(id: String, version: Long) { authorized { api.deleteExercise("Bearer ${it.accessToken}", id, version) }.checkStatus() }
    override suspend fun listRoutines(): List<RoutineDto> = authorized { api.listRoutines("Bearer ${it.accessToken}") }.required().items
    override suspend fun saveRoutine(id: String, routine: RoutineWrite): RoutineDto = authorized { api.saveRoutine("Bearer ${it.accessToken}", id, routine) }.required()
    override suspend fun deleteRoutine(id: String, version: Long) { authorized { api.deleteRoutine("Bearer ${it.accessToken}", id, version) }.checkStatus() }
    override suspend fun getWorkoutPlan(): WorkoutPlanDto = authorized { api.getWorkoutPlan("Bearer ${it.accessToken}") }.required()
    override suspend fun saveWorkoutPlan(plan: WorkoutPlanWrite): WorkoutPlanDto = authorized { api.saveWorkoutPlan("Bearer ${it.accessToken}", plan) }.required()
    override suspend fun saveWorkoutOverride(date: String, override: WorkoutOverrideWrite): WorkoutOverrideDto = authorized { api.saveWorkoutOverride("Bearer ${it.accessToken}", date, override) }.required()
    override suspend fun deleteWorkoutOverride(date: String, version: Long) { authorized { api.deleteWorkoutOverride("Bearer ${it.accessToken}", date, version) }.checkStatus() }
    override suspend fun getWorkoutDays(from: String, to: String): List<WorkoutDayDto> = authorized { api.getWorkoutDays("Bearer ${it.accessToken}", from, to) }.required().items
    override suspend fun startWorkoutSession(id: String, start: WorkoutStartWrite): WorkoutSessionDto = authorized { api.startWorkoutSession("Bearer ${it.accessToken}", id, start) }.required()
    override suspend fun saveWorkoutSession(id: String, session: WorkoutSessionWrite): WorkoutSessionDto = authorized { api.saveWorkoutSession("Bearer ${it.accessToken}", id, session) }.required()
    override suspend fun deleteWorkoutSession(id: String, version: Long) { authorized { api.deleteWorkoutSession("Bearer ${it.accessToken}", id, version) }.checkStatus() }
    override suspend fun getWorkoutHistory(exerciseId: String, before: String): List<WorkoutHistoryItem> = authorized { api.getWorkoutHistory("Bearer ${it.accessToken}", exerciseId, before) }.required().items

    /** Exactly one refresh for concurrent requests rejected with the same old access token. */
    private suspend fun <T> authorized(call: suspend (StoredSession) -> Response<T>): Response<T> {
        val initial = session ?: throw expired()
        val ready = if (initial.expiresAtEpochSeconds <= now() + 15) refreshFor(initial) else initial
        val first = request { call(ready) }
        if (session?.userId != ready.userId) { first.errorBody()?.close(); throw changedAccount() }
        if (first.code() != 401) return first
        first.errorBody()?.close()
        val refreshed = refreshFor(ready)
        val retried = request { call(refreshed) }
        if (session?.userId != refreshed.userId) { retried.errorBody()?.close(); throw changedAccount() }
        if (retried.code() == 401) {
            retried.errorBody()?.close()
            withContext(Dispatchers.IO) { mutex.withLock { if (session?.accessToken == refreshed.accessToken) clearLocked() } }
            throw expired()
        }
        return retried
    }

    private suspend fun refreshFor(rejected: StoredSession): StoredSession = withContext(Dispatchers.IO) {
        mutex.withLock {
            val latest = session ?: throw expired()
            if (latest.userId != rejected.userId) throw changedAccount()
            if (latest.accessToken != rejected.accessToken) latest else refreshLocked(latest)
        }
    }

    /** Must hold mutex. Network failures deliberately leave the prior encrypted session intact. */
    private suspend fun refreshLocked(previous: StoredSession): StoredSession {
        val response = request { api.refresh(RefreshRequestDto(previous.refreshToken)) }
        if (response.code() == 401) {
            response.errorBody()?.close(); clearLocked(); throw expired()
        }
        val tokens = response.required()
        if (tokens.userId != previous.userId) { clearLocked(); malformed() }
        install(tokens)
        return checkNotNull(session)
    }

    /** Persist rotated credentials before making them visible to requests or the UI. */
    private fun install(dto: AuthTokensDto): AccountIdentity {
        val access = dto.accessToken?.takeIf { it.isNotBlank() && it.length <= 16000 } ?: malformed()
        val refresh = dto.refreshToken?.takeIf { it.isNotBlank() && it.length <= 16000 } ?: malformed()
        val userId = dto.userId?.takeIf { it.isNotBlank() && it.length <= 128 } ?: malformed()
        val expires = dto.expiresIn?.takeIf { it in 1L..31_536_000L } ?: malformed()
        val value = StoredSession(access, refresh, now() + expires, userId)
        try { store.write(value) } catch (_: Exception) {
            // A rotated token that cannot be committed safely must not leave an old token in use.
            session = null; identityState.value = null; runCatching { store.clear() }; throw storageError()
        }
        session = value
        return AccountIdentity(userId).also { identityState.value = it }
    }

    private fun clearLocked() {
        session = null; identityState.value = null
        try { store.clear() } catch (_: Exception) { throw storageError() }
    }

    private suspend fun <T> request(block: suspend () -> Response<T>): Response<T> {
        try { return block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (known: AccountException) { throw known }
        catch (_: IOException) { throw AccountException(AccountErrorKind.NETWORK, "서버에 연결하지 못했어요. 인터넷 연결을 확인하고 다시 시도해주세요.") }
        catch (_: IllegalArgumentException) { throw AccountException(AccountErrorKind.VALIDATION, "입력한 값과 형식을 확인해주세요.") }
        catch (_: Exception) { throw AccountException(AccountErrorKind.SERVER, "서버 응답을 확인하지 못했어요. 잠시 후 다시 시도해주세요.") }
    }

    companion object {
        fun create(context: Context, baseUrl: String, debug: Boolean, googleWebClientId: String): AccountRepository {
            val app = context.applicationContext
            val google = GoogleCredentialSignIn(googleWebClientId)
            return AccountRepository(api(baseUrl, debug), EncryptedSessionStore(app), google, googleWebClientId.isNotBlank(), { google.clear(app) })
        }

        internal fun forTesting(baseUrl: String, store: SessionStore, clearProviderState: suspend () -> Unit = {}, now: () -> Long = { Instant.now().epochSecond }): AccountRepository =
            AccountRepository(api(baseUrl, true), store, GoogleCredentialSignIn(""), false, clearProviderState, now)

        private fun api(baseUrl: String, debug: Boolean): AccountApi {
            val url = baseUrl.toHttpUrlOrNull()
            if (url == null || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null || !baseUrl.endsWith("/")) {
                throw AccountException(AccountErrorKind.CONFIGURATION, "서버 주소 설정을 확인해주세요.")
            }
            if (!url.isHttps && !(debug && url.host in setOf("10.0.2.2", "localhost", "127.0.0.1"))) {
                throw AccountException(AccountErrorKind.CONFIGURATION, "안전한 HTTPS 서버 주소가 필요해요.")
            }
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
            return Retrofit.Builder().baseUrl(url).client(client)
                .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
                .build().create(AccountApi::class.java)
        }
    }
}

private fun expired() = AccountException(AccountErrorKind.EXPIRED, "로그인이 만료됐어요. 다시 로그인해주세요.", "SESSION_EXPIRED", 401)
private fun changedAccount() = AccountException(AccountErrorKind.CANCELLED, "로그인 상태가 바뀌어 이전 요청을 중단했어요.")
private fun storageError() = AccountException(AccountErrorKind.STORAGE, "이 기기에 로그인 정보를 안전하게 보관하지 못했어요. 다시 로그인해주세요.")
private fun malformed(): Nothing = throw AccountException(AccountErrorKind.SERVER, "서버 응답을 확인하지 못했어요. 다시 시도해주세요.")
private fun <T> Response<T>.required(): T { checkStatus(); return body() ?: malformed() }
private fun Response<*>.checkStatus() { if (!isSuccessful) throw error() }
private fun Response<*>.error(): AccountException {
    val knownCodes = setOf("PROFILE_NOT_FOUND", "MEASUREMENT_NOT_FOUND", "FOOD_NOT_FOUND", "TEMPLATE_NOT_FOUND", "MEAL_NOT_FOUND", "EXERCISE_NOT_FOUND", "ROUTINE_NOT_FOUND", "SESSION_NOT_FOUND", "OVERRIDE_NOT_FOUND", "SESSION_EXISTS", "REPLACEMENT_REQUIRES_NEW_ENTRY", "PROFILE_REQUIRED", "RESOURCE_IN_USE", "AUTHENTICATION_REQUIRED", "VERSION_CONFLICT", "VALIDATION_ERROR", "INVALID_REQUEST", "AUTH_NOT_CONFIGURED", "AUTH_INVALID", "AUTH_CHALLENGE_INVALID", "AUTH_REAUTH_REQUIRED", "AUTH_ACCOUNT_MISMATCH", "AUTH_RETRY", "GOOGLE_NOT_CONFIGURED", "GOOGLE_AUTH_NOT_CONFIGURED", "AUTH_PROVIDER_NOT_CONFIGURED", "SESSION_EXPIRED", "INVALID_TOKEN", "INVALID_REFRESH_TOKEN", "INVALID_CHALLENGE", "CHALLENGE_EXPIRED")
    val serverCode = runCatching {
        errorBody()?.use { body ->
            val reader = body.charStream()
            val buffer = CharArray(1024)
            val value = StringBuilder()
            while (value.length < 8192) {
                val count = reader.read(buffer, 0, minOf(buffer.size, 8192 - value.length))
                if (count < 0) break
                value.append(buffer, 0, count)
            }
            JsonParser.parseString(value.toString()).asJsonObject.get("code")?.asString?.takeIf { it in knownCodes }
        }
    }.getOrNull()
    val status = code()
    val kind = when {
        serverCode in setOf("AUTH_NOT_CONFIGURED", "GOOGLE_NOT_CONFIGURED", "GOOGLE_AUTH_NOT_CONFIGURED", "AUTH_PROVIDER_NOT_CONFIGURED") -> AccountErrorKind.CONFIGURATION
        status == 401 -> AccountErrorKind.EXPIRED
        status == 403 && serverCode in setOf("AUTH_REAUTH_REQUIRED", "AUTH_ACCOUNT_MISMATCH", "AUTH_CHALLENGE_INVALID") -> AccountErrorKind.CREDENTIAL
        status == 403 && serverCode == "PROFILE_REQUIRED" -> AccountErrorKind.VALIDATION
        status == 400 || status == 422 -> AccountErrorKind.VALIDATION
        status == 409 -> AccountErrorKind.CONFLICT
        status == 404 -> AccountErrorKind.NOT_FOUND
        else -> AccountErrorKind.SERVER
    }
    val message = when (kind) {
        AccountErrorKind.CONFIGURATION -> "로그인 서버 설정이 아직 준비되지 않았어요. 잠시 후 다시 시도해주세요."
        AccountErrorKind.EXPIRED -> "로그인이 만료됐어요. 다시 로그인해주세요."
        AccountErrorKind.CREDENTIAL -> if (serverCode == "AUTH_ACCOUNT_MISMATCH") "현재 로그인한 Google 계정으로 다시 확인해주세요." else "본인 확인을 완료하지 못했어요. Google 계정으로 다시 확인해주세요."
        AccountErrorKind.VALIDATION -> if (serverCode == "PROFILE_REQUIRED") "내 기본 정보와 건강정보 동의를 완료한 뒤 기록해주세요." else "입력한 값과 필수 항목을 확인해주세요."
        AccountErrorKind.CONFLICT -> when (serverCode) {
            "RESOURCE_IN_USE" -> "다른 계획에서 사용 중이에요. 연결을 해제한 뒤 삭제해주세요."
            "SESSION_EXISTS" -> "이 날짜의 운동 기록이 이미 있어요. 다시 불러온 뒤 이어서 기록해주세요."
            "REPLACEMENT_REQUIRES_NEW_ENTRY" -> "완료한 세트는 기존 종목에 남기고, 대체 종목을 새로 추가해주세요."
            else -> "다른 곳에서 기록이 변경됐어요. 최신 기록을 다시 확인해주세요."
        }
        AccountErrorKind.NOT_FOUND -> "요청한 기록을 찾을 수 없어요."
        else -> "요청을 완료하지 못했어요. 잠시 후 다시 시도해주세요."
    }
    return AccountException(kind, message, serverCode, status)
}
