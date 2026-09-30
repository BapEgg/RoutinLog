package com.bapegg.routinlog.profile.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface UserProfileRepository : JpaRepository<UserProfileRevisionEntity, UUID> {
    fun findFirstByUserIdOrderByRevisionDesc(userId: UUID): UserProfileRevisionEntity?
    fun findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescRevisionDesc(userId: UUID, date: LocalDate): UserProfileRevisionEntity?
    fun countByUserId(userId: UUID): Long
}
