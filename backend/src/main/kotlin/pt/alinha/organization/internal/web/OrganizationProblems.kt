package pt.alinha.organization.internal.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import pt.alinha.organization.internal.InvalidOrganizationException
import pt.alinha.organization.internal.NifAlreadyRegisteredException
import pt.alinha.organization.internal.OrganizationNotRegisteredException
import pt.alinha.organization.internal.StaleVersionException

/** RFC 9457 Problem Details with `code` and `errors` (ADR 0003). `detail` is shown to the user, in Portuguese. */
@RestControllerAdvice(basePackageClasses = [OrganizationController::class])
class OrganizationProblems {

    @ExceptionHandler
    fun invalid(e: InvalidOrganizationException) =
        problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Há dados por corrigir.").apply {
            setProperty("errors", e.errors.map { mapOf("field" to it.field, "message" to it.message) })
        }

    @ExceptionHandler
    fun nifTaken(e: NifAlreadyRegisteredException) =
        problem(HttpStatus.CONFLICT, "NIF_ALREADY_REGISTERED", "Já existe uma organização registada com este NIF.")

    @ExceptionHandler
    fun notRegistered(e: OrganizationNotRegisteredException) =
        problem(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_REGISTERED", "A organização ainda não foi registada.")

    @ExceptionHandler(StaleVersionException::class, ObjectOptimisticLockingFailureException::class)
    fun stale() = problem(
        HttpStatus.CONFLICT, "VERSION_CONFLICT",
        "Os dados foram alterados entretanto. Recarregue a página e volte a fazer as alterações.",
    )

    private fun problem(status: HttpStatus, code: String, detail: String) =
        ProblemDetail.forStatusAndDetail(status, detail).apply { setProperty("code", code) }
}
