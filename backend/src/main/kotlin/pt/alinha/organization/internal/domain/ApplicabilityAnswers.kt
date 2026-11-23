package pt.alinha.organization.internal.domain

import pt.alinha.core.compliance.AiActAnswers
import pt.alinha.core.compliance.ApplicabilityInput
import pt.alinha.core.compliance.GdprAnswers
import pt.alinha.core.compliance.Nis2Answers
import pt.alinha.core.compliance.SizeData

/**
 * What the organization answered for the applicability rules (FR02, FR03, organization#4), already validated.
 * The size is not here: it comes from the organization data, so a later change to it shows in the result.
 */
data class ApplicabilityAnswers(
    val publicBody: Boolean,
    /** Optional: when known, a company can stay in a smaller category. */
    val annualBalanceSheetEur: Long?,
    val nis2: Nis2Answers,
    val gdpr: GdprAnswers,
    val aiAct: AiActAnswers,
) {
    fun toInput(employees: Int, turnover: Long) = ApplicabilityInput(
        size = SizeData(employees, turnover, annualBalanceSheetEur),
        publicBody = publicBody,
        nis2 = nis2,
        gdpr = gdpr,
        aiAct = aiAct,
    )
}

/** What the client sends, before validation. Omitted booleans mean false; omitted entity types mean none. */
data class ApplicabilityAnswersInput(
    val publicBody: Boolean? = null,
    val annualBalanceSheetEur: Long? = null,
    val nis2: Nis2AnswersInput? = null,
    val gdpr: GdprAnswersInput? = null,
    val aiAct: AiActAnswersInput? = null,
)

/** [entityTypes] are `Nis2EntityType` names, kept as text so an unknown one is a field error, not a parse error. */
data class Nis2AnswersInput(
    val entityTypes: List<String>? = null,
    val criticalEntity: Boolean? = null,
    val significantImpact: Boolean? = null,
    val higherEducation: Boolean? = null,
)

data class GdprAnswersInput(
    val processesPersonalData: Boolean? = null,
    val regularOrRiskyProcessing: Boolean? = null,
    val specialOrCriminalData: Boolean? = null,
    val largeScaleMonitoring: Boolean? = null,
    val largeScaleSpecialData: Boolean? = null,
)

data class AiActAnswersInput(
    val usesAiSystems: Boolean? = null,
    val providesAiSystems: Boolean? = null,
    val highRiskArea: Boolean? = null,
    val interactsOrGeneratesContent: Boolean? = null,
)

sealed interface AnswersValidation {
    data class Valid(val answers: ApplicabilityAnswers) : AnswersValidation
    data class Invalid(val errors: List<FieldError>) : AnswersValidation
}
