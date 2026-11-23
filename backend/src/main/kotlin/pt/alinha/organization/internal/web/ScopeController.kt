package pt.alinha.organization.internal.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pt.alinha.core.access.RequiresPermission
import pt.alinha.organization.internal.ScopeService
import pt.alinha.organization.internal.ScopeView
import pt.alinha.organization.internal.domain.AiActAnswersInput
import pt.alinha.organization.internal.domain.ApplicabilityAnswersInput
import pt.alinha.organization.internal.domain.GdprAnswersInput
import pt.alinha.organization.internal.domain.Nis2AnswersInput

/** Body of `PUT /scope/answers`: the answers plus the version they were loaded with (null on the first save). */
data class ApplicabilityAnswersUpdate(
    val version: Long? = null,
    val publicBody: Boolean? = null,
    val annualBalanceSheetEur: Long? = null,
    val nis2: Nis2AnswersInput? = null,
    val gdpr: GdprAnswersInput? = null,
    val aiAct: AiActAnswersInput? = null,
) {
    fun toInput() = ApplicabilityAnswersInput(publicBody, annualBalanceSheetEur, nis2, gdpr, aiAct)
}

/** Regulatory scope and NIS2 classification (organization#4). Errors: OrganizationProblems. */
@RestController
@RequestMapping("/api/organization/scope")
class ScopeController(private val scopes: ScopeService) {

    @RequiresPermission("organization", "read")
    @GetMapping
    fun scope(): ScopeView = scopes.scope()

    @RequiresPermission("organization", "edit")
    @PutMapping("/answers")
    fun saveAnswers(@RequestBody body: ApplicabilityAnswersUpdate): ScopeView =
        scopes.saveAnswers(body.toInput(), body.version)
}
