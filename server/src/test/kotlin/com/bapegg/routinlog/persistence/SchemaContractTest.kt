package com.bapegg.routinlog.persistence

import com.bapegg.routinlog.account.persistence.ExternalIdentityEntity
import com.bapegg.routinlog.account.persistence.UserAccountEntity
import com.bapegg.routinlog.body.persistence.BodyMeasurementEntity
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** H2 contract checks only; production PostgreSQL compatibility must be tested separately. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaContractTest @Autowired constructor(
    private val entityManager: EntityManager,
    private val jdbc: JdbcTemplate,
) {
    @Test
    fun `JPA mappings persist a user identity and exact body measurement against the migration`() {
        val account = UserAccountEntity()
        entityManager.persist(account)
        entityManager.persist(ExternalIdentityEntity(user = account, providerSubject = "test-subject-${UUID.randomUUID()}"))
        val record = BodyMeasurementEntity(
            user = account,
            measuredOn = LocalDate.of(2026, 9, 30),
            weightKg = BigDecimal("83.200"),
            waistCm = BigDecimal("80.00"),
        )
        entityManager.persist(record)
        entityManager.flush()
        entityManager.clear()

        val loaded = entityManager.find(BodyMeasurementEntity::class.java, record.id)
        assertEquals(BigDecimal("83.200"), loaded.weightKg)
        assertEquals(BigDecimal("80.00"), loaded.waistCm)
        assertEquals(account.id, loaded.user.id)
    }

    @Test
    fun `each user can record the same day but duplicate records for one user are rejected`() {
        val first = createAccount()
        val second = createAccount()
        insertMeasurement(first, "2026-09-30", "83.2", null)
        insertMeasurement(second, "2026-09-30", null, "80")

        assertFailsWith<DataIntegrityViolationException> {
            insertMeasurement(first, "2026-09-30", "83.3", null)
        }
    }

    @Test
    fun `body records cannot reference a nonexistent user`() {
        assertFailsWith<DataIntegrityViolationException> {
            insertMeasurement(UUID.randomUUID(), "2026-09-30", "83.2", null)
        }
    }

    @Test
    fun `empty and impossible body records are rejected without treating missing values as zero`() {
        val account = createAccount()
        assertFailsWith<DataIntegrityViolationException> {
            insertMeasurement(account, "2026-09-30", null, null)
        }
        assertFailsWith<DataIntegrityViolationException> {
            insertMeasurement(account, "2026-09-30", "0", null)
        }
        assertFailsWith<DataIntegrityViolationException> {
            insertMeasurement(account, "2026-09-30", null, "-1")
        }
    }

    private fun createAccount(): UUID {
        val account = UserAccountEntity()
        entityManager.persist(account)
        entityManager.flush()
        return account.id
    }

    private fun insertMeasurement(userId: UUID, date: String, weight: String?, waist: String?) {
        jdbc.update(
            """INSERT INTO body_measurements
                (id, user_id, measured_on, weight_kg, waist_cm, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""".trimIndent(),
            UUID.randomUUID(), userId, LocalDate.parse(date), weight?.let(::BigDecimal), waist?.let(::BigDecimal),
        )
    }
}
