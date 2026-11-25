package pt.alinha.organization.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener
import java.util.Collections
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.support.TransactionTemplate
import pt.alinha.core.tenant.TenantContext
import pt.alinha.organization.api.TaskApi
import pt.alinha.organization.api.TaskAssignment
import pt.alinha.organization.api.TaskStatus
import pt.alinha.organization.events.TaskAssigned
import pt.alinha.organization.events.TaskCompleted
import pt.alinha.pluginapi.TenantId
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

private const val MINE = "/api/organization/tasks/mine"
private const val QUESTION = "assessment-question"

/**
 * organization#7: other plugins give work to members through TaskApi, and each member sees only their own tasks.
 * Tasks are created with the real service, as the tenant, the way a plugin's endpoint or listener would.
 */
/** Collects the task events, published on the thread that runs the work (ApplicationEvents only sees the test's). */
class TaskEvents {
    val published: MutableList<Any> = Collections.synchronizedList(mutableListOf())

    @EventListener fun on(event: TaskAssigned) { published += event }
    @EventListener fun on(event: TaskCompleted) { published += event }

    inline fun <reified T> of(): List<T> = synchronized(published) { published.filterIsInstance<T>() }
}

@TestConfiguration
class TaskEventsConfig {
    @Bean fun taskEvents() = TaskEvents()
}

