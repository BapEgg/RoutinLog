package com.bapegg.routinlog.features

import com.bapegg.routinlog.security.AuthenticatedUser
import com.bapegg.routinlog.cardio.CardioEstimation
import com.bapegg.routinlog.workout.WorkoutApiException
import org.springframework.http.*
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.dao.DataIntegrityViolationException

@RestController @RequestMapping("/api/v1/features")
class FeatureController(private val features:FeatureService,private val analysis:WeeklyAnalysisService,private val photos:PrivateThumbnails) {
    @GetMapping("/photos/{kind}/{id}") fun photo(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable kind:String,@PathVariable id:java.util.UUID)=response(photos.get(user.userId,kind,id))
    @PutMapping("/photos/{kind}/{id}") fun photo(@AuthenticationPrincipal user:AuthenticatedUser,@PathVariable kind:String,@PathVariable id:java.util.UUID,@RequestBody write:ThumbnailDto)=response(photos.put(user.userId,kind,id,write))
    @GetMapping("/programs") fun programs(@AuthenticationPrincipal user:AuthenticatedUser)=response(features.catalog(user.userId))
    @PostMapping("/programs/apply") fun apply(@AuthenticationPrincipal user:AuthenticatedUser,@RequestBody write:ProgramApply)=response(features.apply(user.userId,write))
    @GetMapping("/preparation") fun preparation(@AuthenticationPrincipal user:AuthenticatedUser)=response(features.preparation(user.userId))
    @PutMapping("/preparation") fun preparation(@AuthenticationPrincipal user:AuthenticatedUser,@RequestBody write:PreparationDto)=response(features.preparation(user.userId,write))
    @GetMapping("/cardio-activities") fun activities(@AuthenticationPrincipal user:AuthenticatedUser):ResponseEntity<Any> { features.account(user.userId);return response(CardioEstimation.activities) }
    @PostMapping("/analysis") fun analysis(@AuthenticationPrincipal user:AuthenticatedUser,@RequestBody write:AnalysisWrite):ResponseEntity<Any> {
        val result=analysis.analyze(user.userId,write);features.account(user.userId);return response(result)
    }
    @GetMapping("/export") fun export(@AuthenticationPrincipal user:AuthenticatedUser)=ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"routinlog-records.json\"").body(features.export(user.userId))
    private fun response(body:Any):ResponseEntity<Any> = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)
}
@RestControllerAdvice(assignableTypes=[FeatureController::class])
class FeatureErrors {
    @ExceptionHandler(FeatureException::class) fun expected(e:FeatureException)=ResponseEntity.status(e.status).cacheControl(CacheControl.noStore()).body(mapOf("code" to e.code,"message" to e.message))
    @ExceptionHandler(WorkoutApiException::class) fun workout(e:WorkoutApiException)=ResponseEntity.status(e.status).cacheControl(CacheControl.noStore()).body(mapOf("code" to e.code,"message" to e.message))
    @ExceptionHandler(com.bapegg.routinlog.report.ReportException::class) fun report(e:com.bapegg.routinlog.report.ReportException)=ResponseEntity.status(e.status).cacheControl(CacheControl.noStore()).body(mapOf("code" to e.code,"message" to e.message))
    @ExceptionHandler(HttpMessageNotReadableException::class,MethodArgumentTypeMismatchException::class) fun malformed()=ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(mapOf("code" to "INVALID_REQUEST","message" to "입력 내용을 확인해주세요."))
    @ExceptionHandler(DataIntegrityViolationException::class) fun conflict()=ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(mapOf("code" to "VERSION_CONFLICT","message" to "최신 내용을 확인하고 다시 시도해주세요."))
}
