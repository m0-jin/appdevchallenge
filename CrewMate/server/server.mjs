import http from 'node:http';
import crypto from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const locations = Object.freeze({
  GANGNAM: { id: 'GANGNAM', name: '강남역', lat: 37.4979, lng: 127.0276 },
  JAMSIL: { id: 'JAMSIL', name: '잠실역', lat: 37.5133, lng: 127.1002 },
  SADANG: { id: 'SADANG', name: '사당역', lat: 37.4766, lng: 126.9816 },
  HONGDAE: { id: 'HONGDAE', name: '홍대입구역', lat: 37.5572, lng: 126.9252 },
  YEOUIDO: { id: 'YEOUIDO', name: '여의도역', lat: 37.5217, lng: 126.9244 },
  SEOUL: { id: 'SEOUL', name: '서울역', lat: 37.5546, lng: 126.9706 },
  SEONGSU: { id: 'SEONGSU', name: '성수역', lat: 37.5446, lng: 127.0558 },
  SINCHON: { id: 'SINCHON', name: '신촌역', lat: 37.5551, lng: 126.9368 },
});
const origin = { name: '시청역 · 출발 예시', lat: 37.5657, lng: 126.9769 };
const driverHome = { name: '운전자 귀가 지점 · 예시', lat: 37.5484, lng: 127.0662 };
const coordinates = p => [p.lat, p.lng];
function haversine(a, b) {
  const toRad = x => x * Math.PI / 180;
  const [p1, l1] = coordinates(a).map(toRad);
  const [p2, l2] = coordinates(b).map(toRad);
  const dLat = p2-p1; const dLng = l2-l1;
  const x = Math.sin(dLat/2)**2 + Math.cos(p1)*Math.cos(p2)*Math.sin(dLng/2)**2;
  return 6371.0088 * 2 * Math.asin(Math.min(1, Math.sqrt(x)));
}
function routeCost(points) {
  let from = origin; let total = 0;
  for (const p of points) { total += haversine(from, p.location); from = p.location; }
  return total + haversine(from, driverHome);
}
function optimizeStops(stops) {
  const toRoute = stops.slice();
  if (toRoute.length > 8) return { stops: toRoute, approxKm: routeCost(toRoute) };
  let best = []; let bestDistance = Infinity;
  function visit(current, remaining, d) {
    if (d >= bestDistance) return;
    if (remaining.length === 0) {
      const total = d + haversine(current, driverHome);
      if (total < bestDistance) { bestDistance = total; best = chosen.slice(); }
      return;
    }
    for (let i=0;i<remaining.length;i++) {
      const p = remaining[i]; const delta = haversine(current, p.location);
      chosen.push(p); visit(p.location, remaining.filter((_,j)=>j!==i), d+delta); chosen.pop();
    }
  }
  const chosen = []; visit(origin, toRoute, 0);
  return { stops: best, approxKm: Math.round(bestDistance * 10) / 10 };
}

