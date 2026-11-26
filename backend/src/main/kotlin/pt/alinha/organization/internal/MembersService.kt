package pt.alinha.organization.internal

import org.springframework.stereotype.Service
import pt.alinha.core.tenant.IssuedInvitation
import pt.alinha.core.tenant.PendingInvitation
import pt.alinha.core.tenant.TenantMember
import pt.alinha.core.tenant.TenantMembership
import java.util.UUID

class AlreadyMemberException : RuntimeException("Already a member of this organization")

/**
 * The organization's team (organization#6, FR05, US12): who has an account in it, and the analysts invited to join.
 * The core owns the invitation secrets and the identity changes (ADR 0007, `TenantMembership`); this plugin owns the
 * endpoints and who may call them (`member:read`, `member:manage`, `invitation:accept`).
 *
 * Not @Transactional: each `TenantMembership` call opens its own transaction, with its audit entry, and some of them
 * call the identity provider, which must not happen inside a transaction of ours.
 */
@Service
class MembersService(private val membership: TenantMembership) {

    fun members(): List<TenantMember> = membership.members()

    fun pendingInvitations(): List<PendingInvitation> = membership.pendingInvitations()

    /**
     * Refuses an email that already has an account here: the invitation could never be accepted (ADR 0007). A friendly
     * check, not the guarantee: someone who joins between the two calls still cannot accept, because the core refuses
     * an account that is already in an organization (`ORGANIZATION_ALREADY_EXISTS`). The core normalizes the email too.
     */
    fun invite(email: String): IssuedInvitation {
        val normalized = email.trim().lowercase()
        if (membership.members().any { it.email?.lowercase() == normalized }) throw AlreadyMemberException()
        return membership.invite(email)
    }

    fun revokeInvitation(id: UUID) = membership.revokeInvitation(id)

    fun removeAnalyst(subject: String) = membership.removeAnalyst(subject)

    fun accept(token: String) {
        membership.acceptForCurrentUser(token)
    }
}
