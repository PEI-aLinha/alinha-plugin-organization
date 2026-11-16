package pt.alinha.organization.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import org.postgresql.util.ServerErrorMessage
import org.springframework.dao.DataIntegrityViolationException

class ViolatedConstraintTest {

    /** PostgreSQL error fields as the driver receives them: one letter code per field, NUL-separated. */
    private fun uniqueViolation(constraint: String, message: String) =
        PSQLException(ServerErrorMessage("SERROR\u0000C23505\u0000M$message\u0000n$constraint\u0000"))

    @Test
    fun `the constraint comes from the error fields, whatever the server language`() {
        val portuguese = uniqueViolation(NIF_CONSTRAINT, "o valor duplicado da chave viola a restrição de unicidade «x»")
        val wrapped = DataIntegrityViolationException("could not execute statement", RuntimeException(portuguese))

        assertThat(violatedConstraint(wrapped)).isEqualTo(NIF_CONSTRAINT)
    }

    @Test
    fun `another constraint, or no PostgreSQL error, is not the NIF`() {
        val other = uniqueViolation("organization_profile_tenant_uq", "duplicate key")

        assertThat(violatedConstraint(DataIntegrityViolationException("x", other))).isEqualTo("organization_profile_tenant_uq")
        assertThat(violatedConstraint(DataIntegrityViolationException("x", IllegalStateException("x")))).isNull()
    }
}
