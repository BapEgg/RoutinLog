package com.bapegg.routinlog.report

import com.bapegg.routinlog.account.persistence.*
import com.bapegg.routinlog.body.*
import com.bapegg.routinlog.condition.*
import com.bapegg.routinlog.food.*
import com.bapegg.routinlog.steps.*
import com.bapegg.routinlog.workout.*
import com.bapegg.routinlog.security.AuthenticatedUser
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.*
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class ReportNutrient(val key:String,val target:BigDecimal?,val targetDays:Int,val recorded:BigDecimal?,val knownItems:Int,val missingItems:Int)
data class ReportAverage(val value:BigDecimal?,val count:Int,val previous:BigDecimal?,val previousCount:Int,val change:BigDecimal?)
data class WeeklyReport(
    val from:LocalDate,val to:LocalDate,val weekEnd:LocalDate,val latestWeek:LocalDate,val timeZone:String,val generatedAt:Instant,
    val nutrition:List<ReportNutrient>,val mealDays:List<MealDayDto>,val workouts:List<WorkoutDayDto>,
    val body:List<BodyMeasurementDto>,val weight:ReportAverage,val waist:ReportAverage,
    val conditions:List<ConditionDto>,val steps:List<StepDay>,
) { override fun toString()="WeeklyReport(redacted)" }
class ReportException(val status:HttpStatus,val code:String,message:String):RuntimeException(message)

/** One account lock gives a consistent read across feature stores. Reports contain observations, not causal diagnoses. */
@Service @Transactional
class WeeklyReportService(private val entities:EntityManager,private val meals:MealService,
    private val workouts:WorkoutService,private val body:BodyMeasurementService,private val conditions:ConditionService,private val steps:StepsService) {
    fun get(userId:UUID,week:LocalDate?):WeeklyReport {
        val user=entities.find(UserAccountEntity::class.java,userId,LockModeType.PESSIMISTIC_WRITE)
        if(user==null||user.status!=AccountStatus.ACTIVE)throw ReportException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","다시 로그인해주세요.")
        val today=LocalDate.now(ZoneId.of(user.timeZone))
        val latest=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val from=week ?: latest.minusWeeks(1)
        if(from<LocalDate.of(1900,1,8)||from>latest||from.dayOfWeek!=DayOfWeek.MONDAY)
            throw ReportException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","이번 주 또는 지난 주의 월요일을 선택해주세요.")
        val to=minOf(from.plusDays(6),today)
        val days=from.datesUntil(to.plusDays(1)).map { meals.day(userId,it) }.toList()
        val measurements=body.list(userId,from.minusWeeks(1),to).items
        val current=measurements.filter { it.date>=from };val previous=measurements.filter { it.date<from }
        return WeeklyReport(from,to,from.plusDays(6),latest,user.timeZone,Instant.now(),nutrition(days),days,
            workouts.days(userId,from,to).items,current,average(current,previous){it.weightKg},average(current,previous){it.waistCm},
            conditions.list(userId,from,to).items,steps.days(userId,from,to).items)
    }
    internal fun nutrition(days:List<MealDayDto>):List<ReportNutrient> {
        // Recalculate across original snapshots once, avoiding a sum of rounded daily totals.
        val totals=MealNutrition.totals(days.flatMap { it.items }.filter { it.status==MealStatus.EATEN }.flatMap { it.items })
        val targets=days.mapNotNull { it.target }
        fun row(key:String,select:(NutritionValues)->BigDecimal?,actual:NutrientTotal):ReportNutrient {
            val known=targets.mapNotNull(select)
            return ReportNutrient(key,known.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO,BigDecimal::add),known.size,
                actual.knownAmount.takeIf { actual.knownItems>0 || actual.missingItems==0 && days.any { d->d.items.any { it.status==MealStatus.SKIPPED } } },actual.knownItems,actual.missingItems)
        }
        return listOf(row("kcal",{it.kcal},totals.kcal),row("carbsG",{it.carbsG},totals.carbsG),row("proteinG",{it.proteinG},totals.proteinG),row("fatG",{it.fatG},totals.fatG),row("fiberG",{it.fiberG},totals.fiberG))
    }
    private fun average(current:List<BodyMeasurementDto>,previous:List<BodyMeasurementDto>,select:(BodyMeasurementDto)->BigDecimal?):ReportAverage {
        val a=current.mapNotNull(select);val b=previous.mapNotNull(select)
        fun mean(values:List<BigDecimal>)=values.takeIf { it.isNotEmpty() }?.let { it.fold(BigDecimal.ZERO,BigDecimal::add).divide(it.size.toBigDecimal(),3,RoundingMode.HALF_UP) }
        val value=mean(a);val before=mean(b)
        return ReportAverage(value,a.size,before,b.size,if(value!=null&&before!=null)value-before else null)
    }
}

@RestController @RequestMapping("/api/v1/reports/weekly")
class WeeklyReportController(private val service:WeeklyReportService) {
    @GetMapping fun get(@AuthenticationPrincipal user:AuthenticatedUser,@RequestParam(required=false) week:LocalDate?)=
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(user.userId,week))
}
@RestControllerAdvice(assignableTypes=[WeeklyReportController::class])
class WeeklyReportErrors {
    @ExceptionHandler(ReportException::class) fun expected(e:ReportException)=ResponseEntity.status(e.status).cacheControl(CacheControl.noStore()).body(mapOf("code" to e.code,"message" to e.message))
    @ExceptionHandler(MethodArgumentTypeMismatchException::class) fun malformed()=ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(mapOf("code" to "INVALID_REQUEST","message" to "조회할 주의 날짜를 확인해주세요."))
}
