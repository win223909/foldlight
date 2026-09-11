'use strict';
const $=s=>document.querySelector(s), number=n=>new Intl.NumberFormat('zh-CN').format(n);
const date=n=>new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(new Date(n));
const regions=typeof Intl.DisplayNames==='function'?new Intl.DisplayNames(['zh-CN'],{type:'region'}):null;
let page=1,total=0,sequence=0,controller=null;
function params(){return new URLSearchParams({days:$('#days').value,device:$('#device').value,bots:$('#bots').checked?'1':'0',ip:$('#ip').value.trim(),kind:$('#kind').value,page:String(page)})}
function element(tag,text,klass){const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(klass)el.className=klass;return el}
function country(code){if(!code)return '未提供';try{return regions?.of(code)||code}catch{return code}}
async function get(url,signal){const res=await fetch(url,{signal,cache:'no-store'});if(res.status===401||res.status===403)throw Error('登录验证失败，请重新打开后台登录。');if(!res.ok||!res.headers.get('content-type')?.includes('application/json'))throw Error('暂时无法读取统计，请稍后刷新。');return res.json()}
function overview(data){
 $('#pageviews').textContent=number(data.totals.pageviews);$('#unique-ips').textContent=number(data.totals.unique_ips);$('#downloads').textContent=number(data.totals.downloads);
 $('#download-breakdown').textContent='Mac '+number(data.totals.downloads_mac)+' · Fold8 '+number(data.totals.downloads_fold8);
 $('#started-at').textContent='统计启用时间：'+date(data.started_at);
 const lag=Date.now()-data.last_ingest_at;$('#live-label').textContent=(lag>15000?'采集更新较慢 · ':'已更新 · ')+date(data.updated_at);
 const chart=$('#chart');chart.replaceChildren();
 const start=new Date(data.start),buckets=[],step=data.days===1?3600000:86400000;
 const end=data.days===1?24:data.days;
 const key=t=>{const parts=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',hour12:false}).format(new Date(t));return data.days===1?parts.slice(0,13)+':00':parts.slice(0,10)};
 const counts=new Map(data.trend.map(x=>[x.bucket,x.pageviews]));
 for(let i=0;i<end;i++){const label=key(start.getTime()+i*step);buckets.push({label,count:counts.get(label)||0})}
 const max=Math.max(1,...buckets.map(x=>x.count));
 if(!data.totals.pageviews)chart.append(element('div','此范围内还没有页面访问','empty'));
 else for(const bucket of buckets){const bar=element('div',undefined,'bar'+(bucket.count?'':' zero'));bar.style.height=Math.max(2,bucket.count/max*145)+'px';bar.title=bucket.label+' · '+number(bucket.count)+' 次访问';chart.append(bar)}
 chart.setAttribute('aria-label','访问趋势，'+data.totals.pageviews+' 次页面访问');
 $('#chart-caption').replaceChildren(element('span',buckets[0].label),element('span',buckets.at(-1).label));
 const devices=$('#device-breakdown');devices.replaceChildren();
 if(!data.breakdowns.device.length)devices.append(element('p','尚无设备数据','fine'));
 for(const row of data.breakdowns.device){const item=element('div',undefined,'distribution'),label=element('div');label.append(element('span',row.name),element('span',number(row.count)+' · '+Math.round(row.count/Math.max(1,data.totals.pageviews)*100)+'%'));const track=element('div',undefined,'track'),fill=element('div');fill.style.width=row.count/Math.max(1,data.totals.pageviews)*100+'%';track.append(fill);item.append(label,track);devices.append(item)}
}
function records(data){
 total=data.total;const body=$('#rows');body.replaceChildren();
 if(!data.rows.length){const cell=element('td','还没有匹配的访问记录。新访问通常在几秒内出现。','empty');cell.colSpan=7;const tr=element('tr');tr.append(cell);body.append(tr)}
 for(const row of data.rows){const tr=element('tr'),at=element('td',date(row.at)),ip=element('td',row.ip,'mono'),device=element('td',row.device),browser=element('td',row.browser),region=element('td',country(row.country)),ref=element('td',row.referrer||'直接或未提供'),kind=element('td',row.kind==='page'?'页面访问':row.path.endsWith('.apk')?'Fold8 下载':'Mac 下载');device.append(element('small',row.system));if(row.bot)browser.append(element('small','疑似机器人'));kind.append(element('small',row.path));tr.append(at,ip,device,browser,region,ref,kind);body.append(tr)}
 $('#record-count').textContent='共 '+number(total)+' 条匹配记录';$('#page-info').textContent='第 '+page+' / '+Math.max(1,Math.ceil(total/50))+' 页';$('#prev').disabled=page<=1;$('#next').disabled=page*50>=total;
}
async function load(){
 const request=++sequence;controller?.abort();controller=new AbortController();$('#refresh').disabled=true;
 try{const q=params(),[summary,visits]=await Promise.all([get('/admin/api/summary?'+q,controller.signal),get('/admin/api/visits?'+q,controller.signal)]);if(request!==sequence)return;overview(summary);records(visits);$('#error').hidden=true}
 catch(error){if(error.name==='AbortError'||request!==sequence)return;const box=$('#error');box.replaceChildren(element('span',error.message||'连接失败，请重试。'),Object.assign(element('a','重新打开后台'),{href:'/admin/'}));box.hidden=false;$('#live-label').textContent='更新失败 · 保留上次成功的数据'}
 finally{if(request===sequence)$('#refresh').disabled=false}
}
$('#filters').onsubmit=e=>{e.preventDefault();page=1;load()};for(const id of ['days','device','bots','kind'])$('#'+id).onchange=()=>{page=1;load()};$('#refresh').onclick=load;$('#prev').onclick=()=>{page=Math.max(1,page-1);load()};$('#next').onclick=()=>{page++;load()};
$('#export').onclick=()=>{const link=element('a');link.href='/admin/api/export?'+params();link.download='duo-visits.csv';link.click()};
document.addEventListener('visibilitychange',()=>{if(!document.hidden)load()});setInterval(()=>{if(!document.hidden)load()},30000);load();
