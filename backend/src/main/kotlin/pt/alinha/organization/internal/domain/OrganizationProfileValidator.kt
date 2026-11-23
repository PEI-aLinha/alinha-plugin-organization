package pt.alinha.organization.internal.domain

private const val NIF_LENGTH = 9
private const val CAE_LENGTH = 5
private const val MIN_SERVICES_LENGTH = 10
private val POSTAL_CODE = Regex("""^\d{4}-\d{3}$""")
private val PHONE = Regex("""^[29]\d{8}$""")
private val WHITESPACE = Regex("""\s""")
/** Control characters other than the line break. */
private val CONTROL_BUT_NEWLINE = Regex("""[\p{Cc}&&[^\n]]""")
/** NIF, CAE and phones may be typed with spaces, dots or dashes, never letters or non-ASCII digits. */
private val NUMBER_TYPING = Regex("""^[0-9 .\-]*$""")
private const val MAX_EMPLOYEES = 10_000_000L

/** Maximum lengths of the free-text fields (also enforced by the database). */
internal object MaxLength {
    const val NAME = 200
    const val SERVICES = 2000
    const val STREET = 300
    const val CITY = 120
    const val EMAIL = 254
    const val CONTACT_NAME = 120
}

/** ASCII digits only: `Char.isDigit` would also accept other scripts' digits, which the database refuses. */
fun digits(value: String): String = value.filter { it in '0'..'9' }

/** Raw length limit of NIF, CAE and phones, before spaces, dots and dashes are removed. */
private const val MAX_NUMBER_INPUT = 20

private fun typedAsNumber(value: String?) = value == null || NUMBER_TYPING.matches(value)
private fun tooLongNumber(value: String?) = (value?.length ?: 0) > MAX_NUMBER_INPUT

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

    fun validate(input: OrganizationProfileInput, extraErrors: List<FieldError> = emptyList()): Validation {
        val errors = extraErrors.toMutableList()
        fun fail(field: String, message: String) { errors += FieldError(field, message) }

        val name = input.name.singleLine()
        when {
            name.isEmpty() -> fail("name", "Indique o nome da empresa, como está na certidão permanente.")
            name.length > MaxLength.NAME -> fail("name", tooLong(MaxLength.NAME))
        }

        val nif = digits(input.nif.orEmpty())
        when {
            tooLongNumber(input.nif) -> fail("nif", "Use no máximo $MAX_NUMBER_INPUT caracteres.")
            !typedAsNumber(input.nif) -> fail("nif", "O NIF só tem números.")
            nif.isEmpty() -> fail("nif", "Indique o NIF da empresa.")
            nif.length != NIF_LENGTH -> fail("nif", "O NIF tem 9 dígitos. Este tem ${nif.length}.")
            !isValidNif(nif) -> fail("nif", "Este NIF não é válido. Confirme se algum número está trocado.")
        }

        val cae = digits(input.cae.orEmpty())
        when {
            tooLongNumber(input.cae) -> fail("cae", "Use no máximo $MAX_NUMBER_INPUT caracteres.")
            !typedAsNumber(input.cae) -> fail("cae", "O código CAE só tem números. Exemplo: 29320.")
            cae.isEmpty() -> fail("cae", "Indique o código CAE principal.")
            cae.length != CAE_LENGTH -> fail("cae", "O código CAE tem 5 dígitos. Exemplo: 29320.")
        }

        val sector = Sectors.byId(input.sector)
        val subsector = input.subsector.singleLine().ifEmpty { null }
        when {
            sector == null -> fail("sector", "Escolha o setor de atividade.")
            sector.subsectors.isEmpty() && subsector != null -> fail("subsector", "Este setor não tem subsetores.")
            sector.subsectors.isNotEmpty() && subsector !in sector.subsectors -> fail("subsector", "Escolha o subsetor.")
        }

        val services = input.services.multiLine()
        when {
            services.length < MIN_SERVICES_LENGTH -> fail("services", "Descreva em poucas palavras o que a empresa faz ou vende.")
            services.length > MaxLength.SERVICES -> fail("services", tooLong(MaxLength.SERVICES))
        }

        val employees = input.employees
        when {
            employees == null -> fail("employees", "Indique quantas pessoas trabalham na empresa.")
            employees < 1 -> fail("employees", "A empresa tem de ter pelo menos 1 trabalhador.")
            employees > MAX_EMPLOYEES -> fail("employees", "Confirme o número de trabalhadores: parece demasiado alto.")
        }

        val turnover = input.turnover
        when {
            turnover == null -> fail("turnover", "Indique o volume de negócios do último ano.")
            turnover < 0 -> fail("turnover", "Use só números, em euros e sem cêntimos. Exemplo: 18 500 000.")
        }

        val street = input.address?.street.singleLine()
        when {
            street.isEmpty() -> fail("address.street", "Indique a morada da sede.")
            street.length > MaxLength.STREET -> fail("address.street", tooLong(MaxLength.STREET))
        }
        val postalCode = input.address?.postalCode.singleLine()
        when {
            postalCode.isEmpty() -> fail("address.postalCode", "Indique o código postal.")
            !POSTAL_CODE.matches(postalCode) -> fail("address.postalCode", "Use o formato 0000-000.")
        }
        val city = input.address?.city.singleLine()
        when {
            city.isEmpty() -> fail("address.city", "Indique a localidade.")
            city.length > MaxLength.CITY -> fail("address.city", tooLong(MaxLength.CITY))
        }

        val phone = digits(input.contacts?.phone.orEmpty())
        when {
            tooLongNumber(input.contacts?.phone) -> fail("contacts.phone", "Use no máximo $MAX_NUMBER_INPUT caracteres.")
            !typedAsNumber(input.contacts?.phone) -> fail("contacts.phone", "O telefone só tem números.")
            phone.isEmpty() -> fail("contacts.phone", "Indique o telefone da empresa.")
            !PHONE.matches(phone) -> fail("contacts.phone", "O telefone tem 9 dígitos e começa por 2 ou 9.")
        }
        val email = input.contacts?.email.singleLine().lowercase()
        when {
            email.isEmpty() -> fail("contacts.email", "Indique o email geral da empresa.")
            email.length > MaxLength.EMAIL -> fail("contacts.email", tooLong(MaxLength.EMAIL))
            !isValidEmail(email) -> fail("contacts.email", "Este email não parece válido. Exemplo: geral@empresa.pt")
        }

        val contactName = input.permanentContact?.name.singleLine()
        when {
            contactName.isEmpty() -> fail("permanentContact.name", "Indique quem é o ponto de contacto permanente.")
            contactName.length > MaxLength.CONTACT_NAME -> fail("permanentContact.name", tooLong(MaxLength.CONTACT_NAME))
        }
        val contactPhone = digits(input.permanentContact?.phone.orEmpty())
        when {
            tooLongNumber(input.permanentContact?.phone) -> fail("permanentContact.phone", "Use no máximo $MAX_NUMBER_INPUT caracteres.")
            !typedAsNumber(input.permanentContact?.phone) -> fail("permanentContact.phone", "O telefone só tem números.")
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

    /** Multi-line text: line breaks are kept (as `\n`), other control characters removed, the ends trimmed. */
    private fun String?.multiLine(): String = this.orEmpty().replace("\r\n", "\n").replace(CONTROL_BUT_NEWLINE, "").trim()

    private fun tooLong(max: Int) = "Use no máximo $max caracteres."
}
