package io.crewmate.app

import io.crewmate.app.core.GeoPoint
import io.crewmate.app.core.RouteOptimizer
import io.crewmate.app.core.Waypoint
import org.json.JSONArray
import org.json.JSONObject

private data class Location(val id:String,val name:String,val lat:Double,val lng:Double) {
    fun json(): JSONObject = JSONObject().put("id",id).put("name",name).put("lat",lat).put("lng",lng)
}

/** Offline state with the same JSON schema as the LAN demo server. */
class OfflineDriverState {
    private val locations = mapOf(
        "SADANG" to Location("SADANG","사당역",37.4766,126.9816),
        "GANGNAM" to Location("GANGNAM","강남역",37.4979,127.0276),
        "JAMSIL" to Location("JAMSIL","잠실역",37.5133,127.1002),
        "HONGDAE" to Location("HONGDAE","홍대입구역",37.5572,126.9252),
        "SEONGSU" to Location("SEONGSU","성수역",37.5446,127.0558),
        "YEOUIDO" to Location("YEOUIDO","여의도역",37.5217,126.9244)
    )
    private data class Rider(val id:String,val name:String,var destination:String,var active:Boolean=true,
                             var waypoint:Boolean=true,var music:Boolean=false)
    private data class RideRequest(val id:String,val passengerId:String,val type:String,val destination:String,
                                   var status:String="pending")
    private val riders=mutableListOf<Rider>()
    private val requests=mutableListOf<RideRequest>()
    private val extraStops=mutableMapOf<String,String>()
    private var version=0
    private val origin=GeoPoint(37.5657,126.9769)
    private val final=GeoPoint(37.5484,127.0662)

    init { reset() }

    fun reset() {
        riders.clear();riders.addAll(listOf(
            Rider("p1","지민","SADANG",music=true),
            Rider("p2","서연","GANGNAM"),
            Rider("p3","도윤","JAMSIL",music=true)
        ))
        extraStops.clear()
        requests.clear();requests.add(RideRequest("sample1","p2","destination","SEONGSU"));version++
    }
    fun generateRequest() {
        val rider=riders.firstOrNull { it.active } ?:return
        requests.add(RideRequest("demo${++version}",rider.id,"waypoint","HONGDAE"))
    }
    fun decide(id:String,approved:Boolean) {
        val req=requests.firstOrNull { it.id==id&&it.status=="pending" }?:return
        val rider=riders.firstOrNull{it.id==req.passengerId&&it.active}?:return
        req.status=if(approved) "approved" else "rejected"
        if(approved&&req.type=="destination")rider.destination=req.destination
        if(approved&&req.type=="waypoint")extraStops[rider.id]=req.destination
        version++
    }
    fun alight(id:String) {
        val rider=riders.firstOrNull{it.id==id&&it.active}?:return
        rider.active=false;rider.waypoint=false;rider.music=false
        extraStops.remove(id)
        requests.filter{it.passengerId==id&&it.status=="pending"}.forEach{it.status="revoked"};version++
    }
    fun permission(id:String,permission:String,enabled:Boolean) {
        val rider=riders.firstOrNull{it.id==id&&it.active}?:return
        if(permission=="canSuggestWaypoint")rider.waypoint=enabled
        if(permission=="canManageMusic")rider.music=enabled
        version++
    }
    fun snapshot():JSONObject {
        val p=JSONArray();riders.forEach{r->p.put(JSONObject()
            .put("id",r.id).put("name",r.name).put("active",r.active)
            .put("destination",locations[r.destination]!!.json())
            .put("canSuggestWaypoint",r.waypoint).put("canManageMusic",r.music))}
        val reqs=JSONArray();requests.forEach{r->reqs.put(JSONObject()
            .put("id",r.id).put("passengerId",r.passengerId)
            .put("passengerName",riders.firstOrNull{it.id==r.passengerId}?.name?:"-")
            .put("type",r.type).put("status",r.status)
            .put("location",locations[r.destination]!!.json()))}
        val detours=riders.filter{it.active && extraStops.containsKey(it.id)}.map{r->
            val loc=locations[extraStops[r.id]]!!
            Waypoint(r.id+"-via","${r.name} 제안 경유",GeoPoint(loc.lat,loc.lng))
        }
        val dropoffs=riders.filter{it.active}.map{r->
            val loc=locations[r.destination]!!
            Waypoint(r.id,"${r.name} 하차",GeoPoint(loc.lat,loc.lng))
        }
        val ordered=RouteOptimizer.optimize(origin,final,detours).stops + RouteOptimizer.optimize(origin,final,dropoffs).stops
        var here=origin
        var total=0.0
        val stopArray=JSONArray();ordered.forEach{waypoint->
            val rider=riders.first{it.id==waypoint.id.removeSuffix("-via")}
            val loc=if(waypoint.id.endsWith("-via"))locations[extraStops[rider.id]]!! else locations[rider.destination]!!
            total+=RouteOptimizer.km(here,waypoint.location);here=waypoint.location
            stopArray.put(JSONObject().put("id",waypoint.id).put("passengerId",rider.id)
                .put("label",waypoint.title).put("location",loc.json()))
        }
        total+=RouteOptimizer.km(here,final)
        return JSONObject()
            .put("version",version).put("code","DEMO")
            .put("origin",JSONObject().put("name","시청역 · 출발 예시"))
            .put("driverHome",JSONObject().put("name","운전자 귀가 지점 · 예시"))
            .put("passengers",p).put("requests",reqs)
            .put("route",JSONObject().put("stops",stopArray)
                .put("approxKm",Math.round(total*10.0)/10.0)
                .put("calculation","직선거리 근사값 · 실제 도로 경로가 아닙니다"))
    }
}
