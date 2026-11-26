package pt.alinha.organization

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class OrganizationPluginTest {
    private val manifest = OrganizationPlugin().manifest
    private val pluginYaml = File("../plugin.yaml").readText()

    @Test
    fun `manifest has the plugin id`() {
        assertThat(manifest.id).isEqualTo("organization")
    }

    @Test
    fun `plugin yaml lists the same events and permissions as the manifest`() {
        manifest.publishes.forEach { assertThat(pluginYaml).contains(it) }
        manifest.permissions.forEach { assertThat(pluginYaml).contains("- $it") }
    }

    @Test
    fun `analysts only read and only a registrant registers`() {
        assertThat(manifest.rolePermissions["analyst"]).containsExactly(OrganizationPlugin.READ)
        manifest.rolePermissions.filterKeys { it != "registrant" }.values
            .forEach { assertThat(it).doesNotContain(OrganizationPlugin.REGISTER) }
    }

    @Test
    fun `only the Designated Officer sees and manages the team, and only a new account accepts an invitation`() {
        manifest.rolePermissions.forEach { (role, granted) ->
            if (role != "responsible") assertThat(granted).doesNotContain(OrganizationPlugin.MEMBER_READ, OrganizationPlugin.MEMBER_MANAGE)
            if (role != "registrant") assertThat(granted).doesNotContain(OrganizationPlugin.INVITATION_ACCEPT)
        }
        assertThat(manifest.rolePermissions["responsible"]).contains(OrganizationPlugin.MEMBER_READ, OrganizationPlugin.MEMBER_MANAGE)
    }

    @Test
    fun `plugin yaml lists the same menu entries as the manifest`() {
        assertThat(manifest.nav).isNotEmpty
        manifest.nav.forEach { entry ->
            assertThat(pluginYaml).contains("label: \"${entry.label}\"")
            assertThat(pluginYaml).contains("path: ${entry.path}")
            assertThat(pluginYaml).contains("area: ${entry.area.name.lowercase()}")
            entry.resource?.let { assertThat(pluginYaml).contains("resource: $it") }
        }
    }

    @Test
    fun `every menu entry needs a permission the plugin declares`() {
        manifest.nav.forEach { entry ->
            val resource = requireNotNull(entry.resource) { "Menu entry '${entry.path}' must be gated by a resource" }
            assertThat(manifest.permissions).contains("$resource:${entry.action}")
        }
    }
}
