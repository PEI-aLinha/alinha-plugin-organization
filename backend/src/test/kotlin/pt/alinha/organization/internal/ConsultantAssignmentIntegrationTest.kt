package pt.alinha.organization.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import pt.alinha.core.tenant.AssignedConsultant
import java.util.UUID

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

    @BeforeEach
    fun reset() {
        identity.assigned.clear()
        assignment.calls.clear()
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

    @Test
    fun `a new organization asks the core for a consultant and publishes ConsultantAssigned`() {
        val (tenant, correlationId) = register()

        eventually { published.events.isNotEmpty() }

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
        register()
        assignment.answer = { null }
        register()

        eventually { assignment.calls.size == 2 }
        Thread.sleep(200) // a publication would follow the call on the same thread

        assertThat(published.events).isEmpty()
    }

    @Test
    fun `a failed assignment does not undo the registration`() {
        assignment.answer = { error("identity provider down") }

        val (tenant, _) = register()
        eventually { assignment.calls.isNotEmpty() }
        Thread.sleep(200)

        assertThat(published.events).isEmpty()
        assertThat(
            jdbc.queryForObject("select count(*) from organization.organization_profile where tenant_id = ?", Long::class.java, tenant),
        ).isEqualTo(1)
    }
}
