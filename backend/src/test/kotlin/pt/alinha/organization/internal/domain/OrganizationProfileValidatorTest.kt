package pt.alinha.organization.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Rui Santos's company, the prototype's example (persona 2), as the wizard sends it. */
fun validInput() = OrganizationProfileInput(
    name = " Componentes Águeda, Lda. ",
    nif = "509 180 027",
    cae = "29320",
    sector = "industria",
    subsector = "automoveis",
    services = "Fabrico de componentes metálicos e plásticos para a indústria automóvel.",
    employees = 120,
    turnover = 18_500_000,
    address = AddressInput("Rua da Zona Industrial do Casarão, 45", "3750-750", "Águeda"),
    contacts = ContactsInput("234 600 120", "Geral@CompAgueda.pt"),
    permanentContact = PermanentContactInput("Rui Santos", "912 345 678"),
)

class OrganizationProfileValidatorTest {

    private fun errorsOf(input: OrganizationProfileInput): Map<String, String> =
        when (val result = OrganizationProfileValidator.validate(input)) {
            is Validation.Valid -> emptyMap()
            is Validation.Invalid -> result.errors.associate { it.field to it.message }
        }

    @Test
    fun `valid data is normalised`() {
        val result = OrganizationProfileValidator.validate(validInput())

        assertThat(result).isInstanceOf(Validation.Valid::class.java)
        val profile = (result as Validation.Valid).profile
        assertThat(profile.name).isEqualTo("Componentes Águeda, Lda.")
        assertThat(profile.nif).isEqualTo("509180027")
        assertThat(profile.contacts).isEqualTo(Contacts("234600120", "geral@compagueda.pt"))
        assertThat(profile.permanentContact.phone).isEqualTo("912345678")
    }

    @Test
    fun `every missing field is reported at once, with its JSON path`() {
        val errors = errorsOf(OrganizationProfileInput())

        assertThat(errors.keys).containsExactlyInAnyOrder(
            "name", "nif", "cae", "sector", "services", "employees", "turnover",
            "address.street", "address.postalCode", "address.city", "contacts.phone", "contacts.email",
            "permanentContact.name", "permanentContact.phone",
        )
        assertThat(errors["nif"]).isEqualTo("Indique o NIF da empresa.")
    }

    @ParameterizedTest
    @ValueSource(strings = ["509180027", "501964843", "123456789"])
    fun `accepts NIFs with a valid check digit`(nif: String) {
        assertThat(isValidNif(nif)).isTrue()
    }

    @Test
    fun `rejects a wrong check digit and says so`() {
        assertThat(isValidNif("509180028")).isFalse()
        assertThat(errorsOf(validInput().copy(nif = "509180028"))["nif"])
            .isEqualTo("Este NIF não é válido. Confirme se algum número está trocado.")
    }

    @Test
    fun `a remainder below 2 gives check digit 0`() {
        // 10000000x: sum = 9, 9 % 11 = 9 -> 2; 50000000x: 45 % 11 = 1 -> 0
        assertThat(isValidNif("500000000")).isTrue()
        assertThat(isValidNif("500000001")).isFalse()
    }

    @Test
    fun `the NIF length is explained`() {
        assertThat(errorsOf(validInput().copy(nif = "50918"))["nif"]).isEqualTo("O NIF tem 9 dígitos. Este tem 5.")
    }

    @Test
    fun `the CAE has 5 digits`() {
        assertThat(errorsOf(validInput().copy(cae = "2932"))).containsKey("cae")
        assertThat(errorsOf(validInput().copy(cae = "29 320"))).doesNotContainKey("cae")
    }

    @Test
    fun `the subsector is required only when the sector has subsectors`() {
        assertThat(errorsOf(validInput().copy(subsector = null))["subsector"]).isEqualTo("Escolha o subsetor.")
        assertThat(errorsOf(validInput().copy(subsector = "eletricidade"))).containsKey("subsector")
        assertThat(errorsOf(validInput().copy(sector = "saude", subsector = null))).isEmpty()
        assertThat(errorsOf(validInput().copy(sector = "saude", subsector = "automoveis"))).containsKey("subsector")
    }

    @Test
    fun `an unknown sector is refused`() {
        assertThat(errorsOf(validInput().copy(sector = "mineracao"))).containsKey("sector")
    }

    @Test
    fun `size must be positive`() {
        assertThat(errorsOf(validInput().copy(employees = 0))).containsKey("employees")
        assertThat(errorsOf(validInput().copy(turnover = -1))).containsKey("turnover")
        assertThat(errorsOf(validInput().copy(turnover = 0))).doesNotContainKey("turnover")
    }

    @Test
    fun `services need a short description`() {
        assertThat(errorsOf(validInput().copy(services = "Fábrica"))).containsKey("services")
    }

    @Test
    fun `postal code, phones and email follow the Portuguese formats`() {
        val errors = errorsOf(
            validInput().copy(
                address = AddressInput("Rua A", "3750750", "Águeda"),
                contacts = ContactsInput("134600120", "geral@empresa"),
                permanentContact = PermanentContactInput("Rui", "91234567"),
            ),
        )

        assertThat(errors).containsOnlyKeys("address.postalCode", "contacts.phone", "contacts.email", "permanentContact.phone")
        assertThat(errors["address.postalCode"]).isEqualTo("Use o formato 0000-000.")
    }

    @Test
    fun `email check needs one at sign and a dotted domain`() {
        assertThat(isValidEmail("geral@empresa.pt")).isTrue()
        listOf("geral@@empresa.pt", "@empresa.pt", "geral@empresa", "geral@.pt", "ge ral@empresa.pt").forEach {
            assertThat(isValidEmail(it)).describedAs(it).isFalse()
        }
    }
}
