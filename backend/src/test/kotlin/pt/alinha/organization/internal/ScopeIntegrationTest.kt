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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.support.TransactionTemplate
import pt.alinha.core.compliance.ApplicabilityRules
import pt.alinha.core.compliance.Nis2EntityType
import pt.alinha.core.tenant.TenantContext
import pt.alinha.organization.internal.domain.ApplicabilityAnswersInput
import pt.alinha.organization.internal.domain.CncsQualificationInput
import pt.alinha.organization.internal.domain.GdprAnswersInput
import pt.alinha.organization.internal.domain.Nis2AnswersInput
import pt.alinha.pluginapi.TenantId
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

private const val REGISTRATION = "/api/organization/registration"
private const val PROFILE = "/api/organization/profile"
private const val SCOPE = "/api/organization/scope"
private const val ANSWERS = "/api/organization/scope/answers"

private fun profileBody(nif: String, employees: Int = 120, turnover: Long = 18_500_000, version: Long? = null) = """
    {${version?.let { "\"version\": $it," } ?: ""}
     "name": "Componentes Águeda, Lda.", "nif": "$nif", "cae": "29320", "sector": "industria", "subsector": "automoveis",
     "services": "Fabrico de componentes para a indústria automóvel.", "employees": $employees, "turnover": $turnover,
     "address": {"street": "Rua da Zona Industrial, 45", "postalCode": "3750-750", "city": "Águeda"},
     "contacts": {"phone": "234600120", "email": "geral@compagueda.pt"},
     "permanentContact": {"name": "Rui Santos", "phone": "912345678"}}
""".trimIndent()

private fun answersBody(
    entityTypes: List<String> = listOf("MOTOR_VEHICLE_MANUFACTURING"),
    version: Long? = null,
    gdpr: String = """{"processesPersonalData": true, "regularOrRiskyProcessing": true}""",
) = """
    {${version?.let { "\"version\": $it," } ?: ""}
     "publicBody": false,
     "nis2": {"entityTypes": [${entityTypes.joinToString { "\"$it\"" }}], "criticalEntity": false,
              "significantImpact": false, "higherEducation": false},
     "gdpr": $gdpr,
     "aiAct": {"usesAiSystems": true}}
""".trimIndent()

@SpringBootTest(classes = [OrganizationTestApplication::class])
@AutoConfigureMockMvc
class ScopeIntegrationTest {
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
    @Autowired private lateinit var json: JsonMapper
    @Autowired private lateinit var service: ScopeService
    @Autowired private lateinit var tenants: TenantContext
    @Autowired private lateinit var tx: TransactionTemplate
    @Autowired private lateinit var dataSource: DataSource

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

    /** Registers an organization (120 employees, 18.5 M€: a medium company) and returns its tenant. */
    private fun registered(nif: String = freshNif()): String {
        sub = UUID.randomUUID().toString()
        identity.assigned.clear()
        mvc.post(REGISTRATION) {
            header("Authorization", "Bearer registrant.$sub")
            contentType = MediaType.APPLICATION_JSON
            content = profileBody(nif)
        }.andExpect { status { isCreated() } }
        return identity.assigned.single().second.value.toString()
    }

    private fun currentNif(tenant: String): String =
        jdbc.queryForObject("select nif from organization.organization_profile where tenant_id = ?::uuid", String::class.java, tenant)!!

    private fun changeSize(tenant: String, employees: Int, turnover: Long, version: Long) {
        mvc.put(PROFILE) {
            header("Authorization", "Bearer responsible.$sub.$tenant")
            contentType = MediaType.APPLICATION_JSON
            content = profileBody(currentNif(tenant), employees, turnover, version)
        }.andExpect { status { isOk() } }
    }

    private fun readScope(tenant: String, role: String = "responsible"): ResultActionsDsl =
        mvc.get(SCOPE) {
            header("Authorization", "Bearer $role.${UUID.randomUUID()}.$tenant")
            if (role == "consultant") header("X-Alinha-Tenant", tenant)
        }

