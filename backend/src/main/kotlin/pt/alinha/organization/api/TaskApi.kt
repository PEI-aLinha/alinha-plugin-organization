package pt.alinha.organization.api

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Tasks of the current tenant's members (organization#7, FR06, FR18): another plugin gives a piece of its own work
 * (an assessment question, an asset to register, an evidence source to connect) to a member, and closes the task
 * when that work is done. Each call joins the caller's transaction and is recorded in the audit trail there; the
 * caller checks its own permission before calling.
 *
 * A piece of work is identified by [TaskAssignment.type] and [TaskAssignment.reference] and has at most one task.
 * [TaskAssigned] and [TaskCompleted] (in `events`) are published for each change.
 */
interface TaskApi {
    /**
     * Gives the work to [TaskAssignment.assignee]. If it already has a task, the task is updated and reopened (a new
     * assignee, due date or title); the same assignment again changes nothing.
     *
     * @throws IllegalArgumentException when a field is blank, too long or [TaskAssignment.type] is not kebab-case.
     */
    fun assign(assignment: TaskAssignment): TaskView

    /** Removes the work's task, done or not. Nothing happens when it has none. */
    fun unassign(type: String, reference: String)

    /** Marks the work's task as done. Nothing happens when it has none or it is already done. */
    fun complete(type: String, reference: String)

    /** The tenant's tasks for [type] (e.g. to show who has each question). */
    fun tasks(type: String): List<TaskView>
}

data class TaskAssignment(
    /** Kind of work, kebab-case, prefixed with the owning plugin (e.g. `assessment-question`). */
    val type: String,
    /** The work's id in the owning plugin. */
    val reference: String,
    /** What to do, in European Portuguese, shown in the assignee's task list. */
    val title: String,
    /** The assignee's token `sub`. */
    val assignee: String,
    val dueDate: LocalDate? = null,
    /** Where the work is done, relative to the client area, e.g. `assessment/<id>`; null when there is no page. */
    val link: String? = null,
)

enum class TaskStatus { OPEN, DONE }

data class TaskView(
    val id: UUID,
    val type: String,
    val reference: String,
    val title: String,
    val link: String?,
    val assignee: String,
    val dueDate: LocalDate?,
    val status: TaskStatus,
    val assignedAt: Instant,
    val completedAt: Instant?,
)
