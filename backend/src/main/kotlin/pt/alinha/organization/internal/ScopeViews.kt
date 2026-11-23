package pt.alinha.organization.internal

import pt.alinha.core.compliance.AiActAnswers
import pt.alinha.core.compliance.ApplicabilityResult
import pt.alinha.core.compliance.Finding
import pt.alinha.core.compliance.FrameworkApplicability
import pt.alinha.core.compliance.GdprAnswers
import pt.alinha.core.compliance.Nis2Answers
import pt.alinha.core.compliance.Nis2Classification
import pt.alinha.core.compliance.Nis2EntityType
import pt.alinha.organization.internal.persistence.ApplicabilityAnswersEntity
import pt.alinha.organization.internal.persistence.CncsQualificationEntity
import java.time.Instant
import java.time.LocalDate

/** Body of `GET /scope` and of both `PUT`s (`OrganizationScope` in the contract). */
data class ScopeView(
    val answers: ApplicabilityAnswersView?,
    val result: ApplicabilityResultView?,
    val qualification: CncsQualificationView?,
    val nis2: Nis2StatusView?,
    val entityTypes: List<EntityTypeOption>,
)

data class ApplicabilityAnswersView(
    val version: Long,
    val publicBody: Boolean,
    val annualBalanceSheetEur: Long?,
    val nis2: Nis2Answers,
    val gdpr: GdprAnswers,
    val aiAct: AiActAnswers,
    val answeredAt: Instant,
    val updatedAt: Instant,
)

/** The core's ApplicabilityResult with the contract's enum values (`not-covered`, `review`, `medium`). */
data class ApplicabilityResultView(
    val rulesVersion: String,
    val size: String,
    val nis2Classification: String,
    val cncsMayQualify: Boolean,
    val indicative: Boolean,
    val frameworks: List<FrameworkApplicabilityView>,
)

data class FrameworkApplicabilityView(val framework: String, val applies: String, val findings: List<Finding>)

data class CncsQualificationView(
    val version: Long,
    val classification: String,
    val actDate: LocalDate,
    val actReference: String?,
    val registeredAt: Instant,
    val updatedAt: Instant,
)

/** The NIS2 classification that counts: the CNCS's when registered, otherwise the indicative one. */
data class Nis2StatusView(val classification: String, val official: Boolean, val suggested: String?)

data class EntityTypeOption(
    val id: Nis2EntityType,
    val annex: String,
    val reference: String,
    val label: String,
    val suggested: Boolean,
)

/** Contract value of an enum: `NOT_COVERED` → `not-covered` (as `AssessmentDetail.nis2Classification`). */
internal fun Enum<*>.apiValue(): String = name.lowercase().replace('_', '-')

internal fun ApplicabilityAnswersEntity.toView(): ApplicabilityAnswersView {
    val answers = toAnswers()
    return ApplicabilityAnswersView(
        version, answers.publicBody, answers.annualBalanceSheetEur, answers.nis2, answers.gdpr, answers.aiAct,
        answeredAt, updatedAt,
    )
}

internal fun ApplicabilityResult.toView() = ApplicabilityResultView(
    rulesVersion = rulesVersion,
    size = size.apiValue(),
    nis2Classification = nis2Classification.apiValue(),
    cncsMayQualify = cncsMayQualify,
    indicative = indicative,
    frameworks = frameworks.map(FrameworkApplicability::toView),
)

private fun FrameworkApplicability.toView() = FrameworkApplicabilityView(framework, applies.apiValue(), findings)

internal fun CncsQualificationEntity.toView() = CncsQualificationView(
    version, classification.apiValue(), actDate, actReference, registeredAt, updatedAt,
)

internal fun nis2Status(official: Nis2Classification?, suggested: Nis2Classification?): Nis2StatusView? = when {
    official != null -> Nis2StatusView(official.apiValue(), true, suggested?.apiValue())
    suggested != null -> Nis2StatusView(suggested.apiValue(), false, suggested.apiValue())
    else -> null
}
