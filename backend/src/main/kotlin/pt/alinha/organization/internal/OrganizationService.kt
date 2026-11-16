package pt.alinha.organization.internal

import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pt.alinha.core.audit.AuditTrail
import pt.alinha.core.tenant.TenantProvisioning
import pt.alinha.organization.api.AddressView
import pt.alinha.organization.api.ContactsView
import pt.alinha.organization.api.OrganizationApi
import pt.alinha.organization.api.OrganizationProfileView
import pt.alinha.organization.api.PermanentContactView
import pt.alinha.organization.events.OrganizationRegistered
import pt.alinha.organization.internal.domain.OrganizationProfile
import pt.alinha.organization.internal.domain.OrganizationProfileInput
import pt.alinha.organization.internal.domain.OrganizationProfileValidator
import pt.alinha.organization.internal.domain.Sectors
import pt.alinha.organization.internal.domain.Validation
import pt.alinha.organization.internal.persistence.OrganizationProfileEntity
import pt.alinha.organization.internal.persistence.OrganizationProfileRepository
import java.time.Instant
import java.util.UUID

internal const val ENTITY_TYPE = "organization"
private const val NIF_CONSTRAINT = "organization_profile_nif_uq"

@Service
class OrganizationService(
    private val profiles: OrganizationProfileRepository,
    private val provisioning: TenantProvisioning,
    private val audit: AuditTrail,
    private val events: ApplicationEventPublisher,
) : OrganizationApi {

    /**
     * Creates the caller's organization and tenant (organization#2, ADR 0004). Not @Transactional: the core opens
     * the transaction. Who registered it is the actor of the `organization.registered` audit entry.
     */
    fun register(input: OrganizationProfileInput): OrganizationProfileView {
        val profile = validated(input)
        return provisioning.provisionForCurrentUser { tenantId ->
            val now = Instant.now()
            val entity = save(OrganizationProfileEntity(UUID.randomUUID(), now, profile))
            audit.record(
                "organization.registered", ENTITY_TYPE, entity.id.toString(),
                mapOf("sector" to profile.sector, "subsector" to profile.subsector),
            )
            events.publishEvent(OrganizationRegistered(tenantId, entity.id, now))
            entity.toView()
        }
    }

    @Transactional(readOnly = true)
    override fun currentProfile(): OrganizationProfileView? = profiles.findFirstBy()?.toView()

    /** Updates the current tenant's organization. [version] must match, so a stale form cannot overwrite changes. */
    @Transactional
    fun update(input: OrganizationProfileInput, version: Long?): OrganizationProfileView {
        val profile = validated(input)
        val entity = profiles.findFirstBy() ?: throw OrganizationNotRegisteredException()
        if (version == null || version != entity.version) throw StaleVersionException()
        val changed = entity.update(profile, Instant.now())
        if (changed.isEmpty()) return entity.toView()
        val saved = save(entity)
        audit.record("organization.updated", ENTITY_TYPE, saved.id.toString(), mapOf("changed" to changed))
        return saved.toView()
    }

    private fun validated(input: OrganizationProfileInput): OrganizationProfile =
        when (val result = OrganizationProfileValidator.validate(input)) {
            is Validation.Valid -> result.profile
            is Validation.Invalid -> throw InvalidOrganizationException(result.errors)
        }

    private fun save(entity: OrganizationProfileEntity): OrganizationProfileEntity =
        try {
            profiles.saveAndFlush(entity)
        } catch (e: DataIntegrityViolationException) {
            if (e.mostSpecificCause.message.orEmpty().contains(NIF_CONSTRAINT)) throw NifAlreadyRegisteredException()
            throw e
        }
}

internal fun OrganizationProfileEntity.toView() = OrganizationProfileView(
    id = id,
    version = version,
    name = name,
    nif = nif,
    cae = cae,
    sector = sector,
    subsector = subsector,
    sectorAnnex = Sectors.byId(sector)?.annex?.name,
    services = services,
    employees = employees,
    turnover = turnover,
    address = AddressView(street, postalCode, city),
    contacts = ContactsView(phone, email),
    permanentContact = PermanentContactView(permanentContactName, permanentContactPhone),
    registeredAt = registeredAt,
    updatedAt = updatedAt,
)
