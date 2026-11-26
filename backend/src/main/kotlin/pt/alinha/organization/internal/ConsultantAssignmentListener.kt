package pt.alinha.organization.internal

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.retry.RetryPolicy
import org.springframework.core.retry.RetryTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate
import pt.alinha.core.tenant.ConsultantAssignment
import pt.alinha.core.tenant.CorrelationContext
import pt.alinha.core.tenant.TenantContext
import pt.alinha.organization.events.ConsultantAssigned
import pt.alinha.organization.events.OrganizationRegistered
import java.time.Duration

/**
 * Gives every new organization a consultant (FR04, organization#5). The choice (fewest organizations, then the
 * longest since their last assignment) is the core's: it compares all tenants, which a plugin cannot read.
 *
 * Runs after the registration commits, on its own thread, as the new tenant (the recipe in [OrganizationRegistered]).
 * A failed attempt rolls its transaction back and is retried [RETRIES] times, waiting [retryDelay], then three
 * times as long, and so on: a short identity provider outage must not leave the organization without a consultant.
 * The core's assignment is idempotent, so each retry is safe. When every attempt fails, the error is logged with the
 * organization id and rethrown, which leaves the event publication incomplete in `public.event_publication`
 * (docs/events.md). Nothing resubmits it automatically yet; it can be resubmitted from there once the cause is fixed.
 * The audit entry (`tenant.consultant_assigned`) is the core's, with `system` as actor.
 */
@Component
class ConsultantAssignmentListener(
    private val tenants: TenantContext,
    private val tx: TransactionTemplate,
    private val assignment: ConsultantAssignment,
    private val events: ApplicationEventPublisher,
    private val correlation: CorrelationContext,
    @Value("\${alinha.organization.consultant-assignment.retry-delay:1s}") retryDelay: Duration,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val retry = RetryTemplate(
        RetryPolicy.builder().maxRetries(RETRIES).delay(retryDelay).multiplier(BACKOFF_MULTIPLIER).build(),
    )

    @Async
    @TransactionalEventListener
    fun on(event: OrganizationRegistered) {
        try {
            tenants.runAs(event) {
                retry.invoke(Runnable { assignOnce(event) })
            }
        } catch (e: RuntimeException) {
            log.error("Could not assign a consultant to organization {} after {} attempts", event.organizationId, RETRIES + 1, e)
            throw e
        }
    }

    /** One attempt, in its own transaction, so a failed one leaves nothing behind. */
    private fun assignOnce(event: OrganizationRegistered) {
        tx.executeWithoutResult {
            val consultant = assignment.assignConsultant()
            if (consultant != null && consultant.newlyAssigned) {
                events.publishEvent(
                    ConsultantAssigned(event.tenantId, correlation.current(), event.organizationId, consultant.subject),
                )
            }
        }
    }

    private companion object {
        const val RETRIES = 3L
        const val BACKOFF_MULTIPLIER = 3.0
    }
}
