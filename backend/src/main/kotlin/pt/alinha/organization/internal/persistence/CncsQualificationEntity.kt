package pt.alinha.organization.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import pt.alinha.core.compliance.Nis2Classification
import pt.alinha.core.tenant.TenantOwnedEntity
import pt.alinha.organization.internal.domain.CncsQualification
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The official CNCS qualification of the tenant (US11). A correction overwrites it; the audit trail keeps the history. */
@Entity
@Table(schema = "organization", name = "cncs_qualification")
class CncsQualificationEntity(
    @Id val id: UUID,
    @Column(name = "registered_at", nullable = false, updatable = false) val registeredAt: Instant,
    qualification: CncsQualification,
) : TenantOwnedEntity() {

    @Version
    var version: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    var classification: Nis2Classification = qualification.classification
        protected set
    @Column(name = "act_date")
    var actDate: LocalDate = qualification.actDate
        protected set
    @Column(name = "act_reference")
    var actReference: String? = qualification.actReference
        protected set
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = registeredAt
        protected set

    fun toQualification() = CncsQualification(classification, actDate, actReference)

    /** Applies [qualification] and returns the names of the fields that changed (for the audit trail). */
    fun update(qualification: CncsQualification, at: Instant): List<String> {
        val changed = listOfNotNull(
            "classification".takeIf { classification != qualification.classification },
            "actDate".takeIf { actDate != qualification.actDate },
            "actReference".takeIf { actReference != qualification.actReference },
        )
        if (changed.isEmpty()) return changed
        classification = qualification.classification
        actDate = qualification.actDate
        actReference = qualification.actReference
        updatedAt = at
        return changed
    }
}
