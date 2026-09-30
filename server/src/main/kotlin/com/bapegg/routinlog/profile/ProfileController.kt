package com.bapegg.routinlog.profile

import com.bapegg.routinlog.security.AuthenticatedUser
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

fun principalUserId(principal: AuthenticatedUser?): UUID = principal?.userId
    ?: throw RecordApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요해요.")

@RestController
@RequestMapping("/api/v1/me/profile")
class ProfileController(private val profiles: ProfileService) {
    @GetMapping
    fun current(@AuthenticationPrincipal principal: AuthenticatedUser?): ResponseEntity<ProfileDto> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.current(principalUserId(principal)))

    @PutMapping
    fun save(@AuthenticationPrincipal principal: AuthenticatedUser?, @RequestBody request: ProfileDto): ResponseEntity<ProfileDto> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.save(principalUserId(principal), request))
}
