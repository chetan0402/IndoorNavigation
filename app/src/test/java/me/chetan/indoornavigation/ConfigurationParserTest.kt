package me.chetan.indoornavigation

import me.chetan.indoornavigation.data.ConfigurationParser
import me.chetan.indoornavigation.data.GeoLocation
import me.chetan.indoornavigation.data.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigurationParserTest {

    @Test
    fun `test parseGraphText valid nodes and edges`() {
        val input = """
            A, 10.0, 20.0, 0.0
            B, 30.0, 40.0, 5.0
            
            A-B
        """.trimIndent()

        val result = ConfigurationParser.parseGraphText(input)
        assertTrue(result is ParseResult.Success)

        val graph = (result as ParseResult.Success).data
        val nodeA = GeoLocation(10.0, 20.0, 0.0, "A")
        val nodeB = GeoLocation(30.0, 40.0, 5.0, "B")

        assertEquals(2, graph.size)
        assertEquals(listOf(nodeB), graph[nodeA])
        assertEquals(listOf(nodeA), graph[nodeB])
    }

    @Test
    fun `test parseGraphText invalid coordinate returns error`() {
        val input = """
            A, 10.0, invalid, 0.0
        """.trimIndent()

        val result = ConfigurationParser.parseGraphText(input)
        assertTrue(result is ParseResult.Error)
        val error = (result as ParseResult.Error).message
        assertTrue(error.contains("Invalid numeric coordinates"))
    }

    @Test
    fun `test parseGraphText unknown edge node returns error`() {
        val input = """
            A, 10.0, 20.0, 0.0
            A-Z
        """.trimIndent()

        val result = ConfigurationParser.parseGraphText(input)
        assertTrue(result is ParseResult.Error)
        val error = (result as ParseResult.Error).message
        assertTrue(error.contains("unknown node 'Z'"))
    }

    @Test
    fun `test parseAnchorsText valid anchors`() {
        val input = """
            2D:7E:1A:02:3D:21, 0.0, 0.0, 0.0, BLE1
            55:6D:EA:22:2C:71, 0.0, 13.8, 0.0, BLE2
        """.trimIndent()

        val result = ConfigurationParser.parseAnchorsText(input)
        assertTrue(result is ParseResult.Success)

        val anchors = (result as ParseResult.Success).data
        assertEquals(2, anchors.size)
        assertEquals(GeoLocation(0.0, 0.0, 0.0, "BLE1"), anchors["2D:7E:1A:02:3D:21"])
        assertEquals(GeoLocation(0.0, 13.8, 0.0, "BLE2"), anchors["55:6D:EA:22:2C:71"])
    }

    @Test
    fun `test parseAnchorsText duplicate MAC address returns error`() {
        val input = """
            2D:7E:1A:02:3D:21, 0.0, 0.0, 0.0, BLE1
            2D:7E:1A:02:3D:21, 5.0, 5.0, 0.0, BLE1_DUP
        """.trimIndent()

        val result = ConfigurationParser.parseAnchorsText(input)
        assertTrue(result is ParseResult.Error)
        val error = (result as ParseResult.Error).message
        assertTrue(error.contains("Duplicate MAC address"))
    }
}
