package com.bapegg.routinlog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bapegg.routinlog.data.SystemStatusRepository
import com.bapegg.routinlog.domain.BodyMeasurement
import com.bapegg.routinlog.domain.BodyMeasurementInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

enum class AppScreen { WELCOME, SAMPLE_HOME, SAMPLE_BODY, DEBUG_STATUS }

data class RoutineLogUiState(
    val screen: AppScreen = AppScreen.WELCOME,
    val sampleBody: BodyMeasurement = BodyMeasurement(LocalDate.now(), BigDecimal("83.2"), BigDecimal("80.0")),
    val weightInput: String = "83.2",
    val waistInput: String = "80.0",
    val weightError: String? = null,
    val waistError: String? = null,
    val formError: String? = null,
    val sampleNotice: String? = null,
    val checkingStatus: Boolean = false,
    val statusMessage: String = "아직 확인하지 않았습니다.",
)

class RoutineLogViewModel(private val statusRepository: SystemStatusRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(RoutineLogUiState())
    val state = mutableState.asStateFlow()

    fun enterSample() { mutableState.value = RoutineLogUiState(screen = AppScreen.SAMPLE_HOME) }
    fun openBody() = mutableState.update {
        it.copy(screen = AppScreen.SAMPLE_BODY, weightInput = it.sampleBody.weightKg?.toPlainString().orEmpty(),
            waistInput = it.sampleBody.waistCm?.toPlainString().orEmpty(), weightError = null, waistError = null,
            formError = null, sampleNotice = null)
    }
    fun changeWeight(value: String) = mutableState.update { it.copy(weightInput = value, weightError = null, formError = null) }
    fun changeWaist(value: String) = mutableState.update { it.copy(waistInput = value, waistError = null, formError = null) }
    fun back() = mutableState.update {
        if (it.screen == AppScreen.SAMPLE_BODY) it.copy(screen = AppScreen.SAMPLE_HOME)
        else RoutineLogUiState()
    }
    fun endSample() { mutableState.value = RoutineLogUiState() }

    fun applySampleInput() {
        val current = state.value
        val result = BodyMeasurementInput.validate(current.sampleBody.date, current.weightInput, current.waistInput)
        mutableState.update {
            if (result.measurement == null) it.copy(weightError = result.weightError, waistError = result.waistError, formError = result.formError)
            else it.copy(screen = AppScreen.SAMPLE_HOME, sampleBody = result.measurement,
                sampleNotice = "체험 화면에만 반영했어요. 실제 기록으로 저장되지 않아요.")
        }
    }

    fun openDebugStatus() = mutableState.update { it.copy(screen = AppScreen.DEBUG_STATUS) }
    fun checkStatus() {
        if (state.value.checkingStatus) return
        mutableState.update { it.copy(checkingStatus = true, statusMessage = "서버 응답 확인 중…") }
        viewModelScope.launch {
            val message = try {
                "응답 확인: routinlog · ready · ${statusRepository.readVersion()}"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: HttpException) {
                "서버 HTTP ${http.code()}. 서버 실행 상태를 확인해주세요."
            } catch (_: IOException) {
                "연결하지 못했습니다. 서버 실행·네트워크·에뮬레이터 주소를 확인해주세요."
            } catch (_: IllegalStateException) {
                "서버 주소 또는 응답 형식이 현재 앱 계약과 일치하지 않습니다."
            } catch (_: RuntimeException) {
                "서버 응답을 읽지 못했습니다. JSON 응답 형식을 확인해주세요."
            }
            mutableState.update { it.copy(checkingStatus = false, statusMessage = message) }
        }
    }
}
