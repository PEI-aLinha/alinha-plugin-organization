package pt.alinha.organization.internal

import com.jayway.jsonpath.JsonPath
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
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import pt.alinha.pluginapi.TenantId
import java.util.UUID

private const val MEMBERS = "/api/organization/members"
private const val INVITATIONS = "/api/organization/invitations"
private const val ACCEPT = "/api/organization/invitations/accept"

/** The team endpoints on the core's real TenantMembership, RBAC and tenant resolution; only Keycloak is a fake. */
@SpringBootTest(classes = [OrganizationTestApplication::class])
@AutoConfigureMockMvc
class MembersIntegrationTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            OrganizationDatabase.register(registry)
            registry.add("alinha.provisioning.max-per-client-per-hour") { 1_000 }
        }
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var directory: FakeIdentityMembership
    @Autowired private lateinit var jdbc: JdbcTemplate

    private var tenant = UUID.randomUUID()
    private lateinit var officer: String

    @BeforeEach
    fun organization() {
        tenant = UUID.randomUUID()
        officer = UUID.randomUUID().toString()
        directory.add(officer, TenantId(tenant), "responsible")
    }

    private fun bearer(role: String, sub: String = UUID.randomUUID().toString()) =
        if (role == "registrant") "Bearer registrant.$sub" else "Bearer $role.$sub.$tenant"

    private fun invite(email: String, role: String = "responsible"): ResultActionsDsl = mvc.post(INVITATIONS) {
        header("Authorization", bearer(role, officer))
        if (role == "consultant") header("X-Alinha-Tenant", tenant)
        contentType = MediaType.APPLICATION_JSON
        content = """{"email": "$email"}"""
    }

    private fun accept(token: String, sub: String): ResultActionsDsl = mvc.post(ACCEPT) {
        header("Authorization", bearer("registrant", sub))
        contentType = MediaType.APPLICATION_JSON
        content = """{"token": "$token"}"""
    }

    private fun issued(email: String): Pair<String, String> {
        val body = invite(email).andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return JsonPath.read<String>(body, "$.id") to JsonPath.read(body, "$.token")
    }

    @Test
    fun `the Designated Officer invites an analyst, who accepts and appears in the team`() {
        val sub = UUID.randomUUID().toString()
        val (id, token) = issued("$sub@empresa.pt")

        mvc.get(INVITATIONS) { header("Authorization", bearer("responsible", officer)) }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(id) }
            jsonPath("$[0].expired") { value(false) }
            jsonPath("$[0].token") { doesNotExist() }
            jsonPath("$[0].createdAt") { exists() }
        }
        accept(token, sub).andExpect { status { isNoContent() } }

        mvc.get(MEMBERS) { header("Authorization", bearer("responsible", officer)) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[1].id") { value(sub) }
            jsonPath("$[1].role") { value("analyst") }
        }
        mvc.get(INVITATIONS) { header("Authorization", bearer("responsible", officer)) }.andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `analysts and consultants cannot see or manage the team`() {
        mvc.get(MEMBERS) { header("Authorization", bearer("analyst")) }.andExpect { status { isForbidden() } }
        invite("ana@empresa.pt", role = "analyst").andExpect { status { isForbidden() } }
        invite("ana@empresa.pt", role = "consultant").andExpect { status { isForbidden() } }
        assertThat(jdbc.queryForObject("select count(*) from core.invitation where tenant_id = ?", Long::class.java, tenant)).isZero()
    }

    @Test
    fun `only a new account can accept an invitation`() {
        val (_, token) = issued("ana@empresa.pt")

        mvc.post(ACCEPT) {
            header("Authorization", bearer("analyst"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"token": "$token"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `an invalid email is a field error in Portuguese`() {
        invite("nao-e-um-email").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("INVALID_EMAIL") }
            jsonPath("$.errors[0].field") { value("email") }
            jsonPath("$.errors[0].message") { value("Este email não parece válido. Exemplo: nome@empresa.pt") }
        }
    }

    @Test
    fun `inviting someone already in the team is refused`() {
        invite("$officer@empresa.pt").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ALREADY_MEMBER") }
        }
    }

    @Test
    fun `an invitation for another email says so in Portuguese`() {
        val (_, token) = issued("ana@empresa.pt")

        accept(token, UUID.randomUUID().toString()).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("INVITATION_FOR_ANOTHER_EMAIL") }
            jsonPath("$.detail") { value("Este convite foi enviado para outro email. Entre com a conta desse email para o aceitar.") }
        }
    }

    @Test
    fun `an unknown invitation is not found`() {
        accept("nao-existe", UUID.randomUUID().toString()).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("INVITATION_NOT_FOUND") }
        }
    }

    @Test
    fun `the Designated Officer revokes an invitation and removes an analyst`() {
        val (id, _) = issued("ana@empresa.pt")
        val analyst = UUID.randomUUID().toString()
        directory.add(analyst, TenantId(tenant), "analyst")

        mvc.delete("$INVITATIONS/$id") { header("Authorization", bearer("responsible", officer)) }
            .andExpect { status { isNoContent() } }
        mvc.delete("$MEMBERS/$analyst") { header("Authorization", bearer("responsible", officer)) }
            .andExpect { status { isNoContent() } }

        mvc.get(MEMBERS) { header("Authorization", bearer("responsible", officer)) }.andExpect { jsonPath("$.length()") { value(1) } }
        mvc.delete("$MEMBERS/$officer") { header("Authorization", bearer("responsible", officer)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("MEMBER_NOT_FOUND") }
        }
    }

    @Test
    fun `an analyst of another organization cannot be removed from here`() {
        val elsewhere = UUID.randomUUID().toString()
        directory.add(elsewhere, TenantId(UUID.randomUUID()), "analyst")

        mvc.delete("$MEMBERS/$elsewhere") { header("Authorization", bearer("responsible", officer)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("MEMBER_NOT_FOUND") }
        }
        assertThat(directory.accounts).containsKey(elsewhere)
    }

    @Test
    fun `a consultant or an analyst cannot remove anyone`() {
        val analyst = UUID.randomUUID().toString()
        directory.add(analyst, TenantId(tenant), "analyst")

        mvc.delete("$MEMBERS/$analyst") {
            header("Authorization", bearer("consultant"))
            header("X-Alinha-Tenant", tenant)
        }.andExpect { status { isForbidden() } }
        mvc.delete("$MEMBERS/$analyst") { header("Authorization", bearer("analyst")) }.andExpect { status { isForbidden() } }
        assertThat(directory.accounts).containsKey(analyst)
    }

    @Test
    fun `another organization's invitation cannot be revoked`() {
        val (id, _) = issued("ana@empresa.pt")

        mvc.delete("$INVITATIONS/$id") { header("Authorization", "Bearer responsible.${UUID.randomUUID()}.${UUID.randomUUID()}") }
            .andExpect { status { isNotFound() } }
    }
}
