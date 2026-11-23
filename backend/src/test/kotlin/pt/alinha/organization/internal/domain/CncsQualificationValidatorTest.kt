package pt.alinha.organization.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import pt.alinha.core.compliance.Nis2Classification
import java.time.LocalDate

private val TODAY: LocalDate = LocalDate.of(2026, 11, 23)

class CncsQualificationValidatorTest {

    private fun validate(input: CncsQualificationInput) = CncsQualificationValidator.validate(input, TODAY)

    private fun errorsOf(input: CncsQualificationInput): Map<String, String> =
        when (val result = validate(input)) {
            is QualificationValidation.Valid -> emptyMap()
            is QualificationValidation.Invalid -> result.errors.associate { it.field to it.message }
        }

    private fun act(
        classification: String? = "essential",
        actDate: LocalDate? = LocalDate.of(2026, 10, 2),
        actReference: String? = "  CNCS/QA/2026/0042 ",
    ) = CncsQualificationInput(classification, actDate, actReference)

    @Test
    fun `a valid act is accepted with a trimmed reference`() {
        val result = validate(act()) as QualificationValidation.Valid

        assertThat(result.qualification).isEqualTo(
            CncsQualification(Nis2Classification.ESSENTIAL, LocalDate.of(2026, 10, 2), "CNCS/QA/2026/0042"),
        )
    }

    @Test
    fun `important is an official classification`() {
        val result = validate(act(classification = "important")) as QualificationValidation.Valid

        assertThat(result.qualification.classification).isEqualTo(Nis2Classification.IMPORTANT)
    }

    @Test
    fun `a blank reference is null`() {
        val result = validate(act(actReference = " \n ")) as QualificationValidation.Valid

        assertThat(result.qualification.actReference).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["not-covered", "to-be-assessed", "ESSENTIAL", "essencial", ""])
    fun `only essential and important are official classifications`(classification: String) {
        assertThat(errorsOf(act(classification = classification))).containsExactlyEntriesOf(
            mapOf("classification" to "Escolha a classificação que está no Ato de Qualificação."),
        )
    }

    @Test
    fun `a missing classification is a field error`() {
        assertThat(errorsOf(act(classification = null))).containsExactlyEntriesOf(
            mapOf("classification" to "Escolha a classificação que está no Ato de Qualificação."),
        )
    }

    @Test
    fun `a missing date is a field error`() {
        assertThat(errorsOf(act(actDate = null))).containsExactlyEntriesOf(
            mapOf("actDate" to "Indique a data do Ato de Qualificação."),
        )
    }

    @Test
    fun `today is accepted and tomorrow is not`() {
        assertThat(errorsOf(act(actDate = TODAY))).isEmpty()
        assertThat(errorsOf(act(actDate = TODAY.plusDays(1)))).containsExactlyEntriesOf(
            mapOf("actDate" to "A data não pode ser no futuro."),
        )
    }

    @Test
    fun `a date before the law is a field error`() {
        assertThat(errorsOf(act(actDate = LocalDate.of(2025, 12, 4)))).isEmpty()
        assertThat(errorsOf(act(actDate = LocalDate.of(2025, 12, 3)))).containsExactlyEntriesOf(
            mapOf("actDate" to "A data não pode ser anterior a 4 de dezembro de 2025, quando a lei foi publicada."),
        )
    }

    @Test
    fun `a reference over 100 characters is a field error`() {
        assertThat(errorsOf(act(actReference = "x".repeat(100)))).isEmpty()
        assertThat(errorsOf(act(actReference = "x".repeat(101)))).containsExactlyEntriesOf(
            mapOf("actReference" to "Use no máximo 100 caracteres."),
        )
    }

    @Test
    fun `every error is reported at once, after the extra ones`() {
        val extra = FieldError("version", "Recarregue a página e volte a fazer as alterações.")

        val result = CncsQualificationValidator.validate(
            CncsQualificationInput(classification = "not-covered", actDate = null, actReference = "y".repeat(200)),
            TODAY,
            listOf(extra),
        ) as QualificationValidation.Invalid

        assertThat(result.errors.map { it.field }).containsExactly("version", "classification", "actDate", "actReference")
    }
}
