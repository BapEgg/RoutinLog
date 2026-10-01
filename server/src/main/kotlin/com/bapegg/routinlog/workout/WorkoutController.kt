package com.bapegg.routinlog.workout

import com.bapegg.routinlog.security.AuthenticatedUser
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1")
class WorkoutController(private val workouts:WorkoutService) {
    @GetMapping("/workout-exercises")
    fun exercises(@AuthenticationPrincipal user:AuthenticatedUser)=privateResponse(workouts.exercises(user.userId))
    @PutMapping("/workout-exercises/{id}")
    fun exercise(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:String,@RequestBody request:ExerciseWrite)=privateResponse(workouts.putExercise(user.userId,workoutUuid(id),request))
    @DeleteMapping("/workout-exercises/{id}")
    fun deleteExercise(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:String,@RequestParam version:Long):ResponseEntity<Void> {
        workouts.deleteExercise(user.userId,workoutUuid(id),version); return emptyResponse()
    }
    @GetMapping("/workout-routines")
    fun routines(@AuthenticationPrincipal user:AuthenticatedUser)=privateResponse(workouts.routines(user.userId))
    @PutMapping("/workout-routines/{id}")
    fun routine(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:String,@RequestBody request:RoutineWrite)=privateResponse(workouts.putRoutine(user.userId,workoutUuid(id),request))
    @DeleteMapping("/workout-routines/{id}")
    fun deleteRoutine(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:String,@RequestParam version:Long):ResponseEntity<Void> {
        workouts.deleteRoutine(user.userId,workoutUuid(id),version); return emptyResponse()
    }
    @GetMapping("/workout-plan")
    fun plan(@AuthenticationPrincipal user:AuthenticatedUser)=privateResponse(workouts.plan(user.userId))
    @PutMapping("/workout-plan")
    fun plan(@AuthenticationPrincipal user:AuthenticatedUser,@RequestBody request:WorkoutPlanWrite)=privateResponse(workouts.putPlan(user.userId,request))
    @PutMapping("/workout-overrides/{date}")
    fun override(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable date:LocalDate,@RequestBody request:WorkoutOverrideWrite)=privateResponse(workouts.putOverride(user.userId,date,request))
    @DeleteMapping("/workout-overrides/{date}")
    fun deleteOverride(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable date:LocalDate,@RequestParam version:Long):ResponseEntity<Void> {
        workouts.deleteOverride(user.userId,date,version); return emptyResponse()
    }
    @PutMapping("/workout-sessions/{id}/start")
    fun start(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:String,@RequestBody request:WorkoutStartWrite)=privateResponse(workouts.start(user.userId,workoutUuid(id),request))
    @PutMapping("/workout-sessions/{id}")
    fun session(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:String,@RequestBody request:WorkoutSessionWrite)=privateResponse(workouts.putSession(user.userId,workoutUuid(id),request))
    @DeleteMapping("/workout-sessions/{id}")
    fun deleteSession(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:String,@RequestParam version:Long):ResponseEntity<Void> {
        workouts.deleteSession(user.userId,workoutUuid(id),version); return emptyResponse()
    }
    @GetMapping("/workout-days")
    fun days(@AuthenticationPrincipal user:AuthenticatedUser,@RequestParam from:LocalDate,@RequestParam to:LocalDate)=privateResponse(workouts.days(user.userId,from,to))
    @GetMapping("/workout-history")
    fun history(@AuthenticationPrincipal user:AuthenticatedUser,@RequestParam exerciseId:String,@RequestParam before:LocalDate)=privateResponse(workouts.history(user.userId,workoutUuid(exerciseId),before))
    private fun <T:Any> privateResponse(value:T)=ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value)
    private fun emptyResponse():ResponseEntity<Void> = ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
}

data class WorkoutApiError(val code:String,val message:String)
class WorkoutApiException(val status:HttpStatus,val code:String,override val message:String):RuntimeException(message)

@RestControllerAdvice(assignableTypes=[WorkoutController::class])
class WorkoutApiErrors {
    @ExceptionHandler(WorkoutApiException::class)
    fun expected(error:WorkoutApiException)=ResponseEntity.status(error.status).cacheControl(CacheControl.noStore()).body(WorkoutApiError(error.code,error.message))
    @ExceptionHandler(HttpMessageNotReadableException::class,MethodArgumentTypeMismatchException::class,MissingServletRequestParameterException::class)
    fun malformed()=ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(WorkoutApiError("INVALID_REQUEST","요청 형식과 필수 항목을 확인해주세요."))
    @ExceptionHandler(DataIntegrityViolationException::class,OptimisticLockingFailureException::class)
    fun conflict()=ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).body(WorkoutApiError("VERSION_CONFLICT","기록이 변경됐어요. 최신 내용을 확인한 뒤 다시 시도해주세요."))
}
