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
}
