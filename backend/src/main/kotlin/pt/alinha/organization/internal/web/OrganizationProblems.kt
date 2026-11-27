package pt.alinha.organization.internal.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.ErrorResponseException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import pt.alinha.organization.internal.AlreadyMemberException
import pt.alinha.organization.internal.InvalidOrganizationException
import pt.alinha.organization.internal.NifAlreadyRegisteredException
import pt.alinha.organization.internal.OrganizationNotRegisteredException
import pt.alinha.organization.internal.StaleVersionException
import tools.jackson.core.JacksonException

private val CORE_MESSAGES = mapOf(
    "ORGANIZATION_ALREADY_EXISTS" to "Esta conta já tem uma organização registada. Entre de novo para continuar.",
    "IDENTITY_UNAVAILABLE" to "Não foi possível concluir o registo agora. Tente novamente dentro de alguns minutos.",
    "TOO_MANY_REGISTRATIONS" to "Foram feitas demasiadas tentativas de registo. Tente novamente mais tarde.",
    "REGISTRATION_BUSY" to "Estamos a receber muitos registos neste momento. Tente novamente dentro de alguns minutos.",
    // Team and invitations (ADR 0007)
    "INVALID_EMAIL" to "Há dados por corrigir.",
    "INVITATION_NOT_FOUND" to "Este convite não existe ou foi cancelado. Peça um convite novo a quem o enviou.",
    "INVITATION_EXPIRED" to "Este convite já expirou. Peça um convite novo a quem o enviou.",
    "INVITATION_ALREADY_USED" to "Este convite já foi usado.",
    "INVITATION_FOR_ANOTHER_EMAIL" to
        "Este convite foi enviado para outro email. Entre com a conta desse email para o aceitar.",
    "MEMBER_NOT_FOUND" to "Esta pessoa já não faz parte da equipa.",
)

/** Field errors for core codes about one field, so the form can show the message next to it. */
private val CORE_FIELD_ERRORS = mapOf(
    "INVALID_EMAIL" to ("email" to "Este email não parece válido. Exemplo: nome@empresa.pt"),
)

/** RFC 9457 Problem Details with `code` and `errors` (ADR 0003). `detail` is shown to the user, in Portuguese. */
@RestControllerAdvice(basePackageClasses = [OrganizationController::class])
class OrganizationProblems {

    @ExceptionHandler
    fun invalid(e: InvalidOrganizationException) =
        problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Há dados por corrigir.").apply {
            setProperty("errors", e.errors.map { mapOf("field" to it.field, "message" to it.message) })
        }

    /**
     * A body Jackson cannot read: a value of the wrong type (`"employees": "abc"`), a number out of range, malformed
     * JSON or no body. Same answer as the validator's, naming the field when Jackson knows it.
     */
    @ExceptionHandler
    fun unreadable(e: HttpMessageNotReadableException): ProblemDetail {
        val field = unreadableField(e)
        val detail = if (field == null) UNREADABLE_BODY else "Há dados por corrigir."
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", detail).apply {
            setProperty("errors", listOfNotNull(field?.let { mapOf("field" to it, "message" to INVALID_VALUE) }))
        }
    }

    @ExceptionHandler
    fun nifTaken(e: NifAlreadyRegisteredException) =
        problem(HttpStatus.CONFLICT, "NIF_ALREADY_REGISTERED", "Já existe uma organização registada com este NIF.")

    @ExceptionHandler
    fun alreadyMember(e: AlreadyMemberException) =
        problem(HttpStatus.CONFLICT, "ALREADY_MEMBER", "Esta pessoa já faz parte da equipa.")

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
        val code = body.properties?.get("code")
        CORE_MESSAGES[code]?.let { body.detail = it }
        CORE_FIELD_ERRORS[code]?.let { (field, message) ->
            body.setProperty("errors", listOf(mapOf("field" to field, "message" to message)))
        }
        return ResponseEntity.status(e.statusCode).headers(e.headers).body(body)
    }

    private fun problem(status: HttpStatus, code: String, detail: String) =
        ProblemDetail.forStatusAndDetail(status, detail).apply { setProperty("code", code) }
}

private const val INVALID_VALUE = "Este valor não é válido."
private const val UNREADABLE_BODY = "Não foi possível ler os dados enviados. Recarregue a página e tente de novo."
private const val MAX_CAUSE_DEPTH = 20

/** The body's field Jackson failed on, in the validator's notation (`address.postalCode`), or null if unknown. */
internal fun unreadableField(e: Throwable): String? =
    generateSequence(e) { it.cause }.take(MAX_CAUSE_DEPTH)
        .filterIsInstance<JacksonException>().firstOrNull()
        ?.path?.mapNotNull { it.propertyName }?.joinToString(".")?.ifEmpty { null }
