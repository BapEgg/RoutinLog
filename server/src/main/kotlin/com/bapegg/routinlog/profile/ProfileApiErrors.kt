package com.bapegg.routinlog.profile

import com.bapegg.routinlog.body.BodyMeasurementController
import jakarta.persistence.OptimisticLockException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.math.BigDecimal

data class RecordApiError(val code: String, val message: String)
class RecordApiException(val status: HttpStatus, val code: String, override val message: String) : RuntimeException(message)

fun invalidRecord(message: String): Nothing = throw RecordApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)
fun versionConflict(): Nothing = throw RecordApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "기록이 변경됐어요. 최신 기록을 확인한 뒤 다시 시도해주세요.")

/** Limits are storage/transport constraints, not healthy or recommended medical ranges. */
fun requireDecimal(value: BigDecimal?, maximum: String, scale: Int, label: String, optional: Boolean = false, zeroAllowed: Boolean = false) {
    if (value == null) { if (!optional) invalidRecord("$label 값을 입력해주세요."); return }
    if (value.stripTrailingZeros().scale() > scale || value > BigDecimal(maximum) || (if (zeroAllowed) value.signum() < 0 else value.signum() <= 0)) {
        invalidRecord("$label 값의 범위와 소수 자릿수를 확인해주세요.")
    }
}

@RestControllerAdvice(assignableTypes = [ProfileController::class, BodyMeasurementController::class])
class ProfileApiErrors {
    @ExceptionHandler(RecordApiException::class)
    fun expected(error: RecordApiException) = ResponseEntity.status(error.status).body(RecordApiError(error.code, error.message))

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class, MissingServletRequestParameterException::class)
    fun malformed() = ResponseEntity.badRequest().body(RecordApiError("INVALID_REQUEST", "요청 형식과 필수 항목을 확인해주세요."))

    @ExceptionHandler(OptimisticLockingFailureException::class, OptimisticLockException::class, DataIntegrityViolationException::class)
    fun concurrentWrite() = ResponseEntity.status(HttpStatus.CONFLICT).body(RecordApiError("VERSION_CONFLICT", "기록이 변경됐어요. 최신 기록을 확인한 뒤 다시 시도해주세요."))
}
