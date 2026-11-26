package pt.alinha.organization.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import pt.alinha.core.tenant.TenantOwnedEntity
import pt.alinha.organization.internal.domain.Address
import pt.alinha.organization.internal.domain.Contacts
import pt.alinha.organization.internal.domain.OrganizationProfile
import pt.alinha.organization.internal.domain.PermanentContact
import java.time.Instant
import java.util.UUID

@Entity
@Table(schema = "organization", name = "organization_profile")
class OrganizationProfileEntity(
    @Id val id: UUID,
    @Column(name = "registered_at", nullable = false, updatable = false) val registeredAt: Instant,
    profile: OrganizationProfile,
) : TenantOwnedEntity() {

    @Version
    var version: Long = 0
        protected set

    var name: String = profile.name
        protected set
    /** Fixed at registration: the NIF identifies the company (organization#2). */
    @Column(updatable = false)
    val nif: String = profile.nif
    var cae: String = profile.cae
        protected set
    var sector: String = profile.sector
        protected set
    var subsector: String? = profile.subsector
        protected set
    var services: String = profile.services
        protected set
    var employees: Int = profile.employees
        protected set
    var turnover: Long = profile.turnover
        protected set
    var street: String = profile.address.street
        protected set
    @Column(name = "postal_code")
    var postalCode: String = profile.address.postalCode
        protected set
    var city: String = profile.address.city
        protected set
    var phone: String = profile.contacts.phone
        protected set
    var email: String = profile.contacts.email
        protected set
    @Column(name = "permanent_contact_name")
    var permanentContactName: String = profile.permanentContact.name
        protected set
    @Column(name = "permanent_contact_phone")
    var permanentContactPhone: String = profile.permanentContact.phone
        protected set
    @Column(name = "domain")
    var domain: String? = profile.domain
        protected set
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = registeredAt
        protected set

    fun toProfile() = OrganizationProfile(
        name = name,
        nif = nif,
        cae = cae,
        sector = sector,
        subsector = subsector,
        services = services,
        employees = employees,
        turnover = turnover,
        address = Address(street, postalCode, city),
        contacts = Contacts(phone, email),
        permanentContact = PermanentContact(permanentContactName, permanentContactPhone),
        domain = domain,
    )

    /**
     * Applies [profile] and returns the names of the fields that changed (for the audit trail, no values). The NIF
     * never changes: the service refuses a different one before calling this.
     */
    fun update(profile: OrganizationProfile, at: Instant): List<String> {
        val before = toProfile()
        val changed = listOfNotNull(
            "name".takeIf { before.name != profile.name },
            "cae".takeIf { before.cae != profile.cae },
            "sector".takeIf { before.sector != profile.sector },
            "subsector".takeIf { before.subsector != profile.subsector },
            "services".takeIf { before.services != profile.services },
            "employees".takeIf { before.employees != profile.employees },
            "turnover".takeIf { before.turnover != profile.turnover },
            "address".takeIf { before.address != profile.address },
            "contacts".takeIf { before.contacts != profile.contacts },
            "permanentContact".takeIf { before.permanentContact != profile.permanentContact },
            "domain".takeIf { before.domain != profile.domain },
        )
        if (changed.isEmpty()) return changed
        name = profile.name
        cae = profile.cae
        sector = profile.sector
        subsector = profile.subsector
        services = profile.services
        employees = profile.employees
        turnover = profile.turnover
        street = profile.address.street
        postalCode = profile.address.postalCode
        city = profile.address.city
        phone = profile.contacts.phone
        email = profile.contacts.email
        permanentContactName = profile.permanentContact.name
        permanentContactPhone = profile.permanentContact.phone
        domain = profile.domain
        updatedAt = at
        return changed
    }
}
