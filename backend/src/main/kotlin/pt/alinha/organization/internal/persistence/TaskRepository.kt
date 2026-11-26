package pt.alinha.organization.internal.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Filtered by the current tenant (TenantOwnedEntity). */
interface TaskRepository : JpaRepository<TaskEntity, UUID> {
    fun findByTypeAndReference(type: String, reference: String): TaskEntity?
    fun findByType(type: String): List<TaskEntity>
    fun findByAssignee(assignee: String): List<TaskEntity>
}
