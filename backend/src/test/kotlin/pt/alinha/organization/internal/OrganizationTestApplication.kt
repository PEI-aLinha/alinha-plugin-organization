package pt.alinha.organization.internal

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.context.event.EventListener
import org.springframework.core.task.TaskDecorator
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.postgresql.PostgreSQLContainer
import pt.alinha.core.access.internal.AccessWebConfig
import pt.alinha.core.access.internal.RequiresPermissionInterceptor
import pt.alinha.core.access.internal.RolePermissionRegistry
import pt.alinha.core.access.internal.SecurityConfig
import pt.alinha.core.audit.internal.AuditActor
import pt.alinha.core.audit.internal.JpaAuditTrail
import pt.alinha.core.compliance.ApplicabilityRules
import pt.alinha.core.registry.PluginRegistry
import pt.alinha.core.tenant.internal.CorrelationIdFilter
import pt.alinha.core.tenant.internal.HibernateTenantResolver
import pt.alinha.core.tenant.internal.AsyncListenersConfig
import pt.alinha.core.tenant.internal.IdentityDirectory
import pt.alinha.core.tenant.internal.JwtTenantContext
import pt.alinha.core.tenant.internal.RegistrationRateLimiter
import pt.alinha.core.tenant.internal.ScopedCorrelationContext
import pt.alinha.core.tenant.internal.TenantProvisioner
import pt.alinha.core.tenant.internal.TenantResolutionInterceptor
import pt.alinha.core.tenant.internal.TenantWebConfig
import pt.alinha.core.tenant.AssignedConsultant
import pt.alinha.core.tenant.ConsultantAssignment
import pt.alinha.core.tenant.TenantContext
import pt.alinha.organization.OrganizationPlugin
import pt.alinha.organization.events.ConsultantAssigned
import pt.alinha.organization.internal.persistence.OrganizationProfileRepository
import pt.alinha.organization.internal.web.OrganizationController
import pt.alinha.organization.internal.web.OrganizationProblems
import pt.alinha.organization.internal.web.ScopeController
import pt.alinha.pluginapi.TenantId
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** Records assignments instead of calling Keycloak; can be told to fail. */
class FakeIdentityDirectory : IdentityDirectory {
    val assigned: MutableList<Pair<String, TenantId>> = Collections.synchronizedList(mutableListOf())
    @Volatile var failOnAssign = false

    override fun assignTenant(subject: String, tenantId: TenantId) {
        if (failOnAssign) error("identity provider down")
        assigned += subject to tenantId
    }

    override fun revokeTenant(subject: String, tenantId: TenantId) = Unit
}

/**
 * Stands in for the core's assignment (tested in the core): records each call's tenant and how its transaction ended
 * (TransactionSynchronization.STATUS_*); the answer can be set.
 */
class FakeConsultantAssignment(private val tenants: TenantContext) : ConsultantAssignment {
    val calls: MutableList<Call> = Collections.synchronizedList(mutableListOf())
    val transactionOutcomes: MutableList<Int> = Collections.synchronizedList(mutableListOf())
    @Volatile var answer: () -> AssignedConsultant? = { AssignedConsultant("consultant-sub", newlyAssigned = true) }

    data class Call(val tenant: TenantId, val inTransaction: Boolean)

    override fun assignConsultant(): AssignedConsultant? {
        val inTransaction = TransactionSynchronizationManager.isActualTransactionActive()
        calls += Call(tenants.current(), inTransaction)
        if (inTransaction) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    transactionOutcomes += status
                }
            })
        }
        return answer()
    }
}

/**
 * Counts finished @Async tasks (Boot applies a TaskDecorator bean to the executor @EnableAsync uses), so a test can
 * wait for the listener to end instead of sleeping before it asserts that nothing was published.
 */
class FinishedAsyncTasks : TaskDecorator {
    private val finished = AtomicInteger()

    fun count(): Int = finished.get()

    override fun decorate(runnable: Runnable) = Runnable {
        try {
            runnable.run()
        } finally {
            finished.incrementAndGet()
        }
    }
}

/** Collects [ConsultantAssigned], which is published on the listener's thread (ApplicationEvents only sees the test's). */
class ConsultantAssignedCollector {
    val events: MutableList<ConsultantAssigned> = Collections.synchronizedList(mutableListOf())

    @EventListener
    fun on(event: ConsultantAssigned) {
        events += event
    }
}

/**
 * The plugin with the core's real RBAC, tenant resolution, provisioning and audit trail on PostgreSQL. Only the JWT
 * decoder and the identity provider are fakes. Token value: `<role>.<sub>[.<tenant>]` (a colon is not allowed in a Bearer token); a consultant's tenant is
 * its assigned organization, a client user's is `tenant_id`.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = ["pt.alinha.organization", "pt.alinha.core.audit"])
@EnableJpaRepositories(basePackageClasses = [OrganizationProfileRepository::class])
@Import(
    SecurityConfig::class, AccessWebConfig::class, RequiresPermissionInterceptor::class,
    RolePermissionRegistry::class, PluginRegistry::class,
    TenantWebConfig::class, TenantResolutionInterceptor::class, JwtTenantContext::class,
    HibernateTenantResolver::class, CorrelationIdFilter::class, ScopedCorrelationContext::class, TenantProvisioner::class, RegistrationRateLimiter::class, AuditActor::class, JpaAuditTrail::class,
    OrganizationPlugin::class, OrganizationService::class, OrganizationController::class, OrganizationProblems::class,
    ApplicabilityRules::class, ScopeService::class, ScopeController::class,
    AsyncListenersConfig::class, ConsultantAssignmentListener::class,
)
class OrganizationTestApplication {
    @Bean
    fun identityDirectory() = FakeIdentityDirectory()

    @Bean
    fun consultantAssignment(tenants: TenantContext) = FakeConsultantAssignment(tenants)

    @Bean
    fun consultantAssignedEvents() = ConsultantAssignedCollector()

    @Bean
    fun finishedAsyncTasks() = FinishedAsyncTasks()

    /** The provisioner requires one (its orphan metric must be exported); the app gets it from Actuator. */
    @Bean
    fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()

    @Bean
    @Primary
    fun testJwtDecoder(): JwtDecoder = JwtDecoder { token ->
        val (role, sub, tenant) = (token.split(".") + listOf("", "")).take(3)
        val builder = Jwt.withTokenValue(token).header("alg", "none").subject(sub).claim("email_verified", true)
            .claim("realm_access", mapOf("roles" to listOf(role)))
        when {
            tenant.isEmpty() -> Unit
            role == "consultant" -> builder.claim("tenants", listOf(tenant))
            else -> builder.claim("tenant_id", tenant)
        }
        builder.build()
    }
}

object OrganizationDatabase {
    val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17").apply { start() }

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl)
        registry.add("spring.datasource.username", postgres::getUsername)
        registry.add("spring.datasource.password", postgres::getPassword)
        registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        // As in the application: a session opened before the tenant is known would keep "no tenant".
        registry.add("spring.jpa.open-in-view") { "false" }
        registry.add("spring.flyway.locations") { "classpath:db/migration" }
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { "https://keycloak.test/realms/alinha" }
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences") { "alinha-api" }
    }
}
