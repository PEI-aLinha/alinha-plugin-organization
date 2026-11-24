package pt.alinha.organization.internal

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import pt.alinha.core.tenant.ConsultantAssignment
import pt.alinha.core.tenant.CorrelationContext
import pt.alinha.core.tenant.TenantContext
import pt.alinha.organization.events.ConsultantAssigned
import pt.alinha.organization.events.OrganizationRegistered

/**
 * Gives every new organization a consultant (FR04, organization#5). The choice (fewest organizations, then the
 * longest since their last assignment) is the core's: it compares all tenants, which a plugin cannot read.
 *
 * Runs after the registration commits, on its own thread, as the new tenant (the recipe in [OrganizationRegistered]).
 * A failure rolls the assignment back and leaves the event publication incomplete (docs/events.md); the core's
 * assignment is idempotent, so a later retry is safe. The audit entry (`tenant.consultant_assigned`) is the core's,
 * with `system` as actor.
 */
@Component
class ConsultantAssignmentListener(
    private val tenants: TenantContext,
    private val tx: TransactionTemplate,
    private val assignment: ConsultantAssignment,
    private val events: ApplicationEventPublisher,
    private val correlation: CorrelationContext,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener
    fun on(event: OrganizationRegistered) {
        try {
            tenants.runAs(event) {
                tx.executeWithoutResult {
                    val consultant = assignment.assignConsultant()
                    if (consultant != null && consultant.newlyAssigned) {
                        events.publishEvent(
                            ConsultantAssigned(event.tenantId, correlation.current(), event.organizationId, consultant.subject),
                        )
                    }
                }
            }
        } catch (e: RuntimeException) {
            log.error("Could not assign a consultant to organization {}", event.organizationId, e)
            throw e
        }
    }
}
