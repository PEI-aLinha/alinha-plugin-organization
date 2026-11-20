package pt.alinha.organization.internal

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import pt.alinha.core.tenant.TenantContext
import pt.alinha.organization.events.OrganizationRegistered
import pt.alinha.organization.internal.domain.OrganizationProfileInput
import pt.alinha.organization.internal.persistence.OrganizationProfileRepository
import pt.alinha.pluginapi.TenantId
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

private const val REGISTRATION = "/api/organization/registration"
private const val PROFILE = "/api/organization/profile"

private fun body(nif: String = "509180027", name: String = "Componentes Águeda, Lda.", version: Long? = null) = """
    {${version?.let { "\"version\": $it," } ?: ""}
     "name": "$name", "nif": "$nif", "cae": "29320", "sector": "industria", "subsector": "automoveis",
     "services": "Fabrico de componentes para a indústria automóvel.", "employees": 120, "turnover": 18500000,
     "address": {"street": "Rua da Zona Industrial, 45", "postalCode": "3750-750", "city": "Águeda"},
     "contacts": {"phone": "234600120", "email": "geral@compagueda.pt"},
     "permanentContact": {"name": "Rui Santos", "phone": "912345678"}}
""".trimIndent()

@SpringBootTest(classes = [OrganizationTestApplication::class])
@AutoConfigureMockMvc
@RecordApplicationEvents
class OrganizationIntegrationTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            OrganizationDatabase.register(registry)
            // MockMvc requests all come from 127.0.0.1; the per-client limit is tested in the core.
            registry.add("alinha.provisioning.max-per-client-per-hour") { 1_000 }
        }
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var identity: FakeIdentityDirectory
    @Autowired private lateinit var events: ApplicationEvents
    @Autowired private lateinit var service: OrganizationService
    @Autowired private lateinit var profiles: OrganizationProfileRepository
    @Autowired private lateinit var tenants: TenantContext
    @Autowired private lateinit var tx: TransactionTemplate
    @Autowired private lateinit var json: JsonMapper

    private lateinit var sub: String

    @BeforeEach
    fun newAccount() {
        sub = UUID.randomUUID().toString()
        identity.assigned.clear()
        identity.failOnAssign = false
    }

    /** A NIF with a valid check digit that no other test uses (the NIF is unique across tenants). */
    private fun freshNif(): String {
        val base = (10_000_000 + (Math.random() * 89_999_999).toInt()).toString()
        val sum = base.mapIndexed { i, c -> c.digitToInt() * (9 - i) }.sum()
        val rest = sum % 11
        return base + (if (rest < 2) 0 else 11 - rest)
    }

    private fun register(
        token: String = "registrant.$sub",
        nif: String = freshNif(),
        correlationId: UUID = UUID.randomUUID(),
    ): ResultActionsDsl =
        mvc.post(REGISTRATION) {
            header("Authorization", "Bearer $token")
            header("X-Correlation-Id", correlationId.toString())
            contentType = MediaType.APPLICATION_JSON
            content = body(nif)
        }

    /** Registers and returns the new tenant, as the refreshed token would carry it. */
    private fun registered(nif: String = freshNif()): String {
        register(nif = nif).andExpect { status { isCreated() } }
        return identity.assigned.single().second.value.toString()
    }

    @Test
    fun `a registrant registers the organization and gets its own tenant`() {
        val correlationId = UUID.randomUUID()
        register(correlationId = correlationId).andExpect {
            status { isCreated() }
            jsonPath("$.name") { value("Componentes Águeda, Lda.") }
            jsonPath("$.sectorAnnex") { value("II") }
            jsonPath("$.version") { value(0) }
        }

        val (subject, tenant) = identity.assigned.single()
        assertThat(subject).isEqualTo(sub)
        val row = jdbc.queryForMap("select id, tenant_id from organization.organization_profile where tenant_id = ?", tenant.value)
        val audit = jdbc.queryForList(
            "select action, actor from audit.audit_event where tenant_id = ? order by occurred_at", tenant.value,
        ).map { it["action"] to it["actor"] }
        assertThat(audit).containsExactly("organization.registered" to sub, "tenant.provisioned" to sub)
        assertThat(events.stream(OrganizationRegistered::class.java))
            .singleElement()
            .satisfies({
                assertThat(it.tenantId).isEqualTo(tenant)
                assertThat(it.correlationId).isEqualTo(correlationId)
                assertThat(it.organizationId).isEqualTo(row["id"])
            })
    }

    @Test
    fun `invalid data is a 422 with one message per field and creates nothing`() {
        mvc.post(REGISTRATION) {
            header("Authorization", "Bearer registrant.$sub")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "", "nif": "509180028"}"""
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[?(@.field == 'nif')].message") { value("Este NIF não é válido. Confirme se algum número está trocado.") }
        }
        assertThat(identity.assigned).isEmpty()
        assertThat(jdbc.queryForObject("select count(*) from core.tenant where owner_subject = ?", Long::class.java, sub)).isZero()
    }

    @ParameterizedTest(name = "{0} is a 422 on {1} and creates nothing")
    @CsvSource(
        delimiter = '|',
        value = [
            "\"employees\": \"abc\"   | employees",
            "\"turnover\": 1e30         | turnover",
            "\"address\": \"Águeda\"  | address",
        ],
    )
    fun `a value of the wrong type is invalid data`(field: String, name: String) {
        mvc.post(REGISTRATION) {
            header("Authorization", "Bearer registrant.$sub")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Componentes Águeda, Lda.", $field}"""
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value(name) }
            jsonPath("$.errors[0].message") { value("Este valor não é válido.") }
        }
        assertThat(identity.assigned).isEmpty()
    }

    @ParameterizedTest(name = "body [{0}] is a 422")
    @CsvSource("''", "'{\"name\": '", "'[]'")
    fun `a body that cannot be read is invalid data`(content: String) {
        mvc.post(REGISTRATION) {
            header("Authorization", "Bearer registrant.$sub")
            contentType = MediaType.APPLICATION_JSON
            this.content = content
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors") { isEmpty() }
        }
        assertThat(identity.assigned).isEmpty()
    }

    @Test
    fun `an update with a value of the wrong type is invalid data`() {
        val tenant = registered()

        mvc.put(PROFILE) {
            header("Authorization", "Bearer responsible.$sub.$tenant")
            contentType = MediaType.APPLICATION_JSON
            content = """{"version": 0, "address": {"postalCode": 3750}, "employees": "muitos"}"""
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("employees") }
        }
    }

    @Test
    fun `a second registration by the same account is a 409`() {
        registered()

        register().andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ORGANIZATION_ALREADY_EXISTS") }
            jsonPath("$.detail") { value("Esta conta já tem uma organização registada. Entre de novo para continuar.") }
        }
    }

    @Test
    fun `a NIF registered by another organization is a 409 and rolls back the new tenant`() {
        val nif = freshNif()
        registered(nif)
        val other = UUID.randomUUID().toString()

        register(token = "registrant.$other", nif = nif).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("NIF_ALREADY_REGISTERED") }
        }
        assertThat(jdbc.queryForObject("select count(*) from core.tenant where owner_subject = ?", Long::class.java, other)).isZero()
    }

    @Test
    fun `an identity provider failure is a 503 with Retry-After and a Portuguese message, and creates nothing`() {
        identity.failOnAssign = true

        register().andExpect {
            status { isServiceUnavailable() }
            header { string("Retry-After", "60") }
            jsonPath("$.code") { value("IDENTITY_UNAVAILABLE") }
            jsonPath("$.detail") { value("Não foi possível concluir o registo agora. Tente novamente dentro de alguns minutos.") }
        }
        assertThat(jdbc.queryForObject("select count(*) from core.tenant where owner_subject = ?", Long::class.java, sub)).isZero()
    }

    @ParameterizedTest(name = "{0} cannot register an organization")
    @CsvSource("responsible", "analyst", "consultant")
    fun `only a registrant may register`(role: String) {
        register(token = "$role.$sub.${UUID.randomUUID()}").andExpect { status { isForbidden() } }
    }

    @ParameterizedTest(name = "{0} reads the profile: {1}")
    @CsvSource("responsible,200", "analyst,200", "consultant,200", "registrant,403")
    fun `who reads the profile`(role: String, status: Int) {
        val tenant = registered()

        mvc.get(PROFILE) {
            header("Authorization", "Bearer $role.${UUID.randomUUID()}.$tenant")
            if (role == "consultant") header("X-Alinha-Tenant", tenant)
        }.andExpect { status { isEqualTo(status) } }
    }

    @ParameterizedTest(name = "{0} edits the profile: {1}")
    @CsvSource("responsible,200", "consultant,200", "analyst,403", "registrant,403")
    fun `who edits the profile`(role: String, status: Int) {
        val tenant = registered()

        mvc.put(PROFILE) {
            header("Authorization", "Bearer $role.${UUID.randomUUID()}.$tenant")
            if (role == "consultant") header("X-Alinha-Tenant", tenant)
            contentType = MediaType.APPLICATION_JSON
            content = body(nif = currentNif(tenant), name = "Componentes Águeda, S.A.", version = 0)
        }.andExpect { status { isEqualTo(status) } }
    }

    private fun currentNif(tenant: String): String =
        jdbc.queryForObject("select nif from organization.organization_profile where tenant_id = ?::uuid", String::class.java, tenant)!!

    @Test
    fun `an update records only the names of the changed fields`() {
        val tenant = registered()

        mvc.put(PROFILE) {
            header("Authorization", "Bearer responsible.$sub.$tenant")
            contentType = MediaType.APPLICATION_JSON
            content = body(nif = currentNif(tenant), name = "Componentes Águeda, S.A.", version = 0)
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Componentes Águeda, S.A.") }
            jsonPath("$.version") { value(1) }
        }

        val details = jdbc.queryForObject(
            "select details::text from audit.audit_event where tenant_id = ?::uuid and action = 'organization.updated'",
            String::class.java, tenant,
        )
        assertThat(details).contains("\"changed\"").contains("\"name\"").doesNotContain("S.A.")
    }

    @Test
    fun `a stale version is a 409`() {
        val tenant = registered()

        mvc.put(PROFILE) {
            header("Authorization", "Bearer responsible.$sub.$tenant")
            contentType = MediaType.APPLICATION_JSON
            content = body(nif = currentNif(tenant), name = "Outro nome, Lda.", version = 7)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("VERSION_CONFLICT") }
        }
    }

    @Test
    fun `another organization never sees this profile`() {
        registered()

        mvc.get(PROFILE) { header("Authorization", "Bearer responsible.${UUID.randomUUID()}.${UUID.randomUUID()}") }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("ORGANIZATION_NOT_REGISTERED") }
            }
    }

    @Test
    fun `with two organizations registered, each reads and edits only its own profile`() {
        val nifA = freshNif()
        val tenantA = registered(nifA)
        val subA = sub
        sub = UUID.randomUUID().toString()
        identity.assigned.clear()
        val nifB = freshNif()
        val tenantB = registered(nifB)

        mvc.get(PROFILE) { header("Authorization", "Bearer responsible.$sub.$tenantB") }
            .andExpect {
                status { isOk() }
                jsonPath("$.nif") { value(nifB) }
            }
        mvc.put(PROFILE) {
            header("Authorization", "Bearer responsible.$sub.$tenantB")
            contentType = MediaType.APPLICATION_JSON
            content = body(nif = nifB, name = "Organização B, Lda.", version = 0)
        }.andExpect { status { isOk() } }

        mvc.get(PROFILE) { header("Authorization", "Bearer responsible.$subA.$tenantA") }
            .andExpect {
                status { isOk() }
                jsonPath("$.nif") { value(nifA) }
                jsonPath("$.name") { value("Componentes Águeda, Lda.") }
                jsonPath("$.version") { value(0) }
            }
    }

    @Test
    fun `before registering there is no profile to read or edit`() {
        val tenant = UUID.randomUUID()
        mvc.get(PROFILE) { header("Authorization", "Bearer responsible.$sub.$tenant") }
            .andExpect { status { isNotFound() } }
        mvc.put(PROFILE) {
            header("Authorization", "Bearer responsible.$sub.$tenant")
            contentType = MediaType.APPLICATION_JSON
            content = body(version = 0)
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `before registering, invalid data is still a 404, not a 422`() {
        mvc.put(PROFILE) {
            header("Authorization", "Bearer responsible.$sub.${UUID.randomUUID()}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "", "nif": "509180028"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORGANIZATION_NOT_REGISTERED") }
        }
    }

    @Test
    fun `a missing version is invalid data, not a conflict`() {
        val tenant = registered()

        mvc.put(PROFILE) {
            header("Authorization", "Bearer responsible.$sub.$tenant")
            contentType = MediaType.APPLICATION_JSON
            content = body(nif = currentNif(tenant))
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("version") }
        }
    }

    @Test
    fun `the NIF cannot change after registration`() {
        val tenant = registered()
        val original = currentNif(tenant)

        mvc.put(PROFILE) {
            header("Authorization", "Bearer responsible.$sub.$tenant")
            contentType = MediaType.APPLICATION_JSON
            content = body(nif = freshNif(), version = 0)
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("nif") }
            jsonPath("$.errors[0].message") { value("O NIF não pode ser alterado depois do registo.") }
        }
        assertThat(currentNif(tenant)).isEqualTo(original)
    }

    @Test
    fun `saving the same data changes nothing and records nothing`() {
        val tenant = registered()

        mvc.put(PROFILE) {
            header("Authorization", "Bearer responsible.$sub.$tenant")
            contentType = MediaType.APPLICATION_JSON
            content = body(nif = currentNif(tenant), version = 0)
        }.andExpect {
            status { isOk() }
            jsonPath("$.version") { value(0) }
        }
        assertThat(
            jdbc.queryForObject(
                "select count(*) from audit.audit_event where tenant_id = ?::uuid and action = 'organization.updated'",
                Long::class.java, tenant,
            ),
        ).isZero()
    }

    @Test
    fun `an update that loses a race with another update is a version conflict`() {
        val tenant = registered()
        val input = json.readValue(body(nif = currentNif(tenant), name = "Outro nome, Lda."), OrganizationProfileInput::class.java)

        // Both requests read version 0. The other one commits first; this one passed the version check already, so
        // only the @Version check on the UPDATE can stop it (the API maps this exception to 409 VERSION_CONFLICT).
        // The test's mock request is bound to this thread; runAs refuses to run inside a request.
        val request = RequestContextHolder.getRequestAttributes()
        RequestContextHolder.resetRequestAttributes()
        try {
            assertThatThrownBy {
                tenants.runAs(TenantId(UUID.fromString(tenant))) {
                    tx.execute {
                        profiles.findFirstBy() // the service reads the entity from this session, still at version 0
                        jdbc.update(
                            "update organization.organization_profile set version = version + 1 where tenant_id = ?::uuid",
                            tenant,
                        )
                        service.update(input, 0)
                    }
                }
            }.isInstanceOf(ObjectOptimisticLockingFailureException::class.java)
        } finally {
            RequestContextHolder.setRequestAttributes(request)
        }
        assertThat(
            jdbc.queryForObject("select name from organization.organization_profile where tenant_id = ?::uuid", String::class.java, tenant),
        ).isEqualTo("Componentes Águeda, Lda.")
    }
}