export function makeStore({ code='CREW26', driverKey=crypto.randomBytes(16).toString('hex') } = {}) {
  let passengers; let requests; let version;
  function reset() {
    passengers = [
      { id: 'p1', name: '지민', destination: 'SADANG', active: true, canSuggestWaypoint: true, canManageMusic: true, token: null },
      { id: 'p2', name: '서연', destination: 'GANGNAM', active: true, canSuggestWaypoint: true, canManageMusic: false, token: null },
      { id: 'p3', name: '도윤', destination: 'JAMSIL', active: true, canSuggestWaypoint: true, canManageMusic: true, token: null },
    ];
    requests = [{ id: 'sample1', passengerId:'p2', type:'destination', destination:'SEONGSU', status:'pending', createdAt:new Date().toISOString() }];
    version = (version ?? 0) + 1;
  }
  reset();
  const findPassenger = id => passengers.find(p=>p.id===id);
  const cleanPassenger = p => ({id:p.id,name:p.name,active:p.active,destination:locations[p.destination],canSuggestWaypoint:p.canSuggestWaypoint,canManageMusic:p.canManageMusic});
  function snapshot() {
    const active = passengers.filter(p=>p.active);
    // Approved detours always precede drop-offs. This is a *demo approximation*, not road routing.
    const detours = active.filter(p=>p.extraStop).map(p=>({id:p.id+'-via',passengerId:p.id,label:p.name+' 제안 경유',location:locations[p.extraStop]}));
    const dropoffs = active.map(p => ({id:p.id,passengerId:p.id,label:p.name+' 하차',location:locations[p.destination]}));
    const sortedDetours = optimizeStops(detours).stops;
    const sortedDropoffs = optimizeStops(dropoffs).stops;
    const combined = [...sortedDetours,...sortedDropoffs];
    const sorted = {stops:combined,approxKm:Math.round(routeCost(combined)*10)/10};
    return { version, code, origin, driverHome, passengers:passengers.map(cleanPassenger), requests:requests.map(r=>({
      ...r, passengerName:findPassenger(r.passengerId)?.name ?? '알 수 없음', location: locations[r.destination],
    })), route:{stops:sorted.stops,approxKm:sorted.approxKm,calculation:'직선거리 근사값 · 도로 경로가 아닙니다'},
    serverTime:new Date().toISOString() };
  }
  function joinSession(name, submittedCode) {
    if (submittedCode !== code) throw bad(403,'초대 코드가 올바르지 않습니다.');
    const trimmed = typeof name === 'string' ? name.trim() : '';
    if (trimmed.length < 1 || trimmed.length > 20) throw bad(400,'이름은 1~20자로 입력하세요.');
    if (passengers.filter(p=>p.active).length >= 8) throw bad(409,'최대 8명까지 참여할 수 있습니다.');
    const id = crypto.randomUUID();const token=crypto.randomBytes(24).toString('hex');
    passengers.push({id,name:trimmed,destination:'SEOUL',active:true,canSuggestWaypoint:true,canManageMusic:false,token});
    version++;
    return { token, passengerId:id };
  }
  function getByToken(token) {
    const p=passengers.find(p=>p.active&&p.token&&p.token===token);
    if(!p) throw bad(401,'유효한 동승자 세션이 없습니다.');
    return p;
  }
  function requestChange(token, type, destination) {
    const p=getByToken(token);
    if (type!=='destination'&&type!=='waypoint') throw bad(400,'지원하지 않는 요청입니다.');
    if(type==='waypoint'&&!p.canSuggestWaypoint) throw bad(403,'경유지 제안 권한이 없습니다.');
    if(!locations[destination]) throw bad(400,'목록에서 목적지를 선택하세요.');
    const existing=requests.filter(r=>r.passengerId===p.id&&r.status==='pending');
    if(existing.length>=3) throw bad(429,'대기 중인 요청은 3개까지 가능합니다.');
    const req={id:crypto.randomUUID(),passengerId:p.id,type,destination,status:'pending',createdAt:new Date().toISOString()};
    requests.push(req);version++;return req;
  }
  function decide(id,decision) {
    if(!['approved','rejected'].includes(decision)) throw bad(400,'승인 또는 거절을 선택하세요.');
    const req=requests.find(r=>r.id===id);
    if(!req) throw bad(404,'요청을 찾을 수 없습니다.');
    if(req.status!=='pending') throw bad(409,'이미 처리된 요청입니다.');
    const p=findPassenger(req.passengerId);
    if(!p?.active) throw bad(409,'이미 하차한 동승자의 요청입니다.');
    req.status=decision;
    if(decision==='approved') {
      if(req.type==='destination') p.destination=req.destination;
      else {
        // Waypoint demo: treat as another waypoint tied to passenger; no real navigation integration.
        p.extraStop=req.destination;
      }
    }
    version++;
    return req;
  }
  function markAlight(id) {
    const p=findPassenger(id);
    if(!p) throw bad(404,'동승자를 찾을 수 없습니다.');
    if(!p.active) throw bad(409,'이미 하차한 동승자입니다.');
    p.active=false;p.token=null;p.canSuggestWaypoint=false;p.canManageMusic=false;
    requests.filter(r=>r.passengerId===id&&r.status==='pending').forEach(r=>r.status='revoked');
    version++;return cleanPassenger(p);
  }
  function changePermission(id,permission,enabled) {
    const p=findPassenger(id);
    if(!p?.active) throw bad(404,'탑승 중인 동승자를 찾을 수 없습니다.');
    if(!['canSuggestWaypoint','canManageMusic'].includes(permission)||typeof enabled!=='boolean')throw bad(400,'권한 입력이 올바르지 않습니다.');
    p[permission]=enabled;version++;return cleanPassenger(p);
  }
  function resetSession() { reset(); return snapshot(); }
  return {
    code,driverKey,locations,snapshot,joinSession,getByToken,requestChange,decide,markAlight,changePermission,resetSession,
    self(token){const p=getByToken(token);return {me:cleanPassenger(p),code,requests:requests.filter(r=>r.passengerId===p.id).map(r=>({...r,location:locations[r.destination]}))};}
  };
}
function bad(status,message){const err=new Error(message);err.status=status;return err;}
function send(res,status,value){const body=JSON.stringify(value);res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(body);}
async function bodyJson(req){let body='';for await(const c of req){body+=c;if(body.length>8192)throw bad(413,'요청이 너무 큽니다.');}try{return JSON.parse(body||'{}');}catch{throw bad(400,'JSON 형식 오류');}}
function bearer(req){const h=req.headers.authorization||'';return h.startsWith('Bearer ')?h.slice(7):'';}

