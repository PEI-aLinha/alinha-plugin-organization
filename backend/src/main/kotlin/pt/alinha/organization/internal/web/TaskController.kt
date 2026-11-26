package pt.alinha.organization.internal.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pt.alinha.core.access.RequiresPermission
import pt.alinha.organization.api.TaskView
import pt.alinha.organization.internal.TaskService
import java.security.Principal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A task as `GET /tasks/mine` returns it: like [TaskView], with the status in the contract's lowercase form. */
data class TaskResponse(
    val id: UUID,
    val type: String,
    val typeLabel: String,
    val reference: String,
    val title: String,
    val link: String?,
    val assigneeId: String,
    val dueDate: LocalDate?,
    /** `open` or `done`. */
    val status: String,
    val assignedAt: Instant,
    val completedAt: Instant?,
)

private fun TaskView.toResponse() =
    TaskResponse(id, type, typeLabel, reference, title, link, assigneeId, dueDate, status.name.lowercase(), assignedAt, completedAt)

/**
 * The caller's own tasks in the current organization (organization#7). Only the caller's: a member never sees
 * another member's tasks here. Tasks are created and closed by other plugins through TaskApi.
 */
@RestController
@RequestMapping("/api/organization/tasks")
class TaskController(private val tasks: TaskService) {

    // The principal's name is the token's `sub` (JwtAuthenticationToken), the id tasks are assigned to.
    @RequiresPermission("task", "read")
    @GetMapping("/mine")
    fun mine(principal: Principal): List<TaskResponse> = tasks.mine(principal.name).map { it.toResponse() }
}
