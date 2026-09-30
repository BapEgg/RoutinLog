package com.bapegg.routinlog.account.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserAccountRepository : JpaRepository<UserAccountEntity, UUID>

interface ExternalIdentityRepository : JpaRepository<ExternalIdentityEntity, UUID> {
    fun findByProviderAndProviderSubject(provider: IdentityProvider, providerSubject: String): ExternalIdentityEntity?
}
