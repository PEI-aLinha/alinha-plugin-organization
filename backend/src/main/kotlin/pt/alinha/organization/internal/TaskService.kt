package pt.alinha.organization.internal

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pt.alinha.core.audit.AuditTrail
import pt.alinha.core.tenant.CorrelationContext
import pt.alinha.core.tenant.TenantContext
import pt.alinha.organization.api.TaskApi
import pt.alinha.organization.api.TaskAssignment
import pt.alinha.organization.api.TaskStatus
import pt.alinha.organization.api.TaskView
import pt.alinha.organization.events.TaskAssigned
import pt.alinha.organization.events.TaskCompleted
import pt.alinha.organization.internal.persistence.TaskEntity
import pt.alinha.organization.internal.persistence.TaskRepository
import java.time.Instant
import java.util.UUID

internal const val TASK_ENTITY_TYPE = "task"
internal const val TASK_ASSIGNED = "organization.task-assigned"
internal const val TASK_UNASSIGNED = "organization.task-unassigned"
internal const val TASK_COMPLETED = "organization.task-completed"

private val TYPE = Regex("^[a-z][a-z0-9-]{0,49}$")
/** A path inside the client area (`assessment/<id>`): no scheme, host, dots or query, so it cannot lead elsewhere. */
private val LINK = Regex("^[A-Za-z0-9][A-Za-z0-9/_-]*$")
private const val MAX_REFERENCE = 200
private const val MAX_TITLE = 200
private const val MAX_LINK = 300
private const val MAX_ASSIGNEE = 255

/**
 * [TaskApi] for other plugins and the "my tasks" list (organization#7). The audit details hold ids and the
 * assignee's `sub` only, not the title (free text from the calling plugin).
 */
@Service
class TaskService(
    private val tasks: TaskRepository,
    private val audit: AuditTrail,
    private val events: ApplicationEventPublisher,
    private val tenants: TenantContext,
    private val correlation: CorrelationContext,
) : TaskApi {

    @Transactional
    override fun assign(assignment: TaskAssignment): TaskView {
        val valid = validated(assignment)
        val now = Instant.now()
        val existing = tasks.findByTypeAndReference(valid.type, valid.reference)
        val task = when {
            existing == null -> tasks.save(
                TaskEntity(UUID.randomUUID(), valid.type, valid.reference, valid.title, valid.link, valid.assignee, valid.dueDate, now),
            )
            existing.reassign(valid.title, valid.link, valid.assignee, valid.dueDate, now) -> existing
            else -> return existing.toView()
        }
        audit.record(
            TASK_ASSIGNED, TASK_ENTITY_TYPE, task.id.toString(),
            mapOf("type" to task.type, "reference" to task.reference, "assignee" to task.assignee, "dueDate" to task.dueDate?.toString()),
        )
        events.publishEvent(
            TaskAssigned(tenants.current(), correlation.current(), task.id, task.type, task.reference, task.assignee, task.dueDate),
        )
        return task.toView()
    }

    @Transactional
    override fun unassign(type: String, reference: String) {
        val task = tasks.findByTypeAndReference(type, reference) ?: return
        tasks.delete(task)
        audit.record(
            TASK_UNASSIGNED, TASK_ENTITY_TYPE, task.id.toString(),
            mapOf("type" to task.type, "reference" to task.reference, "assignee" to task.assignee),
        )
    }

    @Transactional
    override fun complete(type: String, reference: String) {
        val task = tasks.findByTypeAndReference(type, reference) ?: return
        if (!task.complete(Instant.now())) return
        audit.record(
            TASK_COMPLETED, TASK_ENTITY_TYPE, task.id.toString(),
            mapOf("type" to task.type, "reference" to task.reference, "assignee" to task.assignee),
        )
        events.publishEvent(TaskCompleted(tenants.current(), correlation.current(), task.id, task.type, task.reference, task.assignee))
    }

    @Transactional(readOnly = true)
    override fun tasks(type: String): List<TaskView> = tasks.findByType(type).map { it.toView() }

    /** The caller's tasks in the current tenant: open ones first, by due date (none last), then the done ones. */
    @Transactional(readOnly = true)
    fun mine(subject: String): List<TaskView> =
        tasks.findByAssignee(subject).map { it.toView() }.sortedWith(
            compareBy<TaskView> { it.status != TaskStatus.OPEN }
                .thenBy(nullsLast()) { if (it.status == TaskStatus.OPEN) it.dueDate else null }
                .thenByDescending { it.completedAt ?: it.assignedAt },
        )

    private fun validated(a: TaskAssignment): TaskAssignment {
        require(TYPE.matches(a.type)) { "Task type must be kebab-case, at most 50 characters" }
        val reference = a.reference.trim()
        val title = a.title.trim()
        val assignee = a.assignee.trim()
        val link = a.link?.trim()
        require(reference.length in 1..MAX_REFERENCE) { "Task reference must have 1 to $MAX_REFERENCE characters" }
        require(title.length in 1..MAX_TITLE) { "Task title must have 1 to $MAX_TITLE characters" }
        require(assignee.length in 1..MAX_ASSIGNEE) { "Task assignee must have 1 to $MAX_ASSIGNEE characters" }
        require(link == null || (link.length <= MAX_LINK && LINK.matches(link))) {
            "Task link must be a path relative to the client area, at most $MAX_LINK characters"
        }
        return a.copy(reference = reference, title = title, assignee = assignee, link = link)
    }
}

internal fun TaskEntity.toView() =
    TaskView(id, type, reference, title, link, assignee, dueDate, status, assignedAt, completedAt)
