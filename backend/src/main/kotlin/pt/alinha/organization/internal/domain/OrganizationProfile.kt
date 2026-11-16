package pt.alinha.organization.internal.domain

/** Head office. */
data class Address(val street: String, val postalCode: String, val city: String)

/** The company's general contacts. */
data class Contacts(val phone: String, val email: String)

/** Person the CNCS can reach at any time in case of an incident. */
data class PermanentContact(val name: String, val phone: String)

/**
 * Organization data needed for the NIS2 classification (FR01), already validated and normalised:
 * NIF, CAE and phones are digits only, the email is lower case.
 */
data class OrganizationProfile(
    val name: String,
    val nif: String,
    val cae: String,
    val sector: String,
    val subsector: String?,
    val services: String,
    val employees: Int,
    /** Annual turnover in whole euros. */
    val turnover: Long,
    val address: Address,
    val contacts: Contacts,
    val permanentContact: PermanentContact,
)

/** What the client sends, before validation. Every field may be missing. */
data class OrganizationProfileInput(
    val name: String? = null,
    val nif: String? = null,
    val cae: String? = null,
    val sector: String? = null,
    val subsector: String? = null,
    val services: String? = null,
    val employees: Long? = null,
    val turnover: Long? = null,
    val address: AddressInput? = null,
    val contacts: ContactsInput? = null,
    val permanentContact: PermanentContactInput? = null,
)

data class AddressInput(val street: String? = null, val postalCode: String? = null, val city: String? = null)
data class ContactsInput(val phone: String? = null, val email: String? = null)
data class PermanentContactInput(val name: String? = null, val phone: String? = null)

/** One invalid field; [field] is the JSON path (e.g. `address.postalCode`), [message] is shown to the user. */
data class FieldError(val field: String, val message: String)

sealed interface Validation {
    data class Valid(val profile: OrganizationProfile) : Validation
    data class Invalid(val errors: List<FieldError>) : Validation
}
