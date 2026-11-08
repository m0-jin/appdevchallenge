package io.crewmate.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Native Kotlin CrewMate driver console.
 * Demo mode works fully offline; LAN mode synchronizes with companion phone browsers.
 * No actual vehicle control, no navigation SDK calls, no driving-state detection.
 */
class MainActivity:Activity() {
    private val bg=Color.rgb(10,19,34)
    private val panel=Color.rgb(21,35,55)
    private val raised=Color.rgb(29,48,72)
    private val accent=Color.rgb(89,223,204)
    private val white=Color.rgb(243,247,255)
    private val muted=Color.rgb(151,172,199)
    private val caution=Color.rgb(255,203,135)
    private val offline=OfflineDriverState()
    private var api:DemoApi?=null
    private var state:JSONObject=offline.snapshot()
    private lateinit var scroll:ScrollView
    private lateinit var content:LinearLayout
    private val executor=Executors.newSingleThreadExecutor()
    private val handler=Handler(Looper.getMainLooper())
    private var busy=false
    private var live=false
    private val poller=object:Runnable{
        override fun run(){
            if(live&&!busy)refreshFromServer()
            handler.postDelayed(this,2500)
        }
    }

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg
        window.navigationBarColor=bg
        scroll=ScrollView(this).apply{isFillViewport=true;setBackgroundColor(bg)}
        content=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(20),dp(22),dp(20),dp(45))
        }
        scroll.addView(content)
        setContentView(scroll)
        render()
        handler.postDelayed(poller,2500)
    }
    override fun onDestroy(){handler.removeCallbacks(poller);executor.shutdownNow();super.onDestroy()}
    private fun dp(value:Int)=(resources.displayMetrics.density*value).toInt()
    private fun background(color:Int,radius:Int=16)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun text(s:String,size:Float=14f,color:Int=white,bold:Boolean=false)=TextView(this).apply{
        text=s;textSize=size;setTextColor(color)
        if(bold)typeface=Typeface.DEFAULT_BOLD
        setLineSpacing(dp(2).toFloat(),1.05f)
    }
    private fun addText(parent:LinearLayout,s:String,size:Float=14f,color:Int=white,bold:Boolean=false,space:Int=0):TextView{
        val t=text(s,size,color,bold)
        parent.addView(t,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(space)})
        return t
    }
    private fun card(parent:LinearLayout):LinearLayout{
        val c=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            background=background(panel)
            setPadding(dp(18),dp(17),dp(18),dp(18))
        }
        parent.addView(c,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(14)})
        return c
    }
    private fun row(parent:LinearLayout):LinearLayout{
        val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        parent.addView(r,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
        return r
    }
    private fun button(label:String,primary:Boolean=true,click:()->Unit):Button{
        return Button(this).apply{
            text=label;textSize=12f;isAllCaps=false
            setTextColor(if(primary) bg else white)
            background=background(if(primary)accent else raised,10)
            setPadding(dp(12),0,dp(12),0)
            setOnClickListener{click()}
            minHeight=dp(44)
        }
    }
    private fun addButtonRow(parent:LinearLayout,vararg buttons:Button){
        val r=row(parent)
        buttons.forEachIndexed { index,b ->
            r.addView(b,LinearLayout.LayoutParams(0,dp(44),1f).apply{if(index>0)leftMargin=dp(8)})
        }
    }
    private fun notice(msg:String){Toast.makeText(this,msg,Toast.LENGTH_LONG).show()}

    private fun render(){
        if(isFinishing||isDestroyed)return
        val oldScroll=scroll.scrollY
        content.removeAllViews()
        val header=row(content)
        header.addView(text("◆  CREWMATE",19f,accent,true),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(text(if(live) "● LIVE LAN" else "● DEMO MODE",12f,if(live)accent else caution,true))
        addText(content,"함께 타고, 각자의 목적지까지.",25f,white,true,18)
        addText(content,"동승자가 보낸 목적지 변경 요청을 확인하고, 승인한 내용만 공동 귀가 계획에 반영합니다.",13f,muted,false,8)

        val action=card(content)
        addText(action,"차량용 운전자 콘솔",16f,white,true)
        addText(action,if(live) "휴대폰 브라우저와 실제 LAN으로 연결 중 · 운전자 승인 모드"
            else "오프라인 데모 · 화면과 승인 흐름을 서버 없이 테스트할 수 있어요.",12f,muted,false,5)
        addButtonRow(action,
            button(if(live)"연결 해제" else "LAN 서버 연결",true){if(live){live=false;api=null;state=offline.snapshot();render()}else connectDialog()},
            button("디자인 시안",false){startActivity(Intent(this,PreviewActivity::class.java))})
        if(live){
            addText(action,"서버: ${api?.baseUrl}",11f,muted,false,7)
            addText(action,"동승자 초대 코드: ${state.optString("code","-")}",12f,accent,true,4)
            addButtonRow(action,button("초대 안내",false){inviteDialog()},button("새로고침",false){refreshFromServer()})
        }else{
            addButtonRow(action,
                button("테스트 요청 생성",false){offline.generateRequest();state=offline.snapshot();render()},
                button("데모 초기화",false){offline.reset();state=offline.snapshot();render()})
        }

        val p=state.optJSONArray("passengers")?:JSONArray()
        val active=(0 until p.length()).map{p.getJSONObject(it)}.filter{it.optBoolean("active")}
        val route=state.optJSONObject("route")?:JSONObject()
        val stops=route.optJSONArray("stops")?:JSONArray()
        val overview=card(content)
        addText(overview,"오늘의 공동 귀가",16f,white,true)
        addText(overview,"탑승자 ${active.size}명  ·  경유 ${stops.length()}곳  ·  약 ${String.format(Locale.KOREA,"%.1f",route.optDouble("approxKm",0.0))} km",14f,accent,true,8)
        addText(overview,"※ 직선거리 기반 비교값이며 실제 도로 주행 거리·시간은 아닙니다.",11f,muted,false,6)
        addText(overview,"출발  ·  ${state.optJSONObject("origin")?.optString("name")?:"시청역"}",13f,white,true,16)
        for(i in 0 until stops.length()){
            val stop=stops.getJSONObject(i)
            addText(overview,"  ${i+1}.  ${stop.optString("label")}  —  ${stop.optJSONObject("location")?.optString("name")}",14f,white,false,11)
        }
        addText(overview,"도착  ·  ${state.optJSONObject("driverHome")?.optString("name")?:"운전자 귀가 지점"}",13f,accent,true,14)

        val reqs=state.optJSONArray("requests")?:JSONArray()
        val pending=(0 until reqs.length()).map{reqs.getJSONObject(it)}.filter{it.optString("status")=="pending"}
        val approvals=card(content)
        addText(approvals,"동승자 변경 요청  ·  ${pending.size}건 대기",16f,white,true)
        if(pending.isEmpty())addText(approvals,"승인 대기 중인 요청이 없어요.",13f,muted,false,12)
        pending.forEach{r->
            val person=r.optString("passengerName","동승자")
            val type=if(r.optString("type")=="waypoint")"경유지 추가" else "하차 목적지 변경"
            val place=r.optJSONObject("location")?.optString("name","-")?:"-"
            addText(approvals,"$person  ·  $type",13f,muted,true,15)
            addText(approvals,place,17f,white,true,3)
            val id=r.optString("id")
            addButtonRow(approvals,
                button("승인",true){confirmApproval(id,true)},
                button("거절",false){confirmApproval(id,false)})
        }
        val list=card(content)
        addText(list,"현재 탑승자와 권한",16f,white,true)
        if(active.isEmpty())addText(list,"모든 동승자가 하차했어요. 세션을 종료할 수 있어요.",13f,muted,false,12)
        active.forEach{pax->
            val id=pax.optString("id")
            addText(list,"●  ${pax.optString("name")}",16f,white,true,17)
            addText(list,"목적지: ${pax.optJSONObject("destination")?.optString("name")}",12f,muted,false,3)
            addButtonRow(list,
                button("경유지 제안 ${if(pax.optBoolean("canSuggestWaypoint"))"ON" else "OFF"}",false){
                    changePermission(id,"canSuggestWaypoint",!pax.optBoolean("canSuggestWaypoint"))
                },
                button("음악 ${if(pax.optBoolean("canManageMusic"))"ON" else "OFF"}",false){
                    changePermission(id,"canManageMusic",!pax.optBoolean("canManageMusic"))
                })
            val alightBtn=button("${pax.optString("name")} 하차 완료 · 접근 권한 회수",false){
                AlertDialog.Builder(this).setTitle("하차 완료 처리")
                    .setMessage("실제 하차를 확인했나요? 처리하면 해당 동승자의 세션 권한이 회수됩니다.")
                    .setPositiveButton("하차 완료") { _,_->perform({offline.alight(id)},{it.alight(id)})}
                    .setNegativeButton("취소",null).show()
            }
            list.addView(alightBtn,LinearLayout.LayoutParams(-1,dp(43)).apply{topMargin=dp(6)})
        }
        val foot=card(content)
        addText(foot,"안전 및 개발 상태",15f,white,true)
        addText(foot,"• 개발용 시연 데이터만 사용해 주세요.\n• 주행 중 운전자에게 복잡한 조작을 요청하지 마세요.\n• 실제 차량 위치, 경로 탐색, Pleos NaviHelper 제어는 아직 연결되지 않았습니다.\n• LAN 서버는 신뢰할 수 있는 로컬 네트워크에서만 실행하세요.",12f,muted,false,10)
        if(live){
            val resetBtn=button("서버 데모 세션 초기화",false){
                AlertDialog.Builder(this).setTitle("세션 초기화")
                    .setMessage("기존 동승자 연결과 요청이 모두 초기화됩니다.")
                    .setPositiveButton("초기화"){_,_->perform({offline.reset()},{it.reset()})}
                    .setNegativeButton("취소",null).show()
            }
            foot.addView(resetBtn,LinearLayout.LayoutParams(-1,dp(44)).apply{topMargin=dp(9)})
        }
        scroll.post{scroll.scrollTo(0,oldScroll)}
    }
    private fun confirmApproval(id:String,approved:Boolean){
        AlertDialog.Builder(this).setTitle(if(approved)"변경 요청 승인" else "변경 요청 거절")
            .setMessage(if(approved)"선택한 변경을 공동 귀가 계획에 반영할까요? 안전한 정차 상태에서 진행하세요."
                        else "이 요청을 거절할까요?")
            .setPositiveButton(if(approved)"승인" else "거절"){_,_->perform({offline.decide(id,approved)},{it.approve(id,approved)})}
            .setNegativeButton("취소",null).show()
    }
    private fun changePermission(id:String,key:String,enabled:Boolean){
        perform({offline.permission(id,key,enabled)},{it.permission(id,key,enabled)})
    }
    private fun perform(localAction:()->Unit,remoteAction:(DemoApi)->Unit){
        val current=api
        if(!live||current==null){localAction();state=offline.snapshot();render();return}
        busy=true
        executor.execute {
            try {remoteAction(current);val fresh=current.snapshot();runOnUiThread{state=fresh;busy=false;render()}}
            catch(e:Exception){runOnUiThread{busy=false;notice("연결 오류: ${e.message}")}}
        }
    }
    private fun refreshFromServer(){
        val current=api?:return
        if(busy)return
        busy=true
        executor.execute {
            try{val fresh=current.snapshot();runOnUiThread{busy=false;if(live&&api===current){state=fresh;render()}}}
            catch(e:Exception){runOnUiThread{busy=false;notice("서버 통신 확인: ${e.message}")}}
        }
    }
    private fun connectDialog(){
        val prefs=getSharedPreferences("demo_lan",MODE_PRIVATE)
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(4),dp(22),0)}
        val url=EditText(this).apply{
            hint="http://10.0.2.2:8787";setSingleLine(true)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString("url","http://10.0.2.2:8787"))
        }
        val key=EditText(this).apply{
            hint="서버 터미널의 Driver key";setSingleLine(true)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("key",""))
        }
        box.addView(text("서버 주소",13f,Color.DKGRAY))
        box.addView(url)
        box.addView(text("운전자 인증 키",13f,Color.DKGRAY))
        box.addView(key)
        AlertDialog.Builder(this).setTitle("로컬 LAN 서버 연결")
            .setMessage("컴퓨터에서 node server.mjs를 실행하세요.\nAndroid 기본 에뮬레이터에서는 10.0.2.2가 호스트 컴퓨터입니다.")
            .setView(box)
            .setPositiveButton("연결"){_,_->
                val u=url.text.toString().trim().trimEnd('/')
                val k=key.text.toString().trim()
                if(!u.startsWith("http://")&&!u.startsWith("https://")){notice("http:// 주소를 입력하세요");return@setPositiveButton}
                prefs.edit().putString("url",u).putString("key",k).apply()
                val client=DemoApi(u,k)
                busy=true
                executor.execute{
                    try{val fresh=client.snapshot();runOnUiThread{api=client;live=true;state=fresh;busy=false;render();notice("LAN 서버에 연결됐어요")}}
                    catch(e:Exception){runOnUiThread{busy=false;notice("연결 실패: ${e.message}")}}
                }
            }.setNegativeButton("취소",null).show()
    }
    private fun inviteDialog(){
        val client=api?:return
        AlertDialog.Builder(this).setTitle("동승자 초대")
            .setMessage("동승자 휴대폰에서 같은 Wi-Fi에 연결한 뒤 아래 주소로 접속해 주세요.\n\n"+
                "http://<컴퓨터의 LAN IP>:8787/?code=${state.optString("code","CREW26")}\n\n"+
                "에뮬레이터 주소 ${client.baseUrl}는 휴대폰에서 접근할 수 없을 수 있습니다. " +
                "운전자 인증 키는 동승자에게 공유하지 마세요.")
            .setPositiveButton("확인",null).show()
    }
}
