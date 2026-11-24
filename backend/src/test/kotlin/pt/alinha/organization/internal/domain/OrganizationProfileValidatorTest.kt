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
    fun `the domain is optional and normalised`() {
        val none = OrganizationProfileValidator.validate(validInput().copy(domain = "  "))
        val typed = OrganizationProfileValidator.validate(validInput().copy(domain = "https://www.CompAgueda.pt/"))

        assertThat((none as Validation.Valid).profile.domain).isNull()
        assertThat((typed as Validation.Valid).profile.domain).isEqualTo("compagueda.pt")
    }

    @Test
    fun `an invalid or shared domain is reported on its field`() {
        assertThat(errorsOf(validInput().copy(domain = "10.0.0.1")))
            .containsEntry("domain", "Indique só o domínio, por exemplo empresa.pt.")
        assertThat(errorsOf(validInput().copy(domain = "gmail.com"))["domain"]).contains("serviço partilhado")
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

    @Test
    fun `non-ASCII digits and letters are refused, spaces dots and dashes are not`() {
        assertThat(errorsOf(validInput().copy(nif = "٥٠٩١٨٠٠٢٧"))).containsKey("nif")
        assertThat(errorsOf(validInput().copy(nif = "abc509180027"))["nif"]).isEqualTo("O NIF só tem números.")
        assertThat(errorsOf(validInput().copy(cae = "２９３２０"))).containsKey("cae")
        assertThat(errorsOf(validInput().copy(nif = "509.180-027"))).doesNotContainKey("nif")
    }

    @Test
    fun `control characters never reach one-line fields, and services keep their line breaks`() {
        val result = OrganizationProfileValidator.validate(
            validInput().copy(
                name = "Componentes\nÁgueda,\u0000 Lda.",
                services = "Fabrico de componentes.\r\nVenda a\u0007 fabricantes.",
                address = AddressInput("Rua\tda Zona Industrial, 45", "3750-750", "Águeda\u0085"),
                permanentContact = PermanentContactInput("Rui\u001b Santos", "912 345 678"),
            ),
        )

        val profile = (result as Validation.Valid).profile
        assertThat(profile.name).isEqualTo("Componentes Águeda, Lda.")
        assertThat(profile.services).isEqualTo("Fabrico de componentes.\nVenda a fabricantes.")
        assertThat(profile.address).isEqualTo(Address("Rua da Zona Industrial, 45", "3750-750", "Águeda"))
        assertThat(profile.permanentContact.name).isEqualTo("Rui Santos")
    }

    @Test
    fun `an email with a line break is invalid, not silently joined`() {
        val errors = errorsOf(validInput().copy(contacts = ContactsInput("234 600 120", "geral@\ncompagueda.pt")))

        assertThat(errors["contacts.email"]).isEqualTo("Este email não parece válido. Exemplo: geral@empresa.pt")
    }

    @Test
    fun `free text has a maximum length`() {
        val errors = errorsOf(
            validInput().copy(
                name = "a".repeat(MaxLength.NAME + 1),
                services = "a".repeat(MaxLength.SERVICES + 1),
                address = AddressInput("a".repeat(MaxLength.STREET + 1), "3750-750", "a".repeat(MaxLength.CITY + 1)),
            ),
        )

        assertThat(errors).containsOnlyKeys("name", "services", "address.street", "address.city")
        assertThat(errors["name"]).isEqualTo("Use no máximo 200 caracteres.")
        assertThat(errorsOf(validInput().copy(name = "a".repeat(MaxLength.NAME)))).isEmpty()
    }

    @Test
    fun `an implausible number of employees has its own message`() {
        assertThat(errorsOf(validInput().copy(employees = 50_000_000))["employees"])
            .isEqualTo("Confirme o número de trabalhadores: parece demasiado alto.")
    }

    @Test
    fun `extra errors are reported together with the field errors`() {
        val errors = errorsOf(validInput().copy(cae = ""))

        val result = OrganizationProfileValidator.validate(validInput().copy(cae = ""), listOf(FieldError("version", "x")))

        assertThat((result as Validation.Invalid).errors.map { it.field }).containsExactly("version", "cae")
        assertThat(errors).containsOnlyKeys("cae")
    }

    @Test
    fun `numbers typed with too many characters are refused before stripping`() {
        val padded = "509 180 027" + " ".repeat(20)

        assertThat(errorsOf(validInput().copy(nif = padded))["nif"]).isEqualTo("Use no máximo 20 caracteres.")
        assertThat(errorsOf(validInput().copy(contacts = ContactsInput("2".repeat(21), "geral@empresa.pt")))).containsKey("contacts.phone")
    }
}
