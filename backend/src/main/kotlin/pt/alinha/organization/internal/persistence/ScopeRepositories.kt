package pt.alinha.organization.internal.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Filtered by the current tenant (TenantOwnedEntity): "first" is the tenant's only row. */
interface ApplicabilityAnswersRepository : JpaRepository<ApplicabilityAnswersEntity, UUID> {
    fun findFirstBy(): ApplicabilityAnswersEntity?
}

/** Filtered by the current tenant (TenantOwnedEntity): "first" is the tenant's only row. */
interface CncsQualificationRepository : JpaRepository<CncsQualificationEntity, UUID> {
    fun findFirstBy(): CncsQualificationEntity?
}
