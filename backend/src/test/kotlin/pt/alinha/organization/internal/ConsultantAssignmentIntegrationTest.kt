package pt.alinha.organization.internal

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionTemplate
import pt.alinha.core.tenant.AssignedConsultant
import pt.alinha.core.tenant.CorrelationContext
import pt.alinha.core.tenant.TenantContext
import pt.alinha.organization.events.OrganizationRegistered
import pt.alinha.pluginapi.TenantId
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * organization#5: after a registration commits, the listener asks the core for a consultant as the new tenant and
 * publishes ConsultantAssigned. The choice itself is the core's (ConsultantAssignerIntegrationTest).
 */
@SpringBootTest(classes = [OrganizationTestApplication::class])
@AutoConfigureMockMvc
class ConsultantAssignmentIntegrationTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            OrganizationDatabase.register(registry)
            registry.add("alinha.provisioning.max-per-client-per-hour") { 1_000 }
        }
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var identity: FakeIdentityDirectory
    @Autowired private lateinit var assignment: FakeConsultantAssignment
    @Autowired private lateinit var published: ConsultantAssignedCollector
    @Autowired private lateinit var asyncTasks: FinishedAsyncTasks
    @Autowired private lateinit var tenants: TenantContext
    @Autowired private lateinit var tx: TransactionTemplate
    @Autowired private lateinit var publisher: ApplicationEventPublisher
    @Autowired private lateinit var correlation: CorrelationContext

    /** Async tasks finished before this test's registrations (each test waits for its own). */
    private var finishedBefore = 0

    @BeforeEach
    fun reset() {
        finishedBefore = asyncTasks.count()
        identity.assigned.clear()
        assignment.calls.clear()
        assignment.transactionOutcomes.clear()
        assignment.answer = { AssignedConsultant("consultant-sub", newlyAssigned = true) }
        published.events.clear()
    }

    private fun freshNif(): String {
        val base = (10_000_000 + (Math.random() * 89_999_999).toInt()).toString()
        val sum = base.mapIndexed { i, c -> c.digitToInt() * (9 - i) }.sum()
        val rest = sum % 11
        return base + (if (rest < 2) 0 else 11 - rest)
    }

    /** Registers a new organization and returns its tenant id and the registration's correlation id. */
    private fun register(): Pair<UUID, UUID> {
        val correlationId = UUID.randomUUID()
        mvc.post("/api/organization/registration") {
            header("Authorization", "Bearer registrant.${UUID.randomUUID()}")
            header("X-Correlation-Id", correlationId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"name": "Componentes Águeda, Lda.", "nif": "${freshNif()}", "cae": "29320", "sector": "industria",
                 "subsector": "automoveis", "services": "Fabrico de componentes para a indústria automóvel.",
                 "employees": 120, "turnover": 18500000,
                 "address": {"street": "Rua da Zona Industrial, 45", "postalCode": "3750-750", "city": "Águeda"},
                 "contacts": {"phone": "234600120", "email": "geral@compagueda.pt"},
                 "permanentContact": {"name": "Rui Santos", "phone": "912345678"}}
            """.trimIndent()
        }.andExpect { status { isCreated() } }
        return identity.assigned.last().second.value to correlationId
    }

    /** The listener runs on another thread after the commit. */
    private fun eventually(check: () -> Boolean) {
        val until = System.nanoTime() + 10_000_000_000
        while (!check()) {
            check(System.nanoTime() < until) { "timed out" }
            Thread.sleep(20)
        }
    }

    /** Waits until the listener has run for [registrations] organizations, publish step included. */
    private fun listenerFinished(registrations: Int) = eventually { asyncTasks.count() - finishedBefore >= registrations }

    @Test
    fun `a new organization asks the core for a consultant and publishes ConsultantAssigned`() {
        val (tenant, correlationId) = register()

        listenerFinished(1)

        val call = assignment.calls.single()
        assertThat(call.tenant.value).isEqualTo(tenant)
        assertThat(call.inTransaction).isTrue()
        val organizationId = jdbc.queryForObject(
            "select id from organization.organization_profile where tenant_id = ?", UUID::class.java, tenant,
        )
        val event = published.events.single()
        assertThat(event.tenantId.value).isEqualTo(tenant)
        assertThat(event.organizationId).isEqualTo(organizationId)
        assertThat(event.consultantId).isEqualTo("consultant-sub")
        assertThat(event.correlationId).isEqualTo(correlationId) // same chain as the registration
    }

    @Test
    fun `nothing is published when the organization already had a consultant or none exists`() {
        assignment.answer = { AssignedConsultant("consultant-sub", newlyAssigned = false) }
        val (first, _) = register()
        listenerFinished(1) // the first listener must read its answer before the next one is set
        assignment.answer = { null }
        val (second, _) = register()
        listenerFinished(2)

        // Each registration asks under its own tenant.
        assertThat(assignment.calls.map { it.tenant.value }).containsExactly(first, second)
        assertThat(published.events).isEmpty()
    }

    @Test
    fun `a failed assignment rolls back and does not undo the registration`() {
        assignment.answer = { error("identity provider down") }

        val (tenant, _) = register()
        listenerFinished(1)

        assertThat(assignment.transactionOutcomes).containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK)
        assertThat(published.events).isEmpty()
        assertThat(
            jdbc.queryForObject("select count(*) from organization.organization_profile where tenant_id = ?", Long::class.java, tenant),
        ).isEqualTo(1)
    }

    @Test
    fun `the listener rethrows a failed assignment, so its event publication stays incomplete`() {
        val (tenant, _) = register()
        listenerFinished(1)
        val organizationId = jdbc.queryForObject(
            "select id from organization.organization_profile where tenant_id = ?", UUID::class.java, tenant,
        )!!
        assignment.answer = { error("identity provider down") }

        // Called without the @Async proxy, to see what the event publication registry would see. On another thread:
        // MockMvc leaves its request bound to this one, and runAs refuses to run inside a request.
        val listener = ConsultantAssignmentListener(tenants, tx, assignment, publisher, correlation)
        val run = CompletableFuture.runAsync {
            listener.on(OrganizationRegistered(TenantId(tenant), UUID.randomUUID(), organizationId))
        }
        assertThatThrownBy { run.join() }.hasRootCauseMessage("identity provider down")
    }
}
