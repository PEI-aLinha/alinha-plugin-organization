package pt.alinha.organization.api

import java.time.Instant
import java.util.UUID

/**
 * This plugin's «api» contract: the ONLY synchronous way for other plugins
 * to ask it for data. Everything under pt.alinha.organization.internal is private.
 */
interface OrganizationApi {
    /** The current tenant's organization, or null before it registers. */
    fun currentProfile(): OrganizationProfileView?
}

/** Organization data as other plugins and the API see it. NIF, CAE and phones are digits only. */
data class OrganizationProfileView(
    val id: UUID,
    val version: Long,
    val name: String,
    val nif: String,
    val cae: String,
    /** Sector id (DL 125/2025 annexes), e.g. `industria`. */
    val sector: String,
    val subsector: String?,
    /** NIS2 annex of the sector (`I`, `II`), null when the sector is not covered. */
    val sectorAnnex: String?,
    val services: String,
    val employees: Int,
    /** Annual turnover in whole euros. */
    val turnover: Long,
    val address: AddressView,
    val contacts: ContactsView,
    val permanentContact: PermanentContactView,
    /**
     * The organization's own internet domain (website and email), e.g. `empresa.pt`: lower case, internationalized
     * names in ASCII form (`xn--...`), never an IP address. Null when the organization did not give one.
     */
    val domain: String?,
    val registeredAt: Instant,
    val updatedAt: Instant,
)

data class AddressView(val street: String, val postalCode: String, val city: String)
data class ContactsView(val phone: String, val email: String)
data class PermanentContactView(val name: String, val phone: String)
