package pt.alinha.organization.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import pt.alinha.core.compliance.Nis2EntityType

class SectorEntityTypesTest {

    @Test
    fun `annexSection cuts at the first dot`() {
        assertThat(annexSection("Anexo I, 1.a)")).isEqualTo("Anexo I, 1.")
        assertThat(annexSection("Anexo I, 10.")).isEqualTo("Anexo I, 10.")
        assertThat(annexSection("Anexo II, 5.f)")).isEqualTo("Anexo II, 5.")
    }

    @Test
    fun `energy never suggests space types`() {
        val energy = suggestedEntityTypes("energia", null, "35110")

        assertThat(energy).contains(Nis2EntityType.ELECTRICITY_PRODUCER, Nis2EntityType.HYDROGEN_OPERATOR)
        assertThat(energy).doesNotContain(Nis2EntityType.SPACE_GROUND_INFRASTRUCTURE)
    }

    @Test
    fun `space suggests only space types`() {
        assertThat(suggestedEntityTypes("espaco", null, "51220"))
            .containsExactly(Nis2EntityType.SPACE_GROUND_INFRASTRUCTURE)
    }

    @Test
    fun `a subsector suggests only its row`() {
        assertThat(suggestedEntityTypes("energia", "hidrogenio", "20110"))
            .containsExactly(Nis2EntityType.HYDROGEN_OPERATOR)
    }

    @Test
    fun `digital infrastructure suggests DNS, TLD, cloud and telecoms`() {
        assertThat(suggestedEntityTypes("infra_digitais", null, "61100")).contains(
            Nis2EntityType.DNS_SERVICE_PROVIDER,
            Nis2EntityType.TLD_NAME_REGISTRY,
            Nis2EntityType.CLOUD_COMPUTING_PROVIDER,
            Nis2EntityType.PUBLIC_ELECTRONIC_COMMUNICATIONS_NETWORK,
        )
    }

    @Test
    fun `the CAE adds manufacturing types from another sector`() {
        assertThat(suggestedEntityTypes("outro", null, "29320"))
            .containsExactly(Nis2EntityType.MOTOR_VEHICLE_MANUFACTURING)
    }

    @Test
    fun `the subsector and the CAE add up`() {
        assertThat(suggestedEntityTypes("industria", "dispositivos_medicos", "26110")).containsExactlyInAnyOrder(
            Nis2EntityType.MEDICAL_DEVICES,
            Nis2EntityType.ELECTRONICS_MANUFACTURING,
        )
    }

    @Test
    fun `another sector suggests nothing without a matching CAE`() {
        assertThat(suggestedEntityTypes("outro", null, "47110")).isEmpty()
    }

    @Test
    fun `every sector and subsector is mapped`() {
        Sectors.all.filter { it.annex != null }.forEach { sector ->
            assertThat(suggestedEntityTypes(sector.id, null, "00000")).describedAs(sector.id).isNotEmpty()
            sector.subsectors.keys.forEach { subsector ->
                assertThat(suggestedEntityTypes(sector.id, subsector, "00000")).describedAs("${sector.id}/$subsector").isNotEmpty()
            }
        }
    }
}
