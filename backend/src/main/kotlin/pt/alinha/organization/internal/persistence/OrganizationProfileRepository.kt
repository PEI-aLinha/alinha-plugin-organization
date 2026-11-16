package pt.alinha.organization.internal.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Every query is filtered by the current tenant (TenantOwnedEntity), so "first" is the tenant's only profile. */
interface OrganizationProfileRepository : JpaRepository<OrganizationProfileEntity, UUID> {
    fun findFirstBy(): OrganizationProfileEntity?
}
