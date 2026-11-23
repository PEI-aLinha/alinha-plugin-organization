package pt.alinha.organization.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import pt.alinha.core.compliance.AiActAnswers
import pt.alinha.core.compliance.GdprAnswers
import pt.alinha.core.compliance.Nis2Answers
import pt.alinha.core.compliance.Nis2EntityType
import pt.alinha.core.tenant.TenantOwnedEntity
import pt.alinha.organization.internal.domain.ApplicabilityAnswers
import java.time.Instant
import java.util.UUID

@Entity
@Table(schema = "organization", name = "applicability_answers")
class ApplicabilityAnswersEntity(
    @Id val id: UUID,
    @Column(name = "answered_at", nullable = false, updatable = false) val answeredAt: Instant,
    answers: ApplicabilityAnswers,
) : TenantOwnedEntity() {

    @Version
    var version: Long = 0
        protected set

    @Column(name = "public_body")
    var publicBody: Boolean = answers.publicBody
        protected set
    @Column(name = "annual_balance_sheet")
    var annualBalanceSheet: Long? = answers.annualBalanceSheetEur
        protected set
    /** Nis2EntityType names, in annex order. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "nis2_entity_types", columnDefinition = "text[]")
    var nis2EntityTypes: Array<String> = answers.nis2.entityTypes.map { it.name }.toTypedArray()
        protected set
    @Column(name = "critical_entity")
    var criticalEntity: Boolean = answers.nis2.criticalEntity
        protected set
    @Column(name = "significant_impact")
    var significantImpact: Boolean = answers.nis2.significantImpact
        protected set
    @Column(name = "higher_education")
    var higherEducation: Boolean = answers.nis2.higherEducation
        protected set
    @Column(name = "processes_personal_data")
    var processesPersonalData: Boolean = answers.gdpr.processesPersonalData
        protected set
    @Column(name = "regular_or_risky_processing")
    var regularOrRiskyProcessing: Boolean = answers.gdpr.regularOrRiskyProcessing
        protected set
    @Column(name = "special_or_criminal_data")
    var specialOrCriminalData: Boolean = answers.gdpr.specialOrCriminalData
        protected set
    @Column(name = "large_scale_monitoring")
    var largeScaleMonitoring: Boolean = answers.gdpr.largeScaleMonitoring
        protected set
    @Column(name = "large_scale_special_data")
    var largeScaleSpecialData: Boolean = answers.gdpr.largeScaleSpecialData
        protected set
    @Column(name = "uses_ai_systems")
    var usesAiSystems: Boolean = answers.aiAct.usesAiSystems
        protected set
    @Column(name = "provides_ai_systems")
    var providesAiSystems: Boolean = answers.aiAct.providesAiSystems
        protected set
    @Column(name = "high_risk_area")
    var highRiskArea: Boolean = answers.aiAct.highRiskArea
        protected set
    @Column(name = "interacts_or_generates_content")
    var interactsOrGeneratesContent: Boolean = answers.aiAct.interactsOrGeneratesContent
        protected set
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = answeredAt
        protected set

    fun toAnswers() = ApplicabilityAnswers(
        publicBody = publicBody,
        annualBalanceSheetEur = annualBalanceSheet,
        nis2 = Nis2Answers(
            // A name the core no longer knows is dropped rather than failing every read of the scope.
            entityTypes = nis2EntityTypes.mapNotNull { name -> Nis2EntityType.entries.find { it.name == name } }
                .sortedBy { it.ordinal }.toCollection(LinkedHashSet()),
            criticalEntity = criticalEntity,
            significantImpact = significantImpact,
            higherEducation = higherEducation,
        ),
        gdpr = GdprAnswers(
            processesPersonalData, regularOrRiskyProcessing, specialOrCriminalData, largeScaleMonitoring, largeScaleSpecialData,
        ),
        aiAct = AiActAnswers(usesAiSystems, providesAiSystems, highRiskArea, interactsOrGeneratesContent),
    )

    /** Applies [answers]; false (and nothing changes) when they are the ones already saved. */
    fun update(answers: ApplicabilityAnswers, at: Instant): Boolean {
        if (answers == toAnswers()) return false
        publicBody = answers.publicBody
        annualBalanceSheet = answers.annualBalanceSheetEur
        nis2EntityTypes = answers.nis2.entityTypes.map { it.name }.toTypedArray()
        criticalEntity = answers.nis2.criticalEntity
        significantImpact = answers.nis2.significantImpact
        higherEducation = answers.nis2.higherEducation
        processesPersonalData = answers.gdpr.processesPersonalData
        regularOrRiskyProcessing = answers.gdpr.regularOrRiskyProcessing
        specialOrCriminalData = answers.gdpr.specialOrCriminalData
        largeScaleMonitoring = answers.gdpr.largeScaleMonitoring
        largeScaleSpecialData = answers.gdpr.largeScaleSpecialData
        usesAiSystems = answers.aiAct.usesAiSystems
        providesAiSystems = answers.aiAct.providesAiSystems
        highRiskArea = answers.aiAct.highRiskArea
        interactsOrGeneratesContent = answers.aiAct.interactsOrGeneratesContent
        updatedAt = at
        return true
    }
}