@SpringBootTest(classes = [OrganizationTestApplication::class])
@AutoConfigureMockMvc
@Import(TaskEventsConfig::class)
class TaskIntegrationTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = OrganizationDatabase.register(registry)
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tasks: TaskApi
    @Autowired private lateinit var tenants: TenantContext
    @Autowired private lateinit var tx: TransactionTemplate
    @Autowired private lateinit var events: TaskEvents

    // Unique per test: the database is shared with the other test classes.
    private val tenant = TenantId(UUID.randomUUID())
    private val ana = "ana-" + UUID.randomUUID()
    private val bruno = "bruno-" + UUID.randomUUID()

    @BeforeEach
    fun clearEvents() = events.published.clear()

    /**
     * Runs [block] as the tenant in a transaction, like another plugin's listener. On another thread: MockMvc leaves its
     * request bound to the test thread, and runAs refuses to run inside a request.
     */
    private fun <T : Any> asTenant(tenantId: TenantId = tenant, block: () -> T): T =
        try {
            CompletableFuture.supplyAsync { tenants.runAs(tenantId) { tx.execute { block() }!! } }.join()
        } catch (e: CompletionException) {
            throw e.cause ?: e
        }

    private fun assign(
        reference: String,
        assignee: String = ana,
        due: LocalDate? = null,
        title: String = "Responder à pergunta $reference",
        tenantId: TenantId = tenant,
    ) = asTenant(tenantId) {
        tasks.assign(TaskAssignment(QUESTION, reference, title, assignee, due, "assessment/a1/questions/$reference"))
    }

    private fun mine(subject: String, role: String = "analyst", tenantId: TenantId = tenant) =
        mvc.get(MINE) { header("Authorization", "Bearer $role.$subject.${tenantId.value}") }

    /** References in the order the list returns them. */
    private fun mineReferences(subject: String): List<String> {
        val body = mine(subject).andExpect { status { isOk() } }.andReturn().response.contentAsString
        val list = JsonMapper().readTree(body)
        return (0 until list.size()).map { list.get(it).get("reference").asString() }
    }

    private fun audit(action: String): List<String> = jdbc.queryForList(
        "select details::text from audit.audit_event where tenant_id = ? and action = ? order by occurred_at",
        String::class.java, tenant.value, action,
    )

    @Test
    fun `a member sees only their own tasks, open ones first by due date`() {
        assign("q3", due = null)
        assign("q1", due = LocalDate.of(2026, 12, 10))
        assign("q2", due = LocalDate.of(2026, 12, 5))
        assign("q4", due = LocalDate.of(2026, 12, 1))
        asTenant { tasks.complete(QUESTION, "q4") }
        assign("q5", assignee = bruno)
        assign("q6", tenantId = TenantId(UUID.randomUUID())) // Ana's sub in another organization

        assertThat(mineReferences(ana)).containsExactly("q2", "q1", "q3", "q4")
        mine(ana).andExpect {
            jsonPath("$[0].title") { value("Responder à pergunta q2") }
            jsonPath("$[0].link") { value("assessment/a1/questions/q2") }
            jsonPath("$[0].dueDate") { value("2026-12-05") }
            jsonPath("$[0].status") { value("OPEN") }
            jsonPath("$[3].status") { value("DONE") }
        }
        assertThat(mineReferences(bruno)).containsExactly("q5")
    }

    @Test
    fun `a member with no tasks gets an empty list`() {
        mine(ana, role = "responsible").andExpect {
            status { isOk() }
            content { json("[]") }
        }
    }

    @Test
    fun `only analysts and the designated officer have a task list`() {
        mine(ana, role = "consultant").andExpect { status { isForbidden() } }
        mine(ana, role = "registrant").andExpect { status { isForbidden() } }
    }

    @Test
    fun `assigning is audited and published, and the same assignment again changes nothing`() {
        val task = assign("q1", due = LocalDate.of(2026, 12, 5))
        assign("q1", due = LocalDate.of(2026, 12, 5))

        assertThat(audit(TASK_ASSIGNED)).singleElement().asString()
            .contains(ana, "\"reference\": \"q1\"", "2026-12-05").doesNotContain("Responder")
        val published = events.of<TaskAssigned>()
        assertThat(published).singleElement().satisfies({
            assertThat(it.taskId).isEqualTo(task.id)
            assertThat(it.tenantId).isEqualTo(tenant)
            assertThat(it.assigneeId).isEqualTo(ana)
            assertThat(it.type).isEqualTo(QUESTION)
            assertThat(it.dueDate).isEqualTo(LocalDate.of(2026, 12, 5))
        })
    }

    @Test
    fun `assigning the same work to someone else moves the task, and reopens a done one`() {
        val first = assign("q1")
        asTenant { tasks.complete(QUESTION, "q1") }

        val moved = assign("q1", assignee = bruno)

        assertThat(moved.id).isEqualTo(first.id)
        assertThat(moved.status).isEqualTo(TaskStatus.OPEN)
        assertThat(moved.completedAt).isNull()
        mine(ana).andExpect { content { json("[]") } }
        assertThat(mineReferences(bruno)).containsExactly("q1")
        assertThat(events.of<TaskAssigned>().map { it.assigneeId }).containsExactly(ana, bruno)
    }

    @Test
    fun `a new title or due date is audited but sends no new assignment event`() {
        assign("q1", due = LocalDate.of(2026, 12, 5))
        assign("q1", due = LocalDate.of(2026, 12, 9), title = "Responder à pergunta sobre MFA")

        assertThat(events.of<TaskAssigned>()).hasSize(1)
        assertThat(audit(TASK_ASSIGNED)).hasSize(2)
        assertThat(asTenant { tasks.tasks(QUESTION) }.single().title).isEqualTo("Responder à pergunta sobre MFA")
    }

    @Test
    fun `the reference is matched the same way when assigning and when closing`() {
        asTenant { tasks.assign(TaskAssignment(QUESTION, " q1 ", "Responder", ana)) }

        asTenant { tasks.complete(QUESTION, " q1 ") }

        val task = asTenant { tasks.tasks(QUESTION) }.single()
        assertThat(task.reference).isEqualTo("q1")
        assertThat(task.status).isEqualTo(TaskStatus.DONE)
        asTenant { tasks.unassign(QUESTION, "q1 ") }
        assertThat(asTenant { tasks.tasks(QUESTION) }).isEmpty()
    }

    @Test
    fun `completing closes the task once, with an audit entry and an event`() {
        val task = assign("q1")

        asTenant { tasks.complete(QUESTION, "q1") }
        asTenant { tasks.complete(QUESTION, "q1") }
        asTenant { tasks.complete(QUESTION, "unknown") }

        val done = asTenant { tasks.tasks(QUESTION) }.single()
        assertThat(done.status).isEqualTo(TaskStatus.DONE)
        assertThat(done.completedAt).isNotNull()
        assertThat(audit(TASK_COMPLETED)).hasSize(1)
        assertThat(events.of<TaskCompleted>()).singleElement()
            .satisfies({ assertThat(it.taskId).isEqualTo(task.id) })
    }

    @Test
    fun `unassigning removes the task`() {
        assign("q1")

        asTenant { tasks.unassign(QUESTION, "q1") }
        asTenant { tasks.unassign(QUESTION, "q1") }

        mine(ana).andExpect { content { json("[]") } }
        assertThat(audit(TASK_UNASSIGNED)).singleElement().asString().contains(ana)
    }

    @Test
    fun `another plugin lists the organization's tasks of its own type only`() {
        assign("q1")
        assign("q2", assignee = bruno)
        asTenant { tasks.assign(TaskAssignment("risk-assets-asset", "a1", "Registar o servidor", ana)) }
        assign("q9", tenantId = TenantId(UUID.randomUUID()))

        val questions = asTenant { tasks.tasks(QUESTION) }

        assertThat(questions.map { it.reference to it.assignee }).containsExactlyInAnyOrder("q1" to ana, "q2" to bruno)
    }

    @ParameterizedTest
    @ValueSource(strings = ["https://evil.example/x", "/assessment/a1", "//evil.example", "assessment/../admin", "a?b=1"])
    fun `a link must stay inside the client area`(link: String) {
        assertThrows<IllegalArgumentException> {
            asTenant { tasks.assign(TaskAssignment(QUESTION, "q1", "Responder", ana, link = link)) }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Assessment", "assessment_question", "-question", ""])
    fun `the type must be kebab-case`(type: String) {
        assertThrows<IllegalArgumentException> {
            asTenant { tasks.assign(TaskAssignment(type, "q1", "Responder", ana)) }
        }
    }

    @Test
    fun `blank fields are refused`() {
        assertThrows<IllegalArgumentException> { asTenant { tasks.assign(TaskAssignment(QUESTION, " ", "Responder", ana)) } }
        assertThrows<IllegalArgumentException> { asTenant { tasks.assign(TaskAssignment(QUESTION, "q1", " ", ana)) } }
        assertThrows<IllegalArgumentException> { asTenant { tasks.assign(TaskAssignment(QUESTION, "q1", "Responder", "")) } }
        assertThat(asTenant { tasks.tasks(QUESTION) }).isEmpty()
    }
}
