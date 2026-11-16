package pt.alinha.organization.internal.domain

private const val NIF_LENGTH = 9
private const val CAE_LENGTH = 5
private const val MIN_SERVICES_LENGTH = 10
private val POSTAL_CODE = Regex("""^\d{4}-\d{3}$""")
private val PHONE = Regex("""^[29]\d{8}$""")
private val WHITESPACE = Regex("""\s""")

fun digits(value: String): String = value.filter(Char::isDigit)

/** Portuguese NIF: 9 digits, the last one a mod-11 check digit (same rule as the registration wizard). */
fun isValidNif(value: String): Boolean {
    val d = digits(value)
    if (d.length != NIF_LENGTH) return false
    val sum = d.take(NIF_LENGTH - 1).mapIndexed { i, c -> c.digitToInt() * (NIF_LENGTH - i) }.sum()
    val rest = sum % 11
    return d.last().digitToInt() == if (rest < 2) 0 else 11 - rest
}

/** One "@", no spaces, a dotted domain with no empty label. No regex, so no backtracking. */
fun isValidEmail(value: String): Boolean {
    if (WHITESPACE.containsMatchIn(value)) return false
    val parts = value.split('@')
    if (parts.size != 2 || parts[0].isEmpty()) return false
    val labels = parts[1].split('.')
    return labels.size >= 2 && labels.all(String::isNotEmpty)
}

/**
 * Validates and normalises the organization data. Rules and messages match the registration wizard
 * (frontend prototype `organizationForm.ts`), in plain European Portuguese.
 */
object OrganizationProfileValidator {

    fun validate(input: OrganizationProfileInput): Validation {
        val errors = mutableListOf<FieldError>()
        fun fail(field: String, message: String) { errors += FieldError(field, message) }

        val name = input.name.clean()
        if (name.isEmpty()) fail("name", "Indique o nome da empresa, como está na certidão permanente.")

        val nif = digits(input.nif.orEmpty())
        when {
            nif.isEmpty() -> fail("nif", "Indique o NIF da empresa.")
            nif.length != NIF_LENGTH -> fail("nif", "O NIF tem 9 dígitos. Este tem ${nif.length}.")
            !isValidNif(nif) -> fail("nif", "Este NIF não é válido. Confirme se algum número está trocado.")
        }

        val cae = digits(input.cae.orEmpty())
        when {
            cae.isEmpty() -> fail("cae", "Indique o código CAE principal.")
            cae.length != CAE_LENGTH -> fail("cae", "O código CAE tem 5 dígitos. Exemplo: 29320.")
        }

        val sector = Sectors.byId(input.sector)
        val subsector = input.subsector.clean().ifEmpty { null }
        when {
            sector == null -> fail("sector", "Escolha o setor de atividade.")
            sector.subsectors.isEmpty() && subsector != null -> fail("subsector", "Este setor não tem subsetores.")
            sector.subsectors.isNotEmpty() && subsector !in sector.subsectors -> fail("subsector", "Escolha o subsetor.")
        }

        val services = input.services.clean()
        if (services.length < MIN_SERVICES_LENGTH) {
            fail("services", "Descreva em poucas palavras o que a empresa faz ou vende.")
        }

        val employees = input.employees
        when {
            employees == null -> fail("employees", "Indique quantas pessoas trabalham na empresa.")
            employees < 1 || employees > Int.MAX_VALUE -> fail("employees", "Use só números inteiros. Exemplo: 120.")
        }

        val turnover = input.turnover
        when {
            turnover == null -> fail("turnover", "Indique o volume de negócios do último ano.")
            turnover < 0 -> fail("turnover", "Use só números, em euros e sem cêntimos. Exemplo: 18 500 000.")
        }

        val street = input.address?.street.clean()
        if (street.isEmpty()) fail("address.street", "Indique a morada da sede.")
        val postalCode = input.address?.postalCode.clean()
        when {
            postalCode.isEmpty() -> fail("address.postalCode", "Indique o código postal.")
            !POSTAL_CODE.matches(postalCode) -> fail("address.postalCode", "Use o formato 0000-000.")
        }
        val city = input.address?.city.clean()
        if (city.isEmpty()) fail("address.city", "Indique a localidade.")

        val phone = digits(input.contacts?.phone.orEmpty())
        when {
            phone.isEmpty() -> fail("contacts.phone", "Indique o telefone da empresa.")
            !PHONE.matches(phone) -> fail("contacts.phone", "O telefone tem 9 dígitos e começa por 2 ou 9.")
        }
        val email = input.contacts?.email.clean().lowercase()
        when {
            email.isEmpty() -> fail("contacts.email", "Indique o email geral da empresa.")
            !isValidEmail(email) -> fail("contacts.email", "Este email não parece válido. Exemplo: geral@empresa.pt")
        }

        val contactName = input.permanentContact?.name.clean()
        if (contactName.isEmpty()) fail("permanentContact.name", "Indique quem é o ponto de contacto permanente.")
        val contactPhone = digits(input.permanentContact?.phone.orEmpty())
        when {
            contactPhone.isEmpty() -> fail("permanentContact.phone", "Indique um telefone disponível a qualquer hora.")
            !PHONE.matches(contactPhone) -> fail("permanentContact.phone", "O telefone tem 9 dígitos e começa por 2 ou 9.")
        }

        if (errors.isNotEmpty()) return Validation.Invalid(errors)
        return Validation.Valid(
            OrganizationProfile(
                name = name,
                nif = nif,
                cae = cae,
                sector = checkNotNull(sector).id,
                subsector = subsector,
                services = services,
                employees = checkNotNull(employees).toInt(),
                turnover = checkNotNull(turnover),
                address = Address(street, postalCode, city),
                contacts = Contacts(phone, email),
                permanentContact = PermanentContact(contactName, contactPhone),
            ),
        )
    }

    private fun String?.clean(): String = this?.trim().orEmpty()
}
