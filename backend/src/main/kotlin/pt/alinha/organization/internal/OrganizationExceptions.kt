package pt.alinha.organization.internal

import pt.alinha.organization.internal.domain.FieldError

class InvalidOrganizationException(val errors: List<FieldError>) : RuntimeException("Invalid organization data")
class NifAlreadyRegisteredException : RuntimeException("NIF already registered")
class OrganizationNotRegisteredException : RuntimeException("Organization not registered")
class StaleVersionException : RuntimeException("Stale organization version")
