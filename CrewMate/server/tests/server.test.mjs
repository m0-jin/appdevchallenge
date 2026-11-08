import test from 'node:test';
import assert from 'node:assert/strict';
import {makeStore,makeServer} from '../server.mjs';

const auth = key => ({'x-driver-key':key,'Content-Type':'application/json'});
async function withServer(fn){
  const store=makeStore({driverKey:'my-driver-secret'}), server=makeServer(store);
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const url='http://127.0.0.1:'+server.address().port;
  try{await fn({store,url});}finally{await new Promise((resolve,reject)=>server.close(err=>err?reject(err):resolve()));}
}
async function api(url,path,options={}) {const r=await fetch(url+path,options);return {status:r.status,data:await r.json()};}

test('driver requires key; passenger cannot act as driver',async()=>withServer(async({url})=>{
  assert.equal((await api(url,'/api/state')).status,401);
  assert.equal((await api(url,'/api/state',{headers:auth('my-driver-secret')})).status,200);
  assert.equal((await api(url,'/api/session/reset',{method:'POST'})).status,401);
}));
test('join, submit, approve changes and recalculate route',async()=>withServer(async({url})=>{
  const j=await api(url,'/api/join',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code:'CREW26',name:'민수'})});
  assert.equal(j.status,201);const token=j.data.token;assert.ok(token);
  const newRequest=await api(url,'/api/requests',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+token},body:JSON.stringify({type:'destination',destination:'HONGDAE'})});
  assert.equal(newRequest.status,201);
  const approval=await api(url,`/api/requests/${newRequest.data.id}/decision`,{method:'POST',headers:auth('my-driver-secret'),body:JSON.stringify({decision:'approved'})});
  assert.equal(approval.status,200);
  const state=(await api(url,'/api/state',{headers:auth('my-driver-secret')})).data;
  assert.equal(state.passengers.find(p=>p.id===j.data.passengerId).destination.id,'HONGDAE');
  assert.equal(state.route.stops.at(-1).location.id !== undefined,true);
  assert.equal(state.route.stops.length,4);
}));
test('permission is enforced, alight revokes token and pending requests',async()=>withServer(async({url})=>{
  const join=await api(url,'/api/join',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code:'CREW26',name:'다은'})});
  const id=join.data.passengerId;const token=join.data.token;
  const p=await api(url,`/api/passengers/${id}/permission`,{method:'POST',headers:auth('my-driver-secret'),body:JSON.stringify({permission:'canSuggestWaypoint',enabled:false})});
  assert.equal(p.status,200);
  const deny=await api(url,'/api/requests',{method:'POST',headers:{'Authorization':'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify({type:'waypoint',destination:'GANGNAM'})});
  assert.equal(deny.status,403);
  const req=await api(url,'/api/requests',{method:'POST',headers:{'Authorization':'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify({type:'destination',destination:'GANGNAM'})});
  assert.equal(req.status,201);
  const out=await api(url,`/api/passengers/${id}/alight`,{method:'POST',headers:auth('my-driver-secret')});
  assert.equal(out.data.active,false);
  assert.equal((await api(url,'/api/me',{headers:{Authorization:'Bearer '+token}})).status,401);
  assert.equal((await api(url,`/api/requests/${req.data.id}/decision`,{method:'POST',headers:auth('my-driver-secret'),body:JSON.stringify({decision:'approved'})})).status,409);
}));
test('bad destinations and duplicate approvals rejected',async()=>withServer(async({url})=>{
  const b=await api(url,'/api/join',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code:'CREW26',name:'x'})});
  const req=await api(url,'/api/requests',{method:'POST',headers:{Authorization:'Bearer '+b.data.token,'Content-Type':'application/json'},body:JSON.stringify({type:'destination',destination:'UNKNOWN'})});
  assert.equal(req.status,400);
  const body=JSON.stringify({decision:'approved'});
  assert.equal((await api(url,'/api/requests/sample1/decision',{method:'POST',headers:auth('my-driver-secret'),body})).status,200);
  assert.equal((await api(url,'/api/requests/sample1/decision',{method:'POST',headers:auth('my-driver-secret'),body})).status,409);
}));
