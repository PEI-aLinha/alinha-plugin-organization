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
        publishes = setOf("OrganizationRegistered", "ConsultantAssigned"),
        // Its own event: the consultant assignment runs after the registration commits (organization#5).
        consumes = setOf("OrganizationRegistered"),
        permissions = setOf(REGISTER, READ, EDIT),
        rolePermissions = mapOf(
            // A new account (ADR 0004) may only register its organization.
            "registrant" to setOf(REGISTER),
            "responsible" to setOf(READ, EDIT),
            "consultant" to setOf(READ, EDIT),
            "analyst" to setOf(READ),
        ),
        nav = listOf(NavEntry("Organização", "organization", NavArea.CLIENT, resource = "organization")),
    )

    companion object {
        const val REGISTER = "organization:register"
        const val READ = "organization:read"
        const val EDIT = "organization:edit"
    }
}
