package pt.alinha.organization.internal.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pt.alinha.core.access.RequiresPermission
import pt.alinha.organization.api.TaskView
import pt.alinha.organization.internal.TaskService
import java.security.Principal

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
    fun mine(principal: Principal): List<TaskView> = tasks.mine(principal.name)
}
