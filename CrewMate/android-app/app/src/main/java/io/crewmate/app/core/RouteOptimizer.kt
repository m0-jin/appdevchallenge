package io.crewmate.app.core

import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double)
data class Waypoint(val id: String, val title: String, val location: GeoPoint)

data class RoutePlan(val stops: List<Waypoint>, val straightLineKm: Double)

/**
 * Small-group demo route solver. Start and final destination are fixed.
 * This uses haversine straight-line distance, NOT real-road travel time.
 * For > 8 stops, a deterministic nearest-neighbor fallback avoids factorial work.
 */
object RouteOptimizer {
    fun km(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0088
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    fun optimize(start: GeoPoint, last: GeoPoint, stops: List<Waypoint>): RoutePlan {
        if (stops.isEmpty()) return RoutePlan(emptyList(), km(start, last))
        require(stops.map { it.id }.toSet().size == stops.size) { "Duplicate waypoint IDs" }
        val best = if (stops.size <= 8) bruteForce(start, last, stops) else greedy(start, stops)
        return RoutePlan(best, length(start, last, best))
    }

    private fun length(start: GeoPoint, last: GeoPoint, stops: List<Waypoint>): Double {
        var curr = start
        var total = 0.0
        stops.forEach { total += km(curr, it.location); curr = it.location }
        return total + km(curr, last)
    }

    private fun greedy(start: GeoPoint, stops: List<Waypoint>): List<Waypoint> {
        val remaining = stops.toMutableList()
        val route = mutableListOf<Waypoint>()
        var current = start
        while (remaining.isNotEmpty()) {
            val next = remaining.minWithOrNull(compareBy<Waypoint>({ km(current, it.location) }, { it.id }))!!
            route += next
            remaining.remove(next)
            current = next.location
        }
        return route
    }

    private fun bruteForce(start: GeoPoint, last: GeoPoint, stops: List<Waypoint>): List<Waypoint> {
        var bestDistance = Double.POSITIVE_INFINITY
        var bestOrder = emptyList<Waypoint>()
        val chosen = ArrayList<Waypoint>()
        val used = BooleanArray(stops.size)
        fun dfs(current: GeoPoint, subtotal: Double) {
            if (subtotal >= bestDistance) return
            if (chosen.size == stops.size) {
                val score = subtotal + km(current, last)
                if (score < bestDistance) { bestDistance = score; bestOrder = chosen.toList() }
                return
            }
            for (i in stops.indices) {
                if (used[i]) continue
                used[i] = true
                chosen += stops[i]
                dfs(stops[i].location, subtotal + km(current, stops[i].location))
                chosen.removeAt(chosen.lastIndex)
                used[i] = false
            }
        }
        dfs(start, 0.0)
        return bestOrder
    }
}
