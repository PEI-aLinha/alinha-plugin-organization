package pt.alinha.organization.events

import pt.alinha.pluginapi.DomainEvent
import pt.alinha.pluginapi.TenantId
import java.time.Instant
import java.util.UUID

/**
 * A new organization finished its registration (FR01). The event carries ids only; consumers read the data through
 * `OrganizationApi.currentProfile()` inside `TenantContext.runAs(tenantId)`.
 *
 * `runAs` must start outside a transaction, so do not use `@ApplicationModuleListener` (it opens one first). Use
 * `@Async @TransactionalEventListener` with no `@Transactional`, and open the transaction inside `runAs`:
 * ```
 * @Async @TransactionalEventListener
 * fun on(event: OrganizationRegistered) = tenants.runAs(event.tenantId) { tx.execute { ... } }
 * ```
 */
data class OrganizationRegistered(
    override val tenantId: TenantId,
    val organizationId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
