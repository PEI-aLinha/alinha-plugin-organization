package pt.alinha.organization.internal.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import pt.alinha.core.access.RequiresPermission
import pt.alinha.core.tenant.TenantAgnostic
import pt.alinha.organization.api.OrganizationProfileView
import pt.alinha.organization.internal.OrganizationNotRegisteredException
import pt.alinha.organization.internal.OrganizationService
import pt.alinha.organization.internal.domain.AddressInput
import pt.alinha.organization.internal.domain.ContactsInput
import pt.alinha.organization.internal.domain.OrganizationProfileInput
import pt.alinha.organization.internal.domain.PermanentContactInput

/** Body of `PUT /profile`: the organization data plus the version the form was loaded with. */
data class OrganizationProfileUpdate(
    val version: Long? = null,
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
    val domain: String? = null,
) {
    fun toInput() = OrganizationProfileInput(
        name, nif, cae, sector, subsector, services, employees, turnover, address, contacts, permanentContact, domain,
    )
}

@RestController
@RequestMapping("/api/organization")
class OrganizationController(private val organizations: OrganizationService) {

    /** Walking skeleton still used by the plugin page; remove with it when the wizard lands (organization#3). */
    @RequiresPermission("plugins")
    @GetMapping("/ping")
    fun ping() = mapOf("plugin" to "organization", "status" to "ok")

    /** A new account registers its organization; the tenant is created here, so none is resolved (ADR 0004). */
    @TenantAgnostic
    @RequiresPermission("organization", "register")
    @PostMapping("/registration")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody input: OrganizationProfileInput): OrganizationProfileView = organizations.register(input)

    @RequiresPermission("organization", "read")
    @GetMapping("/profile")
    fun profile(): OrganizationProfileView =
        organizations.currentProfile() ?: throw OrganizationNotRegisteredException()

    @RequiresPermission("organization", "edit")
    @PutMapping("/profile")
    fun update(@RequestBody body: OrganizationProfileUpdate): OrganizationProfileView =
        organizations.update(body.toInput(), body.version)
}
