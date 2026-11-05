package me.chetan.indoornavigation.data

sealed class ParseResult<out T> {
    data class Success<T>(val data: T) : ParseResult<T>()
    data class Error(val message: String) : ParseResult<Nothing>()
}

object ConfigurationParser {

    fun parseGraphText(text: String): ParseResult<Map<GeoLocation, List<GeoLocation>>> {
        val lines = text.lines()
        val nodesMap = mutableMapOf<String, GeoLocation>()
        val adjMap = mutableMapOf<GeoLocation, MutableList<GeoLocation>>()

        for ((index, rawLine) in lines.withIndex()) {
            val lineNum = index + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.contains(",")) {
                // Node line: Name, x, y, z
                val parts = line.split(",").map { it.trim() }
                if (parts.size < 4) {
                    return ParseResult.Error("Line $lineNum: Node entry must have at least 4 fields (Name, X, Y, Z).")
                }
                val name = parts[0]
                if (name.isEmpty()) {
                    return ParseResult.Error("Line $lineNum: Node name cannot be empty.")
                }
                if (nodesMap.containsKey(name)) {
                    return ParseResult.Error("Line $lineNum: Duplicate node name '$name'.")
                }

                val x = parts[1].toDoubleOrNull()
                val y = parts[2].toDoubleOrNull()
                val z = parts[3].toDoubleOrNull()

                if (x == null || y == null || z == null) {
                    return ParseResult.Error("Line $lineNum: Invalid numeric coordinates for node '$name'.")
                }

                val geo = GeoLocation(x, y, z, name)
                nodesMap[name] = geo
                adjMap[geo] = mutableListOf()
            } else if (line.contains("-")) {
                // Edge line: NodeA-NodeB
                val parts = line.split("-").map { it.trim() }
                if (parts.size != 2) {
                    return ParseResult.Error("Line $lineNum: Edge entry must be in format 'NodeA-NodeB'.")
                }
                val nameA = parts[0]
                val nameB = parts[1]

                if (nameA.isEmpty() || nameB.isEmpty()) {
                    return ParseResult.Error("Line $lineNum: Edge contains empty node name.")
                }

                val locA = nodesMap[nameA]
                    ?: return ParseResult.Error("Line $lineNum: Edge refers to unknown node '$nameA'.")
                val locB = nodesMap[nameB]
                    ?: return ParseResult.Error("Line $lineNum: Edge refers to unknown node '$nameB'.")

                if (!adjMap[locA]!!.contains(locB)) {
                    adjMap[locA]!!.add(locB)
                }
                if (!adjMap[locB]!!.contains(locA)) {
                    adjMap[locB]!!.add(locA)
                }
            } else {
                return ParseResult.Error("Line $lineNum: Unrecognized format. Expected 'Name,X,Y,Z' or 'NodeA-NodeB'.")
            }
        }

        if (nodesMap.isEmpty()) {
            return ParseResult.Error("Graph configuration is empty. Please define at least one node.")
        }

        return ParseResult.Success(adjMap)
    }

    fun parseAnchorsText(text: String): ParseResult<Map<String, GeoLocation>> {
        val lines = text.lines()
        val anchorsMap = mutableMapOf<String, GeoLocation>()

        for ((index, rawLine) in lines.withIndex()) {
            val lineNum = index + 1
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            val parts = line.split(",").map { it.trim() }
            if (parts.size < 4) {
                return ParseResult.Error("Line $lineNum: Anchor entry must have at least 4 fields (MAC, X, Y, Z).")
            }

            val mac = parts[0].uppercase()
            if (mac.isEmpty()) {
                return ParseResult.Error("Line $lineNum: MAC address cannot be empty.")
            }
            if (anchorsMap.containsKey(mac)) {
                return ParseResult.Error("Line $lineNum: Duplicate MAC address '$mac'.")
            }

            val x = parts[1].toDoubleOrNull()
            val y = parts[2].toDoubleOrNull()
            val z = parts[3].toDoubleOrNull()

            if (x == null || y == null || z == null) {
                return ParseResult.Error("Line $lineNum: Invalid numeric coordinates for anchor '$mac'.")
            }

            val name = if (parts.size >= 5 && parts[4].isNotEmpty()) parts[4] else mac
            anchorsMap[mac] = GeoLocation(x, y, z, name)
        }

        if (anchorsMap.isEmpty()) {
            return ParseResult.Error("BLE Anchors configuration is empty. Please define at least one anchor.")
        }

        return ParseResult.Success(anchorsMap)
    }
}