export function makeServer(store=makeStore()){
  return http.createServer(async(req,res)=>{
    const url=new URL(req.url||'/', 'http://localhost');const path=url.pathname;
    try{
      if(req.method==='GET'&&(path==='/'||path==='/passenger.html')) {
        const html=await readFile(join(HERE,'public','passenger.html'));
        res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(html);return;
      }
      if(req.method==='GET'&&path==='/api/health')return send(res,200,{ok:true,service:'CrewMate demo server'});
      if(req.method==='GET'&&path==='/api/locations')return send(res,200,{locations:Object.values(locations)});
      if(req.method==='POST'&&path==='/api/join'){
        const d=await bodyJson(req);return send(res,201,store.joinSession(d.name,d.code));
      }
      if(req.method==='GET'&&path==='/api/me')return send(res,200,store.self(bearer(req)));
      if(req.method==='POST'&&path==='/api/requests'){
        const d=await bodyJson(req);return send(res,201,store.requestChange(bearer(req),d.type,d.destination));
      }
      const drive=(req.headers['x-driver-key']||'')===store.driverKey;
      if(path.startsWith('/api/')&&!drive)throw bad(401,'올바른 운전자 키가 필요합니다.');
      if(req.method==='GET'&&path==='/api/state')return send(res,200,store.snapshot());
      const decision=path.match(/^\/api\/requests\/([^/]+)\/decision$/);
      if(req.method==='POST'&&decision){const d=await bodyJson(req);return send(res,200,store.decide(decision[1],d.decision));}
      const alight=path.match(/^\/api\/passengers\/([^/]+)\/alight$/);
      if(req.method==='POST'&&alight)return send(res,200,store.markAlight(alight[1]));
      const permission=path.match(/^\/api\/passengers\/([^/]+)\/permission$/);
      if(req.method==='POST'&&permission){const d=await bodyJson(req);return send(res,200,store.changePermission(permission[1],d.permission,d.enabled));}
      if(req.method==='POST'&&path==='/api/session/reset')return send(res,200,store.resetSession());
      return send(res,404,{error:'경로를 찾을 수 없습니다.'});
    }catch(e){send(res,e.status||500,{error:e.status?e.message:'서버 처리 오류'});}
  });
}

if(process.argv[1] && fileURLToPath(import.meta.url)===process.argv[1]) {
  const store=makeStore({code:process.env.CREWMATE_CODE||'CREW26',driverKey:process.env.CREWMATE_DRIVER_KEY||crypto.randomBytes(16).toString('hex')});
  const port=Number(process.env.PORT||8787);
  makeServer(store).listen(port,'0.0.0.0',()=>{
    console.log(`CrewMate demo server running on port ${port}`);
    console.log(`Session code: ${store.code}`);
    console.log(`Driver key (keep private): ${store.driverKey}`);
    console.log(`Passenger URL: http://<computer-LAN-IP>:${port}/?code=${store.code}`);
    console.log('Android emulator server URL: http://10.0.2.2:'+port);
  });
}
