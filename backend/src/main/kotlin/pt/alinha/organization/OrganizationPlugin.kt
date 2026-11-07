package pt.alinha.organization

import org.springframework.stereotype.Component
import pt.alinha.pluginapi.AlinhaPlugin
import pt.alinha.pluginapi.PluginKind
import pt.alinha.pluginapi.PluginManifest

/** Registo do plugin no Module Registry do Core. Manter igual ao plugin.yaml. */
@Component
class OrganizationPlugin : AlinhaPlugin {
    override val manifest = PluginManifest(
        id = "organization",
        name = "Organization",
        kind = PluginKind.FEATURE,
        version = "0.1.0",
        publishes = setOf(),
        consumes = setOf(),
        permissions = setOf(),
    )
}
