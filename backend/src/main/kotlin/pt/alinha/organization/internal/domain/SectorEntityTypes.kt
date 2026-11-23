package pt.alinha.organization.internal.domain

import pt.alinha.core.compliance.Nis2EntityType

/**
 * Annex row of each sector (DL 125/2025, annexes I and II), as written in `Nis2EntityType.reference`. A sector
 * maps to its whole section (`Anexo I, 1.`); a subsector to its exact row (`Anexo I, 1.a)`).
 */
private class AnnexRows(val section: String, val subsectorRows: Map<String, String> = emptyMap())

private val SECTOR_ROWS: Map<String, AnnexRows> = mapOf(
    "energia" to AnnexRows(
        "Anexo I, 1.",
        mapOf(
            "eletricidade" to "Anexo I, 1.a)",
            "aquecimento" to "Anexo I, 1.b)",
            "petroleo" to "Anexo I, 1.c)",
            "gas" to "Anexo I, 1.d)",
            "hidrogenio" to "Anexo I, 1.e)",
        ),
    ),
    "transportes" to AnnexRows(
        "Anexo I, 2.",
        mapOf(
            "aereo" to "Anexo I, 2.a)",
            "ferroviario" to "Anexo I, 2.b)",
            "maritimo" to "Anexo I, 2.c)",
            "rodoviario" to "Anexo I, 2.d)",
        ),
    ),
    "bancario" to AnnexRows("Anexo I, 3."),
    "financeiro" to AnnexRows("Anexo I, 4."),
    "saude" to AnnexRows("Anexo I, 5."),
    "agua" to AnnexRows("Anexo I, 6."),
    "aguas_residuais" to AnnexRows("Anexo I, 7."),
    "infra_digitais" to AnnexRows("Anexo I, 8."),
    "tic" to AnnexRows("Anexo I, 9."),
    "espaco" to AnnexRows("Anexo I, 10."),
    "postais" to AnnexRows("Anexo II, 1."),
    "residuos" to AnnexRows("Anexo II, 2."),
    "quimicos" to AnnexRows("Anexo II, 3."),
    "alimentar" to AnnexRows("Anexo II, 4."),
    "industria" to AnnexRows(
        "Anexo II, 5.",
        mapOf(
            "dispositivos_medicos" to "Anexo II, 5.a)",
            "informatica" to "Anexo II, 5.b)",
            "eletrico" to "Anexo II, 5.c)",
            "maquinas" to "Anexo II, 5.d)",
            "automoveis" to "Anexo II, 5.e)",
            "outro_transporte" to "Anexo II, 5.f)",
        ),
    ),
    "digitais" to AnnexRows("Anexo II, 6."),
    "investigacao" to AnnexRows("Anexo II, 7."),
)

/**
 * The section of an annex row: `Anexo I, 1.a)` → `Anexo I, 1.`. Compare sections with equality, never with
 * `startsWith`: `Anexo I, 1.` is a prefix of `Anexo I, 10.` (space).
 */
fun annexSection(reference: String): String = reference.substring(0, reference.indexOf('.') + 1)

/**
 * NIS2 entity types to show first to the user (organization#4): the types of the organization's sector, or of its
 * subsector's row, plus the ones its CAE suggests (`Nis2EntityType.suggestedFor`). Only a suggestion: the user picks.
 */
fun suggestedEntityTypes(sector: String, subsector: String?, cae: String): Set<Nis2EntityType> {
    val rows = SECTOR_ROWS[sector]
    val row = subsector?.let { rows?.subsectorRows?.get(it) }
    val bySector = when {
        rows == null -> emptyList()
        row != null -> Nis2EntityType.entries.filter { it.reference == row }
        else -> Nis2EntityType.entries.filter { annexSection(it.reference) == rows.section }
    }
    return (bySector + Nis2EntityType.suggestedFor(cae)).sortedBy { it.ordinal }.toCollection(LinkedHashSet())
}
