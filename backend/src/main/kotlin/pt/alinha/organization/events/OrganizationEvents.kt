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
 * `@Async @TransactionalEventListener` with no `@Transactional`, and open the transaction inside `runAs`. Pass the
 * event itself, so the work also carries its [correlationId] (docs/events.md in the main repo):
 * ```
 * @Async @TransactionalEventListener
 * fun on(event: OrganizationRegistered) = tenants.runAs(event) { tx.execute { ... } }
 * ```
 */
data class OrganizationRegistered(
    override val tenantId: TenantId,
    override val correlationId: UUID,
    val organizationId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent

/**
 * The new organization was given a consultant (FR04, organization#5), who can open it once their token is refreshed
 * (ADR 0002). Published once per organization, only when the assignment was made by the platform. [consultantId]
 * is the consultant's token `sub`: a consumer that needs a name or an email asks the core, never the event.
 */
data class ConsultantAssigned(
    override val tenantId: TenantId,
    override val correlationId: UUID,
    val organizationId: UUID,
    val consultantId: String,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
