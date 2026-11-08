import io.crewmate.app.core.*

fun main() {
    val start = GeoPoint(37.565, 126.978)
    val home = GeoPoint(37.51, 127.12)
    val a = Waypoint("a", "A", GeoPoint(37.55, 127.02))
    val b = Waypoint("b", "B", GeoPoint(37.52, 127.08))
    val c = Waypoint("c", "C", GeoPoint(37.49, 126.98))
    val p = RouteOptimizer.optimize(start, home, listOf(a, b, c))
    check(p.stops.map { it.id }.toSet() == setOf("a", "b", "c"))
    check(p.straightLineKm > 0)
    val permuted = RouteOptimizer.optimize(start, home, listOf(c, a, b))
    check(p.straightLineKm - permuted.straightLineKm < 1e-7)
    check(RouteOptimizer.optimize(start, home, emptyList()).stops.isEmpty())
    check(RouteOptimizer.km(start, start) < 1e-9)
    println("PASS: Kotlin RouteOptimizer 5 assertions")
}
