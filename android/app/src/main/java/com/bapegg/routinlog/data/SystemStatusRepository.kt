package com.bapegg.routinlog.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

data class SystemStatusDto(
    val service: String? = null,
    val status: String? = null,
    val version: String? = null,
)

interface SystemStatusApi {
    @GET("api/v1/system/status")
    suspend fun status(): SystemStatusDto
}

class SystemStatusRepository private constructor(private val api: SystemStatusApi?) {
    suspend fun readVersion(): String {
        checkNotNull(api) { "API 주소가 설정되지 않았습니다." }
        val response = api.status()
        check(response.service == "routinlog" && response.status == "ready" &&
            !response.version.isNullOrBlank()) { "예상한 상태 응답과 다릅니다." }
        return requireNotNull(response.version)
    }

    companion object {
        fun create(baseUrl: String, debug: Boolean): SystemStatusRepository {
            if (baseUrl.isBlank()) return SystemStatusRepository(null)
            val url = requireNotNull(baseUrl.toHttpUrlOrNull()) { "API 주소 형식을 확인해주세요." }
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null &&
                url.fragment == null && baseUrl.endsWith("/")) { "API 기본 주소 형식을 확인해주세요." }
            require(url.isHttps || (debug && url.host in setOf("10.0.2.2", "localhost", "127.0.0.1"))) {
                "개발용 로컬 주소 이외에는 HTTPS가 필요합니다."
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()
            val api = Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SystemStatusApi::class.java)
            return SystemStatusRepository(api)
        }
    }
}
