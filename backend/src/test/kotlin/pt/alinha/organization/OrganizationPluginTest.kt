package pt.alinha.organization

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrganizationPluginTest {
    @Test
    fun `manifest has the plugin id`() {
        assertEquals("organization", OrganizationPlugin().manifest.id)
    }
}
