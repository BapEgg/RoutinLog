package com.bapegg.routinlog.review

import com.bapegg.routinlog.security.AuthenticatedUser
import com.bapegg.routinlog.workout.WorkoutApiException
import org.springframework.http.*
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.LocalDate

@RestController @RequestMapping("/api/v1/workout-reviews")
class WorkoutReviewController(private val reviews:WorkoutReviewService) {
    @GetMapping fun history(@AuthenticationPrincipal user:AuthenticatedUser)=response(reviews.history(user.userId))
    @GetMapping("/{week}") fun get(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable week:LocalDate)=response(reviews.get(user.userId,week))
    @PostMapping("/{week}/prepare") fun prepare(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable week:LocalDate,@RequestBody request:ReviewPrepare)=response(reviews.prepare(user.userId,week,request))
    @PostMapping("/{week}/decision") fun decide(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable week:LocalDate,@RequestBody request:ReviewDecision)=response(reviews.decide(user.userId,week,request))
    private fun <T:Any> response(value:T)=ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value)
}
@RestControllerAdvice(assignableTypes=[WorkoutReviewController::class])
class WorkoutReviewErrors {
    @ExceptionHandler(ReviewException::class) fun expected(e:ReviewException)=ResponseEntity.status(e.status).cacheControl(CacheControl.noStore()).body(mapOf("code" to e.code,"message" to e.message))
    @ExceptionHandler(WorkoutApiException::class) fun workout(e:WorkoutApiException)=ResponseEntity.status(e.status).cacheControl(CacheControl.noStore()).body(mapOf("code" to e.code,"message" to e.message))
    @ExceptionHandler(HttpMessageNotReadableException::class,MethodArgumentTypeMismatchException::class)
    fun malformed()=ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(mapOf("code" to "INVALID_REQUEST","message" to "입력한 날짜와 값을 확인해주세요."))
}