    private fun answer(tenant: String, body: String = answersBody(), role: String = "responsible"): ResultActionsDsl =
        mvc.put(ANSWERS) {
            header("Authorization", "Bearer $role.${UUID.randomUUID()}.$tenant")
            if (role == "consultant") header("X-Alinha-Tenant", tenant)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    @Suppress("UNCHECKED_CAST")
    private fun scopeOf(tenant: String): Map<String, Any?> {
        val body = readScope(tenant).andExpect { status { isOk() } }.andReturn().response.contentAsString
        return json.readValue(body, Map::class.java) as Map<String, Any?>
    }

    private fun auditCount(tenant: String, action: String): Long =
        jdbc.queryForObject(
            "select count(*) from audit.audit_event where tenant_id = ?::uuid and action = ?", Long::class.java, tenant, action,
        )!!

    @Test
    fun `before any answer the scope has entity types and no result`() {
        val tenant = registered()

        readScope(tenant).andExpect {
            status { isOk() }
            jsonPath("$.answers") { value(null) }
            jsonPath("$.result") { value(null) }
            jsonPath("$.qualification") { value(null) }
            jsonPath("$.nis2") { value(null) }
            jsonPath("$.entityTypes.length()") { value(Nis2EntityType.entries.size) }
            jsonPath("$.entityTypes[0].id") { value("ELECTRICITY_SUPPLIER") }
            jsonPath("$.entityTypes[0].annex") { value("I") }
            jsonPath("$.entityTypes[0].reference") { value("Anexo I, 1.a)") }
            jsonPath("$.entityTypes[0].label") { value("Comercialização de eletricidade") }
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `the sector's entity types come suggested`() {
        val tenant = registered()

        val types = scopeOf(tenant)["entityTypes"] as List<Map<String, Any?>>
        val suggested = types.filter { it["suggested"] == true }.map { it["id"] }

        assertThat(suggested).containsExactly("MOTOR_VEHICLE_MANUFACTURING")
    }

    @Test
    fun `a medium car-parts maker is an important entity`() {
        val tenant = registered()

        answer(tenant).andExpect {
            status { isOk() }
            jsonPath("$.answers.version") { value(0) }
            jsonPath("$.answers.nis2.entityTypes[0]") { value("MOTOR_VEHICLE_MANUFACTURING") }
            jsonPath("$.result.rulesVersion") { value(ApplicabilityRules.VERSION) }
            jsonPath("$.result.size") { value("medium") }
            jsonPath("$.result.nis2Classification") { value("important") }
            jsonPath("$.result.indicative") { value(true) }
            jsonPath("$.nis2.classification") { value("important") }
            jsonPath("$.nis2.official") { value(false) }
            jsonPath("$.nis2.suggested") { value("important") }
        }
        readScope(tenant).andExpect { jsonPath("$.result.nis2Classification") { value("important") } }
    }

    @Test
    fun `a small DNS provider is essential`() {
        val tenant = registered()
        changeSize(tenant, employees = 8, turnover = 900_000, version = 0)

        answer(tenant, answersBody(listOf("DNS_SERVICE_PROVIDER"))).andExpect {
            status { isOk() }
            jsonPath("$.result.size") { value("micro") }
            jsonPath("$.result.nis2Classification") { value("essential") }
        }
    }

    @Test
    fun `the result lists NIS2, GDPR and the AI Act with explanations`() {
        val tenant = registered()

        answer(tenant).andExpect {
            status { isOk() }
            jsonPath("$.result.frameworks[0].framework") { value("nis2-qnrcs") }
            jsonPath("$.result.frameworks[0].applies") { value("yes") }
            jsonPath("$.result.frameworks[1].framework") { value("gdpr") }
            jsonPath("$.result.frameworks[1].applies") { value("yes") }
            jsonPath("$.result.frameworks[2].framework") { value("ai-act") }
            jsonPath("$.result.frameworks[2].applies") { value("yes") }
            jsonPath("$.result.frameworks[1].findings[0].id") { value("gdpr.applies") }
            jsonPath("$.result.frameworks[1].findings[0].explanation") { isNotEmpty() }
            jsonPath("$.result.frameworks[1].findings[0].legalBasis") { isNotEmpty() }
            jsonPath("$.result.frameworks[1].findings[0].review") { value(false) }
        }
    }

    @Test
    fun `the classification follows a later profile change`() {
        val tenant = registered()
        changeSize(tenant, employees = 40, turnover = 9_000_000, version = 0)
        answer(tenant).andExpect { jsonPath("$.result.nis2Classification") { value("not-covered") } }

        changeSize(tenant, employees = 300, turnover = 60_000_000, version = 1)

        readScope(tenant).andExpect {
            jsonPath("$.result.size") { value("large") }
            jsonPath("$.result.nis2Classification") { value("important") }
        }
    }

    @Test
    fun `saving answers is audited with the rules version`() {
        val tenant = registered()

        answer(tenant).andExpect { status { isOk() } }

        val details = jdbc.queryForObject(
            "select details::text from audit.audit_event where tenant_id = ?::uuid and action = 'organization.scope-answered'",
            String::class.java, tenant,
        )
        assertThat(details).contains(ApplicabilityRules.VERSION).contains("\"important\"").contains("MOTOR_VEHICLE_MANUFACTURING")
    }

    @Test
    fun `changed answers are saved and audited again`() {
        val tenant = registered()
        answer(tenant).andExpect { status { isOk() } }

        answer(tenant, answersBody(listOf("FOOD"), version = 0)).andExpect {
            status { isOk() }
            jsonPath("$.answers.version") { value(1) }
            jsonPath("$.answers.nis2.entityTypes[0]") { value("FOOD") }
        }
        assertThat(auditCount(tenant, "organization.scope-answered")).isEqualTo(2)
    }

    @Test
    fun `saving the same answers records nothing`() {
        val tenant = registered()
        answer(tenant).andExpect { status { isOk() } }

        answer(tenant, answersBody(version = 0)).andExpect {
            status { isOk() }
            jsonPath("$.answers.version") { value(0) }
        }
        assertThat(auditCount(tenant, "organization.scope-answered")).isEqualTo(1)
    }

    @Test
    fun `contradictory answers are a 422`() {
        val tenant = registered()

        answer(tenant, answersBody(gdpr = """{"processesPersonalData": false, "specialOrCriminalData": true}""")).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("gdpr") }
        }
        readScope(tenant).andExpect { jsonPath("$.answers") { value(null) } }
        assertThat(auditCount(tenant, "organization.scope-answered")).isZero()
    }

    @Test
    fun `an unknown entity type is a 422 on nis2 entityTypes`() {
        val tenant = registered()

        answer(tenant, answersBody(listOf("TELEPORTATION"))).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("nis2.entityTypes") }
        }
    }

    @Test
    fun `a stale version is a 409`() {
        val tenant = registered()
        answer(tenant).andExpect { status { isOk() } }

        answer(tenant, answersBody(listOf("FOOD"), version = 7)).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("VERSION_CONFLICT") }
        }
    }

    @Test
    fun `a version for answers that do not exist yet is a 409`() {
        val tenant = registered()

        answer(tenant, answersBody(version = 3)).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("VERSION_CONFLICT") }
        }
    }

    @Test
    fun `a second save without version is invalid data`() {
        val tenant = registered()
        answer(tenant).andExpect { status { isOk() } }

        answer(tenant, answersBody(listOf("FOOD"))).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("version") }
        }
    }

    @Test
    fun `a second first save is a version conflict`() {
        val tenant = registered()
        val input = ApplicabilityAnswersInput(gdpr = GdprAnswersInput(processesPersonalData = false), nis2 = Nis2AnswersInput())

        val error = raceWithUncommittedInsert(
            tenant,
            """
            insert into organization.applicability_answers (id, tenant_id, version, public_body, nis2_entity_types,
                critical_entity, significant_impact, higher_education, processes_personal_data, regular_or_risky_processing,
                special_or_criminal_data, large_scale_monitoring, large_scale_special_data, uses_ai_systems,
                provides_ai_systems, high_risk_area, interacts_or_generates_content, answered_at, updated_at)
            values (?, ?::uuid, 0, false, '{}', false, false, false, false, false, false, false, false, false, false,
                false, false, now(), now())
            """.trimIndent(),
            "applicability_answers",
        ) { service.saveAnswers(input, null) }

        assertThat(error).isInstanceOf(StaleVersionException::class.java)
        assertThat(auditCount(tenant, "organization.scope-answered")).isZero()
    }

    /**
     * Another first write of this tenant inserted its row ([insert], with the id and tenant as parameters 1 and 2) but
     * has not committed: [write] does not see it, and its INSERT waits on the unique tenant index until the other one
     * commits, then fails. Returns what [write] threw; the API must answer 409, not 500.
     */
    private fun raceWithUncommittedInsert(tenant: String, insert: String, table: String, write: () -> Unit): Throwable? {
        dataSource.connection.use { other ->
            other.autoCommit = false
            other.prepareStatement(insert).use {
                it.setObject(1, UUID.randomUUID())
                it.setString(2, tenant)
                it.executeUpdate()
            }
            val executor = Executors.newSingleThreadExecutor()
            try {
                // runAs refuses to run inside a request; the executor's thread has none.
                val attempt = executor.submit<Unit> {
                    tenants.runAs(TenantId(UUID.fromString(tenant))) { tx.execute { write() } }
                }
                waitUntilBlockedOnLock(table)
                other.commit()
                return runCatching { attempt.get(30, TimeUnit.SECONDS) }.exceptionOrNull()?.cause
            } finally {
                executor.shutdownNow()
            }
        }
    }

    private fun waitUntilBlockedOnLock(table: String) {
        repeat(200) {
            val waiting = jdbc.queryForObject(
                "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like ?",
                Long::class.java, "%insert into organization.$table%",
            )!!
            if (waiting > 0) return
            Thread.sleep(50)
        }
        error("the write never waited on the other insert")
    }

    @Test
    fun `before registering there is no scope`() {
        val tenant = UUID.randomUUID().toString()

        readScope(tenant).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORGANIZATION_NOT_REGISTERED") }
        }
        answer(tenant, answersBody(gdpr = "{}")).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORGANIZATION_NOT_REGISTERED") }
        }
    }

    @ParameterizedTest(name = "{0} reads the scope: {1}")
    @CsvSource("responsible,200", "consultant,200", "analyst,200", "registrant,403")
    fun `who reads the scope`(role: String, status: Int) {
        val tenant = registered()

        readScope(tenant, role).andExpect { status { isEqualTo(status) } }
    }

    @ParameterizedTest(name = "{0} answers: {1}")
    @CsvSource("responsible,200", "consultant,200", "analyst,403", "registrant,403")
    fun `who answers`(role: String, status: Int) {
        val tenant = registered()

        answer(tenant, role = role).andExpect { status { isEqualTo(status) } }
        if (status == 403) assertThat(auditCount(tenant, "organization.scope-answered")).isZero()
    }

    @Test
    fun `another organization never sees these answers`() {
        val tenantA = registered()
        answer(tenantA).andExpect { status { isOk() } }
        val tenantB = registered()

        readScope(tenantB).andExpect {
            status { isOk() }
            jsonPath("$.answers") { value(null) }
        }
        assertThat(
            jdbc.queryForList("select tenant_id::text from organization.applicability_answers where tenant_id in (?::uuid, ?::uuid)",
                String::class.java, tenantA, tenantB),
        ).containsExactly(tenantA)
    }

    // --- Official CNCS qualification (US11) ---

    private fun qualify(tenant: String, body: String = qualificationBody(), role: String = "responsible"): ResultActionsDsl =
        mvc.put(QUALIFICATION) {
            header("Authorization", "Bearer $role.${UUID.randomUUID()}.$tenant")
            if (role == "consultant") header("X-Alinha-Tenant", tenant)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    @Test
    fun `the official qualification overrides the suggestion`() {
        val tenant = registered()
        answer(tenant).andExpect { jsonPath("$.nis2.classification") { value("important") } }

        qualify(tenant).andExpect {
            status { isOk() }
            jsonPath("$.qualification.version") { value(0) }
            jsonPath("$.qualification.classification") { value("essential") }
            jsonPath("$.qualification.actDate") { value("2026-01-15") }
            jsonPath("$.qualification.actReference") { value("CNCS/QA/2026/0042") }
            jsonPath("$.nis2.classification") { value("essential") }
            jsonPath("$.nis2.official") { value(true) }
            jsonPath("$.nis2.suggested") { value("important") }
            jsonPath("$.result.nis2Classification") { value("important") }
        }
        readScope(tenant).andExpect { jsonPath("$.nis2.classification") { value("essential") } }
    }

    @Test
    fun `a qualification can be registered before any answer`() {
        val tenant = registered()

        qualify(tenant, qualificationBody(classification = "important", reference = null)).andExpect {
            status { isOk() }
            jsonPath("$.qualification.actReference") { value(null) }
            jsonPath("$.nis2.classification") { value("important") }
            jsonPath("$.nis2.official") { value(true) }
            jsonPath("$.nis2.suggested") { value(null) }
            jsonPath("$.result") { value(null) }
        }
    }

    @Test
    fun `registering is audited with the suggestion`() {
        val tenant = registered()
        answer(tenant).andExpect { status { isOk() } }

        qualify(tenant).andExpect { status { isOk() } }

        val details = jdbc.queryForObject(
            "select details::text from audit.audit_event where tenant_id = ?::uuid and action = 'organization.qualification-registered'",
            String::class.java, tenant,
        )
        assertThat(details).contains("\"essential\"").contains("\"important\"").contains("2026-01-15")
    }

    @Test
    fun `a correction records only the changed fields`() {
        val tenant = registered()
        qualify(tenant).andExpect { status { isOk() } }

        qualify(tenant, qualificationBody(reference = "CNCS/QA/2026/0043", version = 0)).andExpect {
            status { isOk() }
            jsonPath("$.qualification.version") { value(1) }
            jsonPath("$.qualification.actReference") { value("CNCS/QA/2026/0043") }
        }

        val details = jdbc.queryForObject(
            "select details::text from audit.audit_event where tenant_id = ?::uuid and action = 'organization.qualification-corrected'",
            String::class.java, tenant,
        )
        assertThat(details).contains("actReference").doesNotContain("classification").doesNotContain("0043")
    }

    @Test
    fun `registering the same act again records nothing`() {
        val tenant = registered()
        qualify(tenant).andExpect { status { isOk() } }

        qualify(tenant, qualificationBody(version = 0)).andExpect {
            status { isOk() }
            jsonPath("$.qualification.version") { value(0) }
        }
        assertThat(auditCount(tenant, "organization.qualification-corrected")).isZero()
    }

    @Test
    fun `an invalid qualification is a 422`() {
        val tenant = registered()
        val tomorrow = LocalDate.now(ZoneId.of("Europe/Lisbon")).plusDays(1)

        qualify(tenant, qualificationBody(classification = "not-covered", actDate = tomorrow.toString())).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("classification") }
            jsonPath("$.errors[1].field") { value("actDate") }
            jsonPath("$.errors[1].message") { value("A data não pode ser no futuro.") }
        }
        readScope(tenant).andExpect { jsonPath("$.qualification") { value(null) } }
    }

    @Test
    fun `a malformed date is a 422 on actDate`() {
        val tenant = registered()

        qualify(tenant, qualificationBody(actDate = "2026-13-40")).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("actDate") }
        }
    }

    @Test
    fun `a stale qualification version is a 409`() {
        val tenant = registered()
        qualify(tenant).andExpect { status { isOk() } }

        qualify(tenant, qualificationBody(classification = "important", version = 4)).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("VERSION_CONFLICT") }
        }
    }

    @Test
    fun `a version for a qualification that does not exist yet is a 409`() {
        val tenant = registered()

        qualify(tenant, qualificationBody(version = 0)).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("VERSION_CONFLICT") }
        }
    }

    @Test
    fun `a correction without version is invalid data`() {
        val tenant = registered()
        qualify(tenant).andExpect { status { isOk() } }

        qualify(tenant, qualificationBody(classification = "important")).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("version") }
        }
    }

    @Test
    fun `a second first registration is a version conflict`() {
        val tenant = registered()

        val error = raceWithUncommittedInsert(
            tenant,
            """
            insert into organization.cncs_qualification (id, tenant_id, version, classification, act_date,
                registered_at, updated_at)
            values (?, ?::uuid, 0, 'IMPORTANT', date '2026-01-10', now(), now())
            """.trimIndent(),
            "cncs_qualification",
        ) {
            service.registerQualification(
                CncsQualificationInput("essential", LocalDate.of(2026, 1, 15)),
                null,
            )
        }

        assertThat(error).isInstanceOf(StaleVersionException::class.java)
        assertThat(auditCount(tenant, "organization.qualification-registered")).isZero()
    }

    @Test
    fun `before registering the organization there is no qualification to register`() {
        qualify(UUID.randomUUID().toString(), "{}").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORGANIZATION_NOT_REGISTERED") }
        }
    }

    @ParameterizedTest(name = "{0} registers the qualification: {1}")
    @CsvSource("responsible,200", "consultant,200", "analyst,403", "registrant,403")
    fun `who registers the qualification`(role: String, status: Int) {
        val tenant = registered()

        qualify(tenant, role = role).andExpect { status { isEqualTo(status) } }
        if (status == 403) assertThat(auditCount(tenant, "organization.qualification-registered")).isZero()
    }

    @Test
    fun `another organization never sees this qualification`() {
        val tenantA = registered()
        qualify(tenantA).andExpect { status { isOk() } }
        val tenantB = registered()

        readScope(tenantB).andExpect {
            status { isOk() }
            jsonPath("$.qualification") { value(null) }
            jsonPath("$.nis2") { value(null) }
        }
    }
}

private const val QUALIFICATION = "/api/organization/scope/qualification"

private fun qualificationBody(
    classification: String = "essential",
    actDate: String = "2026-01-15",
    reference: String? = "CNCS/QA/2026/0042",
    version: Long? = null,
) = """
    {${version?.let { "\"version\": $it," } ?: ""}
     "classification": "$classification", "actDate": "$actDate",
     "actReference": ${reference?.let { "\"$it\"" } ?: "null"}}
""".trimIndent()
