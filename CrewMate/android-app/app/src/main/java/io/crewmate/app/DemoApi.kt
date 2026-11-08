package io.crewmate.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Local LAN demo only. Driver key is never sent to passenger web pages. */
class DemoApi(val baseUrl:String,val driverKey:String) {
    private fun request(path:String,body:JSONObject?=null):JSONObject {
        val url=URL(baseUrl.trimEnd('/')+path)
        if(url.protocol!="http" && url.protocol!="https")error("http:// 또는 https:// 주소를 사용하세요")
        val c=url.openConnection() as HttpURLConnection
        c.connectTimeout=4000;c.readTimeout=4000
        c.setRequestProperty("X-Driver-Key",driverKey)
        c.setRequestProperty("Accept","application/json")
        if(body!=null){
            c.requestMethod="POST";c.doOutput=true
            c.setRequestProperty("Content-Type","application/json; charset=utf-8")
            c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
        }
        return try {
            val status=c.responseCode
            val data=(if(status in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:"{}"
            val json=JSONObject(data)
            if(status !in 200..299) error(json.optString("error","서버 응답 오류 ($status)"))
            json
        } finally {c.disconnect()}
    }
    fun snapshot():JSONObject=request("/api/state")
    fun approve(id:String,approved:Boolean)=request("/api/requests/$id/decision",JSONObject()
        .put("decision",if(approved)"approved" else "rejected"))
    fun alight(id:String)=request("/api/passengers/$id/alight",JSONObject())
    fun permission(id:String,key:String,enabled:Boolean)=request("/api/passengers/$id/permission",JSONObject()
        .put("permission",key).put("enabled",enabled))
    fun reset()=request("/api/session/reset",JSONObject())
}
