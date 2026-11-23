package pt.alinha.organization.internal.domain

import pt.alinha.core.compliance.AiActAnswers
import pt.alinha.core.compliance.GdprAnswers
import pt.alinha.core.compliance.Nis2Answers
import pt.alinha.core.compliance.Nis2EntityType

/**
 * Validates the applicability answers. Contradictions are field errors here: the core's answer types throw
 * IllegalArgumentException for them (docs/applicability.md, "Input validation"), so they are only built once
 * every check has passed. Messages are shown to the user, in plain European Portuguese.
 */
object ApplicabilityAnswersValidator {

    private val entityTypesByName = Nis2EntityType.entries.associateBy { it.name }

    fun validate(input: ApplicabilityAnswersInput, extraErrors: List<FieldError> = emptyList()): AnswersValidation {
        val errors = extraErrors.toMutableList()
        fun fail(field: String, message: String) { errors += FieldError(field, message) }

        val balanceSheet = input.annualBalanceSheetEur
        if (balanceSheet != null && balanceSheet < 0) {
            fail("annualBalanceSheetEur", "Use só números, em euros e sem cêntimos. Exemplo: 18 500 000.")
        }

        val names = input.nis2?.entityTypes.orEmpty()
        if (names.any { it !in entityTypesByName }) {
            fail("nis2.entityTypes", "Há um tipo de entidade que não existe. Recarregue a página e escolha de novo.")
        }

        val gdpr = input.gdpr
        val processesPersonalData = gdpr?.processesPersonalData
        val gdprDetails = listOfNotNull(
            gdpr?.regularOrRiskyProcessing, gdpr?.specialOrCriminalData,
            gdpr?.largeScaleMonitoring, gdpr?.largeScaleSpecialData,
        ).any { it }
        when {
            processesPersonalData == null ->
                fail("gdpr.processesPersonalData", "Diga se a empresa trata dados de pessoas.")
            !processesPersonalData && gdprDetails -> fail(
                "gdpr",
                "Se a empresa não trata dados de pessoas, deixe em branco as outras perguntas sobre dados pessoais.",
            )
        }

        val ai = input.aiAct
        val usesOrProvides = ai?.usesAiSystems == true || ai?.providesAiSystems == true
        if (!usesOrProvides && (ai?.highRiskArea == true || ai?.interactsOrGeneratesContent == true)) {
            fail(
                "aiAct",
                "Se a empresa não usa nem desenvolve inteligência artificial, deixe em branco as outras perguntas sobre IA.",
            )
        }

        if (errors.isNotEmpty()) return AnswersValidation.Invalid(errors)
        return AnswersValidation.Valid(
            ApplicabilityAnswers(
                publicBody = input.publicBody == true,
                annualBalanceSheetEur = balanceSheet,
                nis2 = Nis2Answers(
                    // Annex order, so the same answers always read and audit the same way (RNF05).
                    entityTypes = names.map(entityTypesByName::getValue).sortedBy { it.ordinal }.toCollection(LinkedHashSet()),
                    criticalEntity = input.nis2?.criticalEntity == true,
                    significantImpact = input.nis2?.significantImpact == true,
                    higherEducation = input.nis2?.higherEducation == true,
                ),
                gdpr = GdprAnswers(
                    processesPersonalData = checkNotNull(processesPersonalData),
                    regularOrRiskyProcessing = gdpr?.regularOrRiskyProcessing == true,
                    specialOrCriminalData = gdpr?.specialOrCriminalData == true,
                    largeScaleMonitoring = gdpr?.largeScaleMonitoring == true,
                    largeScaleSpecialData = gdpr?.largeScaleSpecialData == true,
                ),
                aiAct = AiActAnswers(
                    usesAiSystems = ai?.usesAiSystems == true,
                    providesAiSystems = ai?.providesAiSystems == true,
                    highRiskArea = ai?.highRiskArea == true,
                    interactsOrGeneratesContent = ai?.interactsOrGeneratesContent == true,
                ),
            ),
        )
    }
}
