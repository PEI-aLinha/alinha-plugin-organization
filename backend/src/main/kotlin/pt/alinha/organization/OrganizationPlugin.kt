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
        publishes = setOf("OrganizationRegistered"),
        consumes = setOf(),
        permissions = setOf(REGISTER, READ, EDIT, MEMBER_READ, MEMBER_MANAGE, INVITATION_ACCEPT),
        rolePermissions = mapOf(
            // A new account (ADR 0004) may only register its organization or join one it was invited to (ADR 0007).
            "registrant" to setOf(REGISTER, INVITATION_ACCEPT),
            // Only the Designated Officer sees and manages the team (organization#6, FR05).
            "responsible" to setOf(READ, EDIT, MEMBER_READ, MEMBER_MANAGE),
            "consultant" to setOf(READ, EDIT),
            "analyst" to setOf(READ),
        ),
        nav = listOf(
            NavEntry("Organização", "organization", NavArea.CLIENT, resource = "organization"),
            NavEntry("Equipa", "team", NavArea.CLIENT, resource = "member"),
        ),
    )

    companion object {
        const val REGISTER = "organization:register"
        const val READ = "organization:read"
        const val EDIT = "organization:edit"
        const val MEMBER_READ = "member:read"
        const val MEMBER_MANAGE = "member:manage"
        const val INVITATION_ACCEPT = "invitation:accept"
    }
}
