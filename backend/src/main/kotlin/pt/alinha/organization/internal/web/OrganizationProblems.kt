package pt.alinha.organization.internal.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import pt.alinha.organization.internal.InvalidOrganizationException
import pt.alinha.organization.internal.NifAlreadyRegisteredException
import pt.alinha.organization.internal.OrganizationNotRegisteredException
import pt.alinha.organization.internal.StaleVersionException

private val CORE_MESSAGES = mapOf(
    "ORGANIZATION_ALREADY_EXISTS" to "Esta conta já tem uma organização registada. Entre de novo para continuar.",
    "IDENTITY_UNAVAILABLE" to "Não foi possível concluir o registo agora. Tente novamente dentro de alguns minutos.",
    "TOO_MANY_REGISTRATIONS" to "Foram feitas demasiadas tentativas de registo. Tente novamente mais tarde.",
    "REGISTRATION_BUSY" to "Estamos a receber muitos registos neste momento. Tente novamente dentro de alguns minutos.",
)

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

    /**
     * Errors raised by the core while registering (TenantProvisioning) carry a `code` in their Problem Details
     * body. Return that body, with the message in Portuguese for the ones the user can see, and their headers
     * (`Retry-After` on 429 and 503).
     */
    @ExceptionHandler
    fun core(e: ErrorResponseException): ResponseEntity<ProblemDetail> {
        val body = e.body
        CORE_MESSAGES[body.properties?.get("code")]?.let { body.detail = it }
        return ResponseEntity.status(e.statusCode).headers(e.headers).body(body)
    }

    private fun problem(status: HttpStatus, code: String, detail: String) =
        ProblemDetail.forStatusAndDetail(status, detail).apply { setProperty("code", code) }
}
