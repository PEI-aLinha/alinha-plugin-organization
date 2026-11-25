package pt.alinha.organization.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import pt.alinha.core.tenant.TenantOwnedEntity
import pt.alinha.organization.api.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A piece of another plugin's work given to a member of the organization (organization#7). */
@Entity
@Table(schema = "organization", name = "task")
class TaskEntity(
    @Id val id: UUID,
    @Column(nullable = false, updatable = false) val type: String,
    @Column(nullable = false, updatable = false) val reference: String,
    title: String,
    link: String?,
    assignee: String,
    dueDate: LocalDate?,
    assignedAt: Instant,
) : TenantOwnedEntity() {

    @Version
    var version: Long = 0
        protected set

    var title: String = title
        protected set
    var link: String? = link
        protected set
    var assignee: String = assignee
        protected set
    @Column(name = "due_date")
    var dueDate: LocalDate? = dueDate
        protected set
    @Enumerated(EnumType.STRING)
    var status: TaskStatus = TaskStatus.OPEN
        protected set
    @Column(name = "assigned_at", nullable = false)
    var assignedAt: Instant = assignedAt
        protected set
    @Column(name = "completed_at")
    var completedAt: Instant? = null
        protected set

    /** What [reassign] changed. */
    enum class Change { NONE, DETAILS, ASSIGNMENT }

    /**
     * Gives the work to [assignee] again, reopening it if it was done. [Change.ASSIGNMENT] when the assignee changed
     * or the task was reopened (the assignee has new work); [Change.DETAILS] when only the title, link or due date did.
     */
    fun reassign(title: String, link: String?, assignee: String, dueDate: LocalDate?, at: Instant): Change {
        val assignment = this.assignee != assignee || status != TaskStatus.OPEN
        val details = this.title != title || this.link != link || this.dueDate != dueDate
        if (assignment) assignedAt = at
        this.title = title
        this.link = link
        this.assignee = assignee
        this.dueDate = dueDate
        status = TaskStatus.OPEN
        completedAt = null
        return when {
            assignment -> Change.ASSIGNMENT
            details -> Change.DETAILS
            else -> Change.NONE
        }
    }

    /** Returns false when it was already done. */
    fun complete(at: Instant): Boolean {
        if (status == TaskStatus.DONE) return false
        status = TaskStatus.DONE
        completedAt = at
        return true
    }
}
