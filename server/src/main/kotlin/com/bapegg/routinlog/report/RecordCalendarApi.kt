package com.bapegg.routinlog.report

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.body.BodyMeasurementService
import com.bapegg.routinlog.condition.ConditionService
import com.bapegg.routinlog.security.AuthenticatedUser
import com.bapegg.routinlog.steps.StepsService
import com.bapegg.routinlog.workout.*
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.*
import java.util.UUID

data class RecordDay(val date: LocalDate, val mealsEaten: Int, val mealsSkipped: Int,
    val weightKg: BigDecimal?, val waistCm: BigDecimal?, val workoutStatus: WorkoutStatus?, val workoutName: String?,
    val doneSets: Int, val skippedSets: Int, val pendingSets: Int, val workoutRecorded: Boolean,
    val conditionRecorded: Boolean, val steps: Long?, val cardioMinutes: Int = 0, val cardioCount: Int = 0) {
    override fun toString() = "RecordDay(redacted)"
}
data class RecordCalendar(val month: LocalDate, val today: LocalDate, val timeZone: String, val days: List<RecordDay>) {
    override fun toString() = "RecordCalendar(redacted)"
}

/** Read only recorded facts: a current meal/routine plan is never projected onto historical completion. */
@Service @Transactional
class RecordCalendarService(private val entities: EntityManager, private val jdbc: JdbcTemplate,
    private val json: ObjectMapper, private val body: BodyMeasurementService,
    private val conditions: ConditionService, private val steps: StepsService) {
    fun get(owner: UUID, month: LocalDate?): RecordCalendar {
        val user = entities.find(UserAccountEntity::class.java, owner, LockModeType.PESSIMISTIC_WRITE)
        if (user == null || user.status != AccountStatus.ACTIVE)
            throw ReportException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "다시 로그인해주세요.")
        val today = LocalDate.now(ZoneId.of(user.timeZone))
        val from = month ?: today.withDayOfMonth(1)
        if (from < LocalDate.of(1900, 1, 1) || from > today || from.dayOfMonth != 1)
            throw ReportException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "1900년부터 이번 달까지 선택해주세요.")
        val to = minOf(YearMonth.from(from).atEndOfMonth(), today)
        val measurements = body.list(owner, from, to).items.associateBy { it.date }
        val feelings = conditions.list(owner, from, to).items.map { it.date }.toSet()
        val walking = steps.days(owner, from, to).items.associateBy { it.date }
        val meals = jdbc.query("SELECT meal_date,status,COUNT(*) AS total FROM meal_records WHERE user_id=? AND meal_date BETWEEN ? AND ? GROUP BY meal_date,status",
            { rs, _ -> Triple(rs.getDate("meal_date").toLocalDate(), rs.getString("status"), rs.getInt("total")) }, owner, from, to).groupBy { it.first }
        // Only one bounded session query; calendar browsing does not deserialize foods or resolve each day's plan.
        val workouts = jdbc.query("SELECT payload FROM workout_sessions WHERE user_id=? AND workout_date BETWEEN ? AND ?",
            { rs, _ -> json.readValue(rs.getString("payload"), WorkoutSessionDto::class.java) }, owner, from, to).associateBy { it.date }
        val cardio = jdbc.query("SELECT recorded_on,payload FROM cardio_records WHERE user_id=? AND recorded_on BETWEEN ? AND ?",
            { rs, _ -> json.readValue(rs.getString("payload"), com.bapegg.routinlog.cardio.CardioDto::class.java) }, owner, from, to).groupBy { it.date }
        val days = from.datesUntil(to.plusDays(1)).map { date ->
            val measurement = measurements[date]
            val session = workouts[date]
            val sets = session?.entries.orEmpty().flatMap { it.sets }
            val done = sets.count { it.status == SetStatus.DONE }
            val skipped = sets.count { it.status == SetStatus.SKIPPED }
            val recorded = session != null && (session.status == WorkoutStatus.COMPLETED || done + skipped > 0 ||
                !session.note.isNullOrBlank() || session.entries.any { !it.note.isNullOrBlank() || !it.replacementReason.isNullOrBlank() || it.sets.any { s -> !s.note.isNullOrBlank() } })
            RecordDay(date, meals[date]?.firstOrNull { it.second == "EATEN" }?.third ?: 0,
                meals[date]?.firstOrNull { it.second == "SKIPPED" }?.third ?: 0,
                measurement?.weightKg, measurement?.waistCm, session?.status, session?.planned?.routineName,
                done, skipped, sets.count { it.status == SetStatus.PENDING }, recorded, date in feelings, walking[date]?.steps, cardio[date].orEmpty().sumOf { it.values.minutes }, cardio[date].orEmpty().size)
        }.toList()
        return RecordCalendar(from, today, user.timeZone, days)
    }
}

@RestController @RequestMapping("/api/v1/record-calendar")
class RecordCalendarController(private val service: RecordCalendarService) {
    @GetMapping fun get(@AuthenticationPrincipal user: AuthenticatedUser, @RequestParam(required = false) month: LocalDate?) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(user.userId, month))
}
@RestControllerAdvice(assignableTypes = [RecordCalendarController::class])
class RecordCalendarErrors {
    @ExceptionHandler(ReportException::class) fun expected(e: ReportException) = ResponseEntity.status(e.status)
        .cacheControl(CacheControl.noStore()).body(mapOf("code" to e.code, "message" to e.message))
    @ExceptionHandler(MethodArgumentTypeMismatchException::class) fun malformed() = ResponseEntity.badRequest()
        .cacheControl(CacheControl.noStore()).body(mapOf("code" to "INVALID_REQUEST", "message" to "조회할 달의 날짜를 확인해주세요."))
}
