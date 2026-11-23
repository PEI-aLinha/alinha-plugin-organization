package pt.alinha.organization.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import pt.alinha.core.compliance.Nis2EntityType
import pt.alinha.core.compliance.SizeData

/** A car-parts maker that processes personal data regularly and uses AI assistants at work. */
fun validAnswersInput(entityTypes: List<String> = listOf("MOTOR_VEHICLE_MANUFACTURING")) = ApplicabilityAnswersInput(
    publicBody = false,
    annualBalanceSheetEur = 12_000_000,
    nis2 = Nis2AnswersInput(entityTypes = entityTypes, criticalEntity = false, significantImpact = false, higherEducation = false),
    gdpr = GdprAnswersInput(processesPersonalData = true, regularOrRiskyProcessing = true),
    aiAct = AiActAnswersInput(usesAiSystems = true),
)

class ApplicabilityAnswersValidatorTest {

    private fun errorsOf(input: ApplicabilityAnswersInput): Map<String, String> =
        when (val result = ApplicabilityAnswersValidator.validate(input)) {
            is AnswersValidation.Valid -> emptyMap()
            is AnswersValidation.Invalid -> result.errors.associate { it.field to it.message }
        }

    private fun valid(input: ApplicabilityAnswersInput = validAnswersInput()): ApplicabilityAnswers {
        val result = ApplicabilityAnswersValidator.validate(input)
        assertThat(result).isInstanceOf(AnswersValidation.Valid::class.java)
        return (result as AnswersValidation.Valid).answers
    }

    @Test
    fun `a full set of answers is valid and keeps entity types in annex order`() {
        val answers = valid(validAnswersInput(listOf("CLOUD_COMPUTING_PROVIDER", "DNS_SERVICE_PROVIDER")))

        assertThat(answers.nis2.entityTypes.toList())
            .containsExactly(Nis2EntityType.DNS_SERVICE_PROVIDER, Nis2EntityType.CLOUD_COMPUTING_PROVIDER)
        assertThat(answers.gdpr.regularOrRiskyProcessing).isTrue()
        assertThat(answers.aiAct.usesAiSystems).isTrue()
        assertThat(answers.annualBalanceSheetEur).isEqualTo(12_000_000)
    }

    @Test
    fun `repeated entity types count once`() {
        val answers = valid(validAnswersInput(listOf("FOOD", "FOOD")))

        assertThat(answers.nis2.entityTypes).containsExactly(Nis2EntityType.FOOD)
    }

    @Test
    fun `omitted booleans are false and omitted entity types are none`() {
        val answers = valid(ApplicabilityAnswersInput(gdpr = GdprAnswersInput(processesPersonalData = false)))

        assertThat(answers.publicBody).isFalse()
        assertThat(answers.annualBalanceSheetEur).isNull()
        assertThat(answers.nis2.entityTypes).isEmpty()
        assertThat(answers.nis2.criticalEntity).isFalse()
        assertThat(answers.nis2.significantImpact).isFalse()
        assertThat(answers.nis2.higherEducation).isFalse()
        assertThat(answers.gdpr.processesPersonalData).isFalse()
        assertThat(answers.aiAct.usesAiSystems).isFalse()
        assertThat(answers.aiAct.providesAiSystems).isFalse()
    }

    @Test
    fun `the personal data answer is required`() {
        assertThat(errorsOf(validAnswersInput().copy(gdpr = null)))
            .containsExactlyEntriesOf(mapOf("gdpr.processesPersonalData" to "Diga se a empresa trata dados de pessoas."))
        assertThat(errorsOf(validAnswersInput().copy(gdpr = GdprAnswersInput())))
            .containsExactlyEntriesOf(mapOf("gdpr.processesPersonalData" to "Diga se a empresa trata dados de pessoas."))
    }

    @Test
    fun `contradictory GDPR answers are field errors, not exceptions`() {
        val input = validAnswersInput().copy(
            gdpr = GdprAnswersInput(processesPersonalData = false, specialOrCriminalData = true),
        )

        assertThat(errorsOf(input)).containsExactlyEntriesOf(
            mapOf(
                "gdpr" to "Se a empresa não trata dados de pessoas, deixe em branco as outras perguntas sobre dados pessoais.",
            ),
        )
    }

    @Test
    fun `AI details without using or providing AI are a field error`() {
        val input = validAnswersInput().copy(aiAct = AiActAnswersInput(interactsOrGeneratesContent = true))

        assertThat(errorsOf(input)).containsExactlyEntriesOf(
            mapOf(
                "aiAct" to "Se a empresa não usa nem desenvolve inteligência artificial, deixe em branco as outras perguntas sobre IA.",
            ),
        )
    }

    @Test
    fun `an unknown entity type is a field error`() {
        assertThat(errorsOf(validAnswersInput(listOf("FOOD", "TELEPORTATION")))).containsExactlyEntriesOf(
            mapOf("nis2.entityTypes" to "Há um tipo de entidade que não existe. Recarregue a página e escolha de novo."),
        )
    }

    @Test
    fun `a negative balance sheet is a field error`() {
        assertThat(errorsOf(validAnswersInput().copy(annualBalanceSheetEur = -1))).containsExactlyEntriesOf(
            mapOf("annualBalanceSheetEur" to "Use só números, em euros e sem cêntimos. Exemplo: 18 500 000."),
        )
    }

    @Test
    fun `every error is reported at once`() {
        val input = ApplicabilityAnswersInput(
            annualBalanceSheetEur = -5,
            nis2 = Nis2AnswersInput(entityTypes = listOf("NOPE")),
            aiAct = AiActAnswersInput(highRiskArea = true),
        )

        val result = ApplicabilityAnswersValidator.validate(input) as AnswersValidation.Invalid

        assertThat(result.errors.map { it.field }).containsExactly(
            "annualBalanceSheetEur", "nis2.entityTypes", "gdpr.processesPersonalData", "aiAct",
        )
    }

    @Test
    fun `toInput takes the size from the profile`() {
        val input = valid().toInput(employees = 40, turnover = 9_000_000)

        assertThat(input.size).isEqualTo(SizeData(40, 9_000_000, 12_000_000))
        assertThat(input.nis2.entityTypes).containsExactly(Nis2EntityType.MOTOR_VEHICLE_MANUFACTURING)
        assertThat(input.publicBody).isFalse()
    }
}
