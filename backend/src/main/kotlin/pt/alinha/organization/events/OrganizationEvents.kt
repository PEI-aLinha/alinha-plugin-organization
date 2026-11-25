package pt.alinha.organization.events

import pt.alinha.pluginapi.DomainEvent
import pt.alinha.pluginapi.TenantId
import java.time.Instant
import java.time.LocalDate
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
 * A task was given to a member, or reassigned or reopened (organization#7): for the assignment e-mail
 * (notifications#3). Carries the assignee's `sub` only; the work is named by [type] and [reference].
 */
data class TaskAssigned(
    override val tenantId: TenantId,
    override val correlationId: UUID,
    val taskId: UUID,
    val type: String,
    val reference: String,
    val assigneeId: String,
    val dueDate: LocalDate?,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent

/** The work behind a task was done (organization#7). */
data class TaskCompleted(
    override val tenantId: TenantId,
    override val correlationId: UUID,
    val taskId: UUID,
    val type: String,
    val reference: String,
    val assigneeId: String,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
