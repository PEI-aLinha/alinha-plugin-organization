package pt.alinha.organization.internal.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import pt.alinha.core.access.RequiresPermission
import pt.alinha.core.tenant.TenantAgnostic
import pt.alinha.organization.internal.MembersService
import java.time.Instant
import java.util.UUID

/** `Member` in the contract. */
data class MemberView(val id: String, val name: String?, val email: String?, val role: String)

/** `Invitation` in the contract. */
data class InvitationView(val id: UUID, val email: String, val createdAt: Instant, val expiresAt: Instant, val expired: Boolean)

/** `IssuedInvitation` in the contract: the secret goes out once, here, for the link the page shows. */
data class IssuedInvitationView(val id: UUID, val email: String, val expiresAt: Instant, val token: String)

data class InvitationRequest(val email: String? = null)

data class AcceptInvitationRequest(val token: String? = null)

/** The team page (organization#6). Only the Designated Officer sees it; the invitee accepts from the onboarding. */
@RestController
@RequestMapping("/api/organization")
class MembersController(private val members: MembersService) {

    @RequiresPermission("member", "read")
    @GetMapping("/members")
    fun members(): List<MemberView> = members.members().map { MemberView(it.subject, it.name, it.email, it.role) }

    @RequiresPermission("member", "manage")
    @DeleteMapping("/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@PathVariable memberId: String) = members.removeAnalyst(memberId)

    @RequiresPermission("member", "read")
    @GetMapping("/invitations")
    fun invitations(): List<InvitationView> = members.pendingInvitations()
        .map { InvitationView(it.id, it.email, it.createdAt, it.expiresAt, it.expired) }

    @RequiresPermission("member", "manage")
    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    fun invite(@RequestBody body: InvitationRequest): IssuedInvitationView =
        members.invite(body.email.orEmpty()).let { IssuedInvitationView(it.id, it.email, it.expiresAt, it.token) }

    @RequiresPermission("member", "manage")
    @DeleteMapping("/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@PathVariable invitationId: UUID) = members.revokeInvitation(invitationId)

    /** A new account joins the organization that invited it; it has no tenant yet, so none is resolved (ADR 0007). */
    @TenantAgnostic
    @RequiresPermission("invitation", "accept")
    @PostMapping("/invitations/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun accept(@RequestBody body: AcceptInvitationRequest) = members.accept(body.token.orEmpty())
}
