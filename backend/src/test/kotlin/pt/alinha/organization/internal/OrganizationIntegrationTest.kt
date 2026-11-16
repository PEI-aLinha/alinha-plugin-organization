package pt.alinha.organization.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import pt.alinha.organization.events.OrganizationRegistered
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
        fun properties(registry: DynamicPropertyRegistry) = OrganizationDatabase.register(registry)
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var identity: FakeIdentityDirectory
    @Autowired private lateinit var events: ApplicationEvents

    private lateinit var sub: String

    @BeforeEach
    fun newAccount() {
        sub = UUID.randomUUID().toString()
        identity.assigned.clear()
    }

    /** A NIF with a valid check digit that no other test uses (the NIF is unique across tenants). */
    private fun freshNif(): String {
        val base = (10_000_000 + (Math.random() * 89_999_999).toInt()).toString()
        val sum = base.mapIndexed { i, c -> c.digitToInt() * (9 - i) }.sum()
        val rest = sum % 11
        return base + (if (rest < 2) 0 else 11 - rest)
    }

    private fun register(token: String = "registrant.$sub", nif: String = freshNif()): ResultActionsDsl =
        mvc.post(REGISTRATION) {
            header("Authorization", "Bearer $token")
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
        register().andExpect {
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
            .satisfies({ assertThat(it.tenantId).isEqualTo(tenant); assertThat(it.organizationId).isEqualTo(row["id"]) })
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
}
