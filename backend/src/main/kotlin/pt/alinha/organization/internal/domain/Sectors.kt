package pt.alinha.organization.internal.domain

/** NIS2 annex of a sector (DL 125/2025, Annexes I and II); null means the sector is not covered. */
enum class Annex { I, II }

data class Sector(val id: String, val label: String, val annex: Annex?, val subsectors: Map<String, String> = emptyMap())

/**
 * Sectors and subsectors of the Regime Jurídico da Cibersegurança (DL 125/2025, NIS2), with the same ids as the
 * registration wizard (frontend prototype `sectors.ts`). The annex feeds the NIS2 classification (organization#4).
 */
object Sectors {
    val all: List<Sector> = listOf(
        Sector("energia", "Energia", Annex.I, mapOf(
            "eletricidade" to "Eletricidade",
            "aquecimento" to "Aquecimento e arrefecimento urbano",
            "petroleo" to "Petróleo",
            "gas" to "Gás",
            "hidrogenio" to "Hidrogénio",
        )),
        Sector("transportes", "Transportes", Annex.I, mapOf(
            "aereo" to "Transporte aéreo",
            "ferroviario" to "Transporte ferroviário",
            "maritimo" to "Transporte marítimo e fluvial",
            "rodoviario" to "Transporte rodoviário",
        )),
        Sector("bancario", "Setor bancário", Annex.I),
        Sector("financeiro", "Infraestruturas do mercado financeiro", Annex.I),
        Sector("saude", "Saúde", Annex.I),
        Sector("agua", "Água potável", Annex.I),
        Sector("aguas_residuais", "Águas residuais", Annex.I),
        Sector("infra_digitais", "Infraestruturas digitais", Annex.I),
        Sector("tic", "Gestão de serviços de TIC (entre empresas)", Annex.I),
        Sector("espaco", "Espaço", Annex.I),
        Sector("postais", "Serviços postais e de estafeta", Annex.II),
        Sector("residuos", "Gestão de resíduos", Annex.II),
        Sector("quimicos", "Produção, fabrico e distribuição de produtos químicos", Annex.II),
        Sector("alimentar", "Produção, transformação e distribuição de produtos alimentares", Annex.II),
        Sector("industria", "Indústria transformadora", Annex.II, mapOf(
            "dispositivos_medicos" to "Fabrico de dispositivos médicos",
            "informatica" to "Fabrico de equipamentos informáticos, eletrónicos e óticos",
            "eletrico" to "Fabrico de equipamento elétrico",
            "maquinas" to "Fabrico de máquinas e equipamentos",
            "automoveis" to "Fabrico de veículos automóveis, reboques e componentes",
            "outro_transporte" to "Fabrico de outro equipamento de transporte",
        )),
        Sector("digitais", "Prestadores de serviços digitais", Annex.II),
        Sector("investigacao", "Investigação", Annex.II),
        Sector("outro", "Outro setor (não está nesta lista)", null),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String?): Sector? = id?.let(byId::get)
}
