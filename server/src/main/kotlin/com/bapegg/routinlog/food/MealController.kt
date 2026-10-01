package com.bapegg.routinlog.food

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
class MealController(private val meals: MealService,private val catalog:FoodCatalog) {
    @GetMapping("/food-catalog")
    fun catalog(@AuthenticationPrincipal user:AuthenticatedUser,@RequestParam q:String,@RequestParam(defaultValue="0") page:Int):ResponseEntity<CatalogSearch> {
        meals.requireActiveAccount(user.userId);return privateResponse(catalog.search(q,page))
    }
    @GetMapping("/food-catalog/{id}")
    fun catalogFood(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:String):ResponseEntity<CatalogFood> {
        meals.requireActiveAccount(user.userId);return privateResponse(catalog.get(id))
    }
    @PostMapping("/food-catalog/{id}/save")
    fun saveCatalogFood(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable id:String,@RequestBody request:CatalogSave)=privateResponse(meals.saveCatalogFood(user.userId,id,request))
    @GetMapping("/foods")
    fun foods(@AuthenticationPrincipal user: AuthenticatedUser)=privateResponse(meals.foods(user.userId))
    @PutMapping("/foods/{id}")
    fun food(@AuthenticationPrincipal user: AuthenticatedUser,@PathVariable id: String,@RequestBody request: FoodWrite)=privateResponse(meals.putFood(user.userId,uuid(id),request))
    @DeleteMapping("/foods/{id}")
    fun deleteFood(@AuthenticationPrincipal user: AuthenticatedUser,@PathVariable id: String,@RequestParam version: Long): ResponseEntity<Void> {
        meals.deleteFood(user.userId,uuid(id),version); return emptyResponse()
    }
    @GetMapping("/meal-templates")
    fun templates(@AuthenticationPrincipal user: AuthenticatedUser)=privateResponse(meals.templates(user.userId))
    @PutMapping("/meal-templates/{id}")
    fun template(@AuthenticationPrincipal user: AuthenticatedUser,@PathVariable id: String,@RequestBody request: MealTemplateWrite)=privateResponse(meals.putTemplate(user.userId,uuid(id),request))
    @DeleteMapping("/meal-templates/{id}")
    fun deleteTemplate(@AuthenticationPrincipal user: AuthenticatedUser,@PathVariable id: String,@RequestParam version: Long): ResponseEntity<Void> {
        meals.deleteTemplate(user.userId,uuid(id),version); return emptyResponse()
    }
    @GetMapping("/meal-plan")
    fun plan(@AuthenticationPrincipal user: AuthenticatedUser)=privateResponse(meals.plan(user.userId))
    @PutMapping("/meal-plan")
    fun plan(@AuthenticationPrincipal user: AuthenticatedUser,@RequestBody request: MealPlanWrite)=privateResponse(meals.putPlan(user.userId,request))
    @GetMapping("/meal-records")
    fun day(@AuthenticationPrincipal user: AuthenticatedUser,@RequestParam date: LocalDate)=privateResponse(meals.day(user.userId,date))
    @DeleteMapping("/meal-day-plans/{date}/{slotId}")
    fun deleteDayPlan(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable date:LocalDate,@PathVariable slotId:String,@RequestParam version:Long):ResponseEntity<Void> {
        meals.deleteDayPlan(user.userId,date,uuid(slotId),version);return emptyResponse()
    }
    @PutMapping("/meal-records/{id}")
    fun meal(@AuthenticationPrincipal user: AuthenticatedUser,@PathVariable id: String,@RequestBody request: MealWrite)=privateResponse(meals.putMeal(user.userId,uuid(id),request))
    @DeleteMapping("/meal-records/{id}")
    fun deleteMeal(@AuthenticationPrincipal user: AuthenticatedUser,@PathVariable id: String,@RequestParam version: Long): ResponseEntity<Void> {
        meals.deleteMeal(user.userId,uuid(id),version); return emptyResponse()
    }
    private fun <T:Any> privateResponse(value:T)=ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value)
    private fun emptyResponse(): ResponseEntity<Void> = ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
}

data class MealApiError(val code:String,val message:String)
class MealApiException(val status:HttpStatus,val code:String,override val message:String):RuntimeException(message)

@RestControllerAdvice(assignableTypes=[MealController::class])
class MealApiErrors {
    @ExceptionHandler(MealApiException::class)
    fun expected(error:MealApiException)=ResponseEntity.status(error.status).cacheControl(CacheControl.noStore()).body(MealApiError(error.code,error.message))
    @ExceptionHandler(HttpMessageNotReadableException::class,MethodArgumentTypeMismatchException::class,MissingServletRequestParameterException::class)
    fun malformed()=ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(MealApiError("INVALID_REQUEST","요청 형식과 필수 항목을 확인해주세요."))
    @ExceptionHandler(DataIntegrityViolationException::class,OptimisticLockingFailureException::class)
    fun conflict()=ResponseEntity.status(HttpStatus.CONFLICT).cacheControl(CacheControl.noStore()).body(MealApiError("VERSION_CONFLICT","기록이 변경됐어요. 최신 내용을 확인한 뒤 다시 시도해주세요."))
}
