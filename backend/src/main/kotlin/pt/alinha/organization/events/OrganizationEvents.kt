package pt.alinha.organization.events

import pt.alinha.pluginapi.DomainEvent
import pt.alinha.pluginapi.TenantId
import java.time.Instant
import java.util.UUID

/**
 * A new organization finished its registration (FR01). Consumers read its data through
 * `OrganizationApi.currentProfile()` inside `TenantContext.runAs(tenantId)`; the event carries ids only.
 */
data class OrganizationRegistered(
    override val tenantId: TenantId,
    val organizationId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
