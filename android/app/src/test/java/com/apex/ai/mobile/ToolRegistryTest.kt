package com.apex.ai.mobile

import com.apex.ai.mobile.registry.ToolMetadata
import com.apex.ai.mobile.registry.ToolRegistry
import com.apex.ai.mobile.registry.ToolRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolRegistryTest {

    private lateinit var registry: ToolRegistry

    @Before
    fun setUp() {
        registry = ToolRegistry()
    }

    @Test
    fun testEveryRegisteredToolHasUniqueName() {
        val allTools = registry.getAllTools()
        val names = allTools.map { it.name }
        val uniqueNames = names.toSet()
        assertEquals("Duplicate tool names found in registry", uniqueNames.size, names.size)
        assertTrue("Expected at least 25 registered tools", allTools.size >= 25)
    }

    @Test
    fun testCoreCategoriesPresent() {
        val categories = registry.getAllTools().map { it.category }.toSet()
        assertTrue("App Control missing", categories.contains("App Control"))
        assertTrue("Communication missing", categories.contains("Communication"))
        assertTrue("Camera & Media missing", categories.contains("Camera & Media"))
        assertTrue("App Management missing", categories.contains("App Management"))
        assertTrue("Files missing", categories.contains("Files"))
        assertTrue("Device missing", categories.contains("Device"))
        assertTrue("Screen Interaction missing", categories.contains("Screen Interaction"))
        assertTrue("Restricted category missing", categories.contains("Restricted"))
    }

    @Test
    fun testRestrictedOperationsExistAndMarkedRestricted() {
        val silentInstall = registry.getTool("silent_install_apk")
        assertNotNull(silentInstall)
        assertEquals(ToolRisk.RESTRICTED, silentInstall?.risk)

        val covertRecord = registry.getTool("covert_record_audio")
        assertNotNull(covertRecord)
        assertEquals(ToolRisk.RESTRICTED, covertRecord?.risk)
    }

    @Test
    fun testUnknownToolReturnsNull() {
        val unknown = registry.getTool("non_existent_exploit_tool")
        assertEquals(null, unknown)
    }
}
