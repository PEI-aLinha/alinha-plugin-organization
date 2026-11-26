package pt.alinha.organization

import org.springframework.stereotype.Component
import pt.alinha.pluginapi.AlinhaPlugin
import pt.alinha.pluginapi.NavArea
import pt.alinha.pluginapi.NavEntry
import pt.alinha.pluginapi.PluginKind
import pt.alinha.pluginapi.PluginManifest

/** Registers the plugin in the core's Module Registry. Keep in sync with plugin.yaml. */
@Component
class OrganizationPlugin : AlinhaPlugin {
    override val manifest = PluginManifest(
        id = "organization",
        name = "Organization",
        kind = PluginKind.FEATURE,
        version = "0.1.0",
        publishes = setOf("OrganizationRegistered", "TaskAssigned", "TaskCompleted"),
        consumes = setOf(),
        permissions = setOf(REGISTER, READ, EDIT, TASKS),
        rolePermissions = mapOf(
            // A new account (ADR 0004) may only register its organization.
            "registrant" to setOf(REGISTER),
            "responsible" to setOf(READ, EDIT, TASKS),
            "consultant" to setOf(READ, EDIT),
            // Their own tasks (organization#7); other plugins give them work through TaskApi.
            "analyst" to setOf(READ, TASKS),
        ),
        nav = listOf(
            NavEntry("Organização", "organization", NavArea.CLIENT, resource = "organization"),
            NavEntry("As minhas tarefas", "tasks", NavArea.CLIENT, resource = "task", order = 15),
        ),
    )

    companion object {
        const val REGISTER = "organization:register"
        const val READ = "organization:read"
        const val EDIT = "organization:edit"
        /** The caller's own tasks ("As minhas tarefas"). */
        const val TASKS = "task:read"
    }
}
