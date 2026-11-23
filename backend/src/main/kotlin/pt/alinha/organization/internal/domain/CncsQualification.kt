package pt.alinha.organization.internal.domain

import pt.alinha.core.compliance.Nis2Classification
import java.time.LocalDate

/**
 * The official NIS2 qualification, from the Qualification Act (Ato de Qualificação) the CNCS issues after the
 * organization registers on MyCiber (US11). It overrides the indicative classification of the applicability rules.
 */
data class CncsQualification(
    /** ESSENTIAL or IMPORTANT: the CNCS only qualifies covered entities. */
    val classification: Nis2Classification,
    val actDate: LocalDate,
    /** The Act's reference, as printed on it; optional. */
    val actReference: String?,
)

/** What the client sends, before validation. [classification] is the contract value (`essential`, `important`). */
data class CncsQualificationInput(
    val classification: String? = null,
    val actDate: LocalDate? = null,
    val actReference: String? = null,
)

sealed interface QualificationValidation {
    data class Valid(val qualification: CncsQualification) : QualificationValidation
    data class Invalid(val errors: List<FieldError>) : QualificationValidation
}

object CncsQualificationValidator {
    /** Decree-Law 125/2025 (Regime Jurídico da Cibersegurança) was published on this day: no Act is older. */
    val RJC_PUBLISHED: LocalDate = LocalDate.of(2025, 12, 4)
    const val MAX_REFERENCE = 100

    /** Contract values of the classifications an Act can carry. */
    private val OFFICIAL = mapOf(
        "essential" to Nis2Classification.ESSENTIAL,
        "important" to Nis2Classification.IMPORTANT,
    )

    /** [today] is the date in Portugal (Europe/Lisbon), given by the caller so tests can fix it. */
    fun validate(input: CncsQualificationInput, today: LocalDate): QualificationValidation {
        val errors = mutableListOf<FieldError>()
        fun fail(field: String, message: String) { errors += FieldError(field, message) }

        val classification = OFFICIAL[input.classification]
        if (classification == null) fail("classification", "Escolha a classificação que está no Ato de Qualificação.")

        val actDate = input.actDate
        when {
            actDate == null -> fail("actDate", "Indique a data do Ato de Qualificação.")
            actDate.isAfter(today) -> fail("actDate", "A data não pode ser no futuro.")
            actDate.isBefore(RJC_PUBLISHED) ->
                fail("actDate", "A data não pode ser anterior a 4 de dezembro de 2025, quando a lei foi publicada.")
        }

        val reference = input.actReference.singleLine().ifEmpty { null }
        if (reference != null && reference.length > MAX_REFERENCE) {
            fail("actReference", "Use no máximo $MAX_REFERENCE caracteres.")
        }

        if (errors.isNotEmpty()) return QualificationValidation.Invalid(errors)
        return QualificationValidation.Valid(
            CncsQualification(checkNotNull(classification), checkNotNull(actDate), reference),
        )
    }
}
