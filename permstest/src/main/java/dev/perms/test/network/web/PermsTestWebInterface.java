package dev.perms.test.network.web;

import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;

/**
 * Web Interface page and API dispatcher for the Network tab HTTP server.
 *
 * Keep remote-control endpoints centralized here so app controls can grow behind
 * a small bridge instead of expanding the generic HTTP file server/runtime.
 */
public final class PermsTestWebInterface {
    public static final String SECTION_GLOBAL = "global";
    public static final String SECTION_MEMORY = "memory";

    private final Bridge bridge;
    private final String token;

    public PermsTestWebInterface(Bridge bridge, String token) {
        this.bridge = bridge;
        this.token = token == null ? "" : token.trim();
    }

    public String buildPageHtml() {
        String tokenParam = token.length() == 0 ? "" : "?token=" + urlEncode(token);
        StringBuilder html = new StringBuilder(36000);
        html.append("<!doctype html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("<meta charset=\"utf-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">\n");
        html.append("<title>PermsTest Web Interface</title>\n");
        html.append("<style>\n");
        html.append(":root{color-scheme:dark;--bg:#0f1014;--panel:#171920;--panel2:#1f222c;--line:#343848;--text:#f2f4f8;--muted:#a7afc0;--accent:#8ab4ff;--good:#7bd88f;--warn:#ffd166;--bad:#ff8a80;--shadow:rgba(0,0,0,.35)}\n");
        html.append("*{box-sizing:border-box}body{margin:0;background:linear-gradient(135deg,#0c0d11,#151824 55%,#0d1118);color:var(--text);font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;font-size:15px;line-height:1.38}.shell{display:grid;grid-template-columns:260px 1fr;min-height:100vh}.sidebar{position:sticky;top:0;height:100vh;overflow:auto;padding:18px 14px;background:rgba(10,12,18,.86);border-right:1px solid var(--line);backdrop-filter:blur(10px)}.brand{font-weight:700;font-size:1.25rem;margin:0 0 4px}.subtitle{color:var(--muted);font-size:.9rem;margin-bottom:14px}.nav{display:grid;gap:8px}.nav button{width:100%;text-align:left}.content{padding:18px;max-width:1450px}.section{display:none}.section.active{display:block}.card{background:linear-gradient(180deg,var(--panel),#14161d);border:1px solid var(--line);border-radius:16px;padding:15px;margin:0 0 14px;box-shadow:0 10px 30px var(--shadow)}.card h2,.card h3{margin:0 0 10px}.grid{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));gap:10px}.col-2{grid-column:span 2}.col-3{grid-column:span 3}.col-4{grid-column:span 4}.col-5{grid-column:span 5}.col-6{grid-column:span 6}.col-8{grid-column:span 8}.col-12{grid-column:span 12}.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.muted{color:var(--muted);font-size:.92rem}.tag{display:inline-flex;align-items:center;border:1px solid var(--line);border-radius:999px;padding:2px 8px;margin:2px 4px 2px 0;background:#20232d;color:#cfd5e3;font-size:.82rem}.ok{color:var(--good)}.bad{color:var(--bad)}.warn{color:var(--warn)}label{display:block;color:#d6dbea;font-size:.88rem;margin-bottom:4px}input,select,textarea{width:100%;background:#10131a;color:var(--text);border:1px solid #3a4052;border-radius:10px;padding:9px 10px;font:inherit;outline:none}textarea{min-height:84px;resize:vertical}select[size]{height:auto;min-height:170px;padding:6px}input:focus,select:focus,textarea:focus{border-color:var(--accent);box-shadow:0 0 0 2px rgba(138,180,255,.18)}button{background:#252a36;color:var(--text);border:1px solid #42495d;border-radius:10px;padding:9px 11px;font:inherit;cursor:pointer}button:hover{border-color:var(--accent);background:#2e3544}.primary{background:#24436f;border-color:#3c6fb5}.danger{background:#4a2226;border-color:#8a3a42}.small{font-size:.86rem;padding:7px 9px}.toolbar{display:flex;gap:8px;flex-wrap:wrap;margin-top:10px}.panel-title{display:flex;justify-content:space-between;gap:8px;align-items:center}.hidden{display:none!important}pre{white-space:pre-wrap;word-break:break-word;background:#0d0f14;border:1px solid #313747;border-radius:12px;padding:10px;max-height:42vh;overflow:auto}.results{width:100%;border-collapse:collapse;margin-top:8px;font-size:.88rem}.results th,.results td{border-bottom:1px solid #2e3442;padding:7px;text-align:left;vertical-align:top}.results th{color:#dce6ff;background:#171b24;position:sticky;top:0}.split{display:grid;grid-template-columns:minmax(260px,360px) 1fr;gap:12px}.empty{padding:12px;border:1px dashed #3a4052;border-radius:12px;color:var(--muted);text-align:center}.notice{border-left:4px solid var(--accent);padding-left:10px}.kv{display:grid;grid-template-columns:140px 1fr;gap:6px;font-size:.9rem}.kv b{color:#d8e2ff}@media(max-width:900px){.shell{grid-template-columns:1fr}.sidebar{position:relative;height:auto}.content{padding:12px}.grid{grid-template-columns:1fr}.col-2,.col-3,.col-4,.col-5,.col-6,.col-8,.col-12{grid-column:span 1}.split{grid-template-columns:1fr}.nav{grid-template-columns:repeat(2,minmax(0,1fr))}.nav button{text-align:center}}@media(max-width:520px){.nav{grid-template-columns:1fr}.toolbar button{flex:1 1 100%}}\n");
        html.append("</style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("<div class=\"shell\">\n");
        html.append("<aside class=\"sidebar\">\n");
        html.append("  <div class=\"brand\">PermsTest Web Interface</div>\n");
        html.append("  <div class=\"subtitle\">Remote controls for enabled app sections.</div>\n");
        html.append("  <div id=\"accessPills\" class=\"muted\">Loading access map...</div>\n");
        html.append("  <div class=\"nav\" style=\"margin-top:14px\">\n");
        html.append("    <button id=\"navGlobal\" onclick=\"showSection('global')\">Global</button>\n");
        html.append("    <button id=\"navMemory\" onclick=\"showSection('memory')\">Memory</button>\n");
        html.append("    <button onclick=\"refreshAll()\">Refresh</button>\n");
        html.append("  </div>\n");
        html.append("</aside>\n");
        html.append("<main class=\"content\">\n");
        html.append("<section id=\"sectionGlobal\" class=\"section active\">\n");
        html.append("  <div class=\"card\">\n");
        html.append("    <div class=\"panel-title\"><h2>Server Controls</h2><span class=\"tag\">Global</span></div>\n");
        html.append("    <div class=\"muted notice\">Global controls are app-level web actions. Tab-specific tools are shown only when enabled in the Network tab.</div>\n");
        html.append("    <div class=\"toolbar\">\n");
        html.append("      <button id=\"ftpStartButton\" class=\"primary\" onclick=\"callApi('/api/ftp/start')\">Start FTP</button>\n");
        html.append("      <button id=\"ftpStopButton\" onclick=\"callApi('/api/ftp/stop')\">Stop FTP</button>\n");
        html.append("      <button onclick=\"refreshAll()\">Refresh Status</button>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");
        html.append("  <div class=\"grid\">\n");
        html.append("    <div class=\"card col-6\"><h3>Status</h3><pre id=\"status\">Loading...</pre></div>\n");
        html.append("    <div class=\"card col-6\"><h3>Output Tail</h3><pre id=\"output\">Loading...</pre></div>\n");
        html.append("  </div>\n");
        html.append("</section>\n");
        html.append("<section id=\"sectionMemory\" class=\"section\">\n");
        html.append("  <div class=\"card\">\n");
        html.append("    <div class=\"panel-title\"><h2>Memory</h2><span class=\"tag\">Backend apk-medit</span></div>\n");
        html.append("    <div class=\"muted notice\">Memory Web uses the same backend command path as the on-device Memory tools. Select a package, resolve a process, then run scan, filter, patch, state, or byte operations.</div>\n");
        html.append("  </div>\n");
        html.append("  <div class=\"split\">\n");
        html.append("    <div class=\"card\">\n");
        html.append("      <h3>Target Package</h3>\n");
        html.append("      <label>Search packages</label><input id=\"pkgSearch\" placeholder=\"Filter by label or package\" oninput=\"renderPackageSelect()\">\n");
        html.append("      <label style=\"margin-top:8px\">Package list</label><select id=\"pkgSelect\" size=\"10\" onchange=\"selectPackageFromList()\"></select>\n");
        html.append("      <div id=\"pkgCount\" class=\"muted\" style=\"margin-top:6px\">Refresh packages to populate the list.</div>\n");
        html.append("      <label style=\"margin-top:8px\">Selected package</label><input id=\"memPkg\" placeholder=\"com.example.app\" oninput=\"targetChanged()\">\n");
        html.append("      <div class=\"toolbar\">\n");
        html.append("        <button class=\"small\" onclick=\"memoryPackages()\">Refresh Packages</button>\n");
        html.append("        <button class=\"small\" onclick=\"memoryLaunch()\">Launch</button>\n");
        html.append("        <button class=\"small danger\" onclick=\"memoryStop()\">Force Stop</button>\n");
        html.append("      </div>\n");
        html.append("      <h3 style=\"margin-top:14px\">Process</h3>\n");
        html.append("      <label>PID / process</label><select id=\"procSelect\" onchange=\"selectProcessFromList()\"><option value=\"\">Auto resolve PID</option></select>\n");
        html.append("      <input id=\"memPid\" placeholder=\"PID or blank for auto\" style=\"margin-top:8px\">\n");
        html.append("      <div class=\"toolbar\">\n");
        html.append("        <button class=\"small\" onclick=\"memoryProcesses()\">Refresh Processes</button>\n");
        html.append("        <button class=\"small primary\" onclick=\"memoryAttach()\">Attach</button>\n");
        html.append("        <button class=\"small\" onclick=\"memoryDetach()\">Detach</button>\n");
        html.append("        <button class=\"small\" onclick=\"memoryStatus()\" title=\"Shows backend and Memory web settings; it does not attach to the target.\">Memory Status</button>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"muted\" style=\"margin-top:6px\">Memory Status shows backend, preferences, and state-file details only.</div>\n");
        html.append("    </div>\n");
        html.append("    <div>\n");
        html.append("      <div class=\"card\">\n");
        html.append("        <h3>Scanner</h3>\n");
        html.append("        <div class=\"grid\">\n");
        html.append("          <div class=\"col-3\"><label>Data type</label><select id=\"memType\"><option>all</option><option>string</option><option>word</option><option>dword</option><option>qword</option></select></div>\n");
        html.append("          <div class=\"col-5\"><label>Value</label><input id=\"memValue\" placeholder=\"number, string, or comparison value\"></div>\n");
        html.append("          <div class=\"col-2\"><label>Max results</label><input id=\"memMax\" value=\"500000\" inputmode=\"numeric\"></div>\n");
        html.append("          <div class=\"col-2\"><label>Case</label><select id=\"memCase\"><option value=\"default\">Settings default</option><option value=\"sensitive\">Sensitive</option><option value=\"insensitive\">Insensitive</option></select></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"toolbar\">\n");
        html.append("          <button class=\"primary\" onclick=\"memoryRunCommand('find')\">Exact Scan</button>\n");
        html.append("          <button onclick=\"memoryRunCommand('snapshot')\">Unknown Initial</button>\n");
        html.append("          <button onclick=\"memoryRunCommand('find-gt')\">Greater Than</button>\n");
        html.append("          <button onclick=\"memoryRunCommand('find-lt')\">Less Than</button>\n");
                html.append("        </div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"card\">\n");
        html.append("        <h3>Next Scan / Patch</h3>\n");
        html.append("        <div class=\"grid\">\n");
        html.append("          <div class=\"col-6\"><label>Filter value</label><input id=\"filterValue\" placeholder=\"leave blank for changed/unchanged\"></div>\n");
        html.append("          <div class=\"col-6\"><label>Patch value</label><input id=\"patchValue\" placeholder=\"value to write to matched results\"></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"toolbar\">\n");
        html.append("          <button onclick=\"memoryRunCommand('filter')\">Filter Exact</button>\n");
        html.append("          <button onclick=\"memoryRunCommand('filter-changed')\">Changed</button>\n");
        html.append("          <button onclick=\"memoryRunCommand('filter-unchanged')\">Unchanged</button>\n");
        html.append("          <button onclick=\"memoryRunCommand('filter-gt')\">Increased / Greater</button>\n");
        html.append("          <button onclick=\"memoryRunCommand('filter-lt')\">Decreased / Less</button>\n");
        html.append("          <button class=\"primary\" onclick=\"memoryPatch()\">Patch Results</button>\n");
        html.append("          <button class=\"danger\" onclick=\"memoryClear()\">Clear Session</button>\n");
        html.append("        </div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"card\">\n");
        html.append("        <h3>Hex / Bytes / Range</h3>\n");
        html.append("        <div class=\"grid\">\n");
        html.append("          <div class=\"col-4\"><label>Begin / address</label><input id=\"memBegin\" placeholder=\"0x...\"></div>\n");
        html.append("          <div class=\"col-4\"><label>End</label><input id=\"memEnd\" placeholder=\"0x...\"></div>\n");
        html.append("          <div class=\"col-4\"><label>Hex bytes</label><input id=\"memBytes\" placeholder=\"AA BB ?? CC\"></div>\n");
        html.append("          <div class=\"col-12\"><label>Mask hex</label><input id=\"memMask\" placeholder=\"optional mask for search-bytes-mask\"></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"toolbar\">\n");
        html.append("          <button onclick=\"memoryRunCommand('search-bytes')\">Find Bytes</button>\n");
        html.append("          <button onclick=\"memoryRunBytesMask()\">Find Bytes + Mask</button>\n");
        html.append("          <button onclick=\"memoryRunCommand('write-bytes')\">Write Bytes</button>\n");
        html.append("          <button onclick=\"memoryRunCommand('dump')\">Dump Range</button>\n");
        html.append("          <button onclick=\"memoryState()\">Read State</button>\n");
        html.append("        </div>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"card\">\n");
        html.append("        <div class=\"panel-title\"><h3>Results / State</h3><span id=\"memSummary\" class=\"tag\">Idle</span></div>\n");
        html.append("        <div id=\"resultsBox\" class=\"empty\">Run a Memory command or read state to show results.</div>\n");
        html.append("        <pre id=\"memoryOut\" style=\"margin-top:10px\">Memory output...</pre>\n");
        html.append("      </div>\n");
        html.append("      <details class=\"card\"><summary>Advanced command</summary>\n");
        html.append("        <div class=\"grid\" style=\"margin-top:10px\">\n");
        html.append("          <div class=\"col-4\"><label>Command</label><select id=\"memCommand\"><option>attach</option><option>detach</option><option>snapshot</option><option>find</option><option>find-gt</option><option>find-lt</option><option>filter</option><option>filter-changed</option><option>filter-unchanged</option><option>filter-gt</option><option>filter-lt</option><option>patch</option><option>dump</option><option>search-bytes</option><option>search-bytes-mask</option><option>write-bytes</option></select></div>\n");
        html.append("          <div class=\"col-8\"><label>Value / bytes</label><input id=\"advancedValue\" placeholder=\"optional command value\"></div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"toolbar\"><button onclick=\"memoryRunAdvanced()\">Run Advanced Command</button></div>\n");
        html.append("      </details>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");
        html.append("</section>\n");
        html.append("</main>\n");
        html.append("</div>\n");
        html.append("<script>\n");
        html.append("const tokenParam='").append(jsonEscape(tokenParam)).append("';\n");
        html.append("let access={global:true,memory:false};let packages=[];let packageMeta={count:0,total:0,truncated:false};let processes=[];\n");
        html.append("function apiUrl(path,params){let q=tokenParam?tokenParam.substring(1):'';if(params){q+=(q?'&':'')+params}return path+(q?'?'+q:'')}\n");
        html.append("async function getJson(path,params){const r=await fetch(apiUrl(path,params));return await r.json()}\n");
        html.append("async function callApi(path,params){try{const j=await getJson(path,params);showJson('status',j);setTimeout(refreshAll,500);return j}catch(e){showText('status',e)}}\n");
        html.append("function showText(id,text){const el=document.getElementById(id);if(el)el.textContent=text==null?'':String(text)}\n");
        html.append("function showJson(id,obj){showText(id,JSON.stringify(obj,null,2))}\n");
        html.append("function setVisible(id,on){const el=document.getElementById(id);if(el)el.classList.toggle('hidden',!on)}\n");
        html.append("function setDisabled(id,on){const el=document.getElementById(id);if(el)el.disabled=!!on}\n");
        html.append("function showSection(name){document.querySelectorAll('.section').forEach(e=>e.classList.remove('active'));const el=document.getElementById('section'+name.charAt(0).toUpperCase()+name.slice(1));if(el)el.classList.add('active')}\n");
        html.append("function accessPill(k,on){return '<span class=\"tag '+(on?'ok':'bad')+'\">'+k+': '+(on?'on':'off')+'</span>'}\n");
        html.append("async function refreshAccess(){try{const j=await getJson('/api/access');access=(j&&j.access)||access;setVisible('navGlobal',!!access.global);setVisible('navMemory',!!access.memory);document.getElementById('accessPills').innerHTML=Object.keys(access).map(k=>accessPill(k,!!access[k])).join(' ');if(!access.memory&&document.getElementById('sectionMemory').classList.contains('active'))showSection('global')}catch(e){showText('accessPills',e)}}\n");
        html.append("function applyServerControls(j){const ftp=!!(j&&j.ftp&&(j.ftp.running||j.ftp.starting));setVisible('ftpStartButton',!ftp);setVisible('ftpStopButton',ftp);setDisabled('ftpStartButton',ftp);setDisabled('ftpStopButton',!ftp)}\n");
        html.append("async function refreshAll(){await refreshAccess();if(access.global){try{const st=await getJson('/api/status');showJson('status',st);applyServerControls(st);const out=await getJson('/api/output');showText('output',out.output||'')}catch(e){showText('status',e)}}if(access.memory){memoryStatus(false)}}\n");
        html.append("function enc(v){return encodeURIComponent(v||'')}function val(id){const el=document.getElementById(id);return el?el.value||'':''}\n");
        html.append("function memParams(extra){let p='pkg='+enc(val('memPkg'))+'&pid='+enc(val('memPid'))+'&max='+enc(val('memMax')||'500000');if(extra)p+='&'+extra;return p}\n");
        html.append("function setMemSummary(text,cls){const el=document.getElementById('memSummary');el.textContent=text;el.className='tag '+(cls||'')}\n");
        html.append("async function mem(path,params){try{setMemSummary('Running...','warn');const j=await getJson('/api/memory/'+path,params);showJson('memoryOut',j);renderMemoryResult(j);setMemSummary((j&&j.ok)?'OK':'Error',(j&&j.ok)?'ok':'bad');return j}catch(e){showText('memoryOut',e);setMemSummary('Error','bad')}}\n");
        html.append("async function memoryStatus(show=true){const j=await mem('status','');if(!show&&j)showJson('memoryOut',j)}\n");
        html.append("async function memoryPackages(){const j=await mem('packages','');packages=(j&&j.packages)||[];packageMeta={count:(j&&j.count)||packages.length,total:(j&&j.total)||packages.length,truncated:!!(j&&j.truncated)};renderPackageSelect();return j}\n");
        html.append("function renderPackageSelect(){const sel=document.getElementById('pkgSelect');const q=val('pkgSearch').toLowerCase();sel.innerHTML='';let rows=packages.filter(p=>!q||String(p.label||'').toLowerCase().includes(q)||String(p.package||'').toLowerCase().includes(q));const count=document.getElementById('pkgCount');if(count){let msg='Showing '+rows.length+' of '+packages.length+' loaded packages';if(packageMeta.total&&packageMeta.total!==packages.length)msg+=' ('+packageMeta.total+' matched device filters)';if(packageMeta.truncated)msg+='; list truncated';count.textContent=msg}if(!rows.length){const o=document.createElement('option');o.textContent='No packages loaded';o.value='';sel.appendChild(o);return}rows.forEach(p=>{const o=document.createElement('option');o.value=p.package||'';o.textContent=(p.label||p.package||'')+' — '+(p.package||'')+(p.running?' [running]':'')+(p.debuggable?' [debuggable]':'');sel.appendChild(o)})}\n");
        html.append("function selectPackageFromList(){const sel=document.getElementById('pkgSelect');if(sel&&sel.value){document.getElementById('memPkg').value=sel.value;targetChanged();memoryProcesses()}}\n");
        html.append("function targetChanged(){processes=[];const sel=document.getElementById('procSelect');sel.innerHTML='<option value=\"\">Auto resolve PID</option>';document.getElementById('memPid').value=''}\n");
        html.append("async function memoryProcesses(){const j=await mem('processes',memParams(''));processes=(j&&j.processes)||[];const sel=document.getElementById('procSelect');sel.innerHTML='<option value=\"\">Auto resolve PID</option>';processes.forEach(p=>{const o=document.createElement('option');o.value=p.pid||'';o.textContent=(p.label||p.name||p.pid||'process');sel.appendChild(o)});return j}\n");
        html.append("function selectProcessFromList(){document.getElementById('memPid').value=val('procSelect')}\n");
        html.append("function memoryLaunch(){return mem('launch',memParams(''))}function memoryStop(){return mem('stop',memParams(''))}function memoryAttach(){return mem('attach',memParams(''))}function memoryDetach(){return mem('detach',memParams(''))}function memoryClear(){return mem('clear',memParams(''))}function memoryState(){return mem('state',memParams(''))}\n");
        html.append("function commandValue(command){if(command==='patch')return val('patchValue')||val('memValue');if(command.startsWith('filter'))return val('filterValue')||val('memValue');if(command.indexOf('bytes')>=0||command==='write-bytes')return val('memBytes')||val('memValue');return val('memValue')}\n");
        html.append("function memoryRunCommand(command){let extra='command='+enc(command)+'&type='+enc(val('memType'))+'&value='+enc(commandValue(command))+'&begin='+enc(val('memBegin'))+'&end='+enc(val('memEnd'));return mem('run',memParams(extra))}\n");
        html.append("function memoryPatch(){return memoryRunCommand('patch')}\n");
        html.append("function memoryRunBytesMask(){let extra='command=search-bytes-mask&type='+enc(val('memType'))+'&value='+enc(val('memBytes'))+'&begin='+enc(val('memBegin'))+'&end='+enc(val('memEnd'))+'&mask='+enc(val('memMask'));return mem('run',memParams(extra))}\n");
        html.append("function memoryRunAdvanced(){let extra='command='+enc(val('memCommand'))+'&type='+enc(val('memType'))+'&value='+enc(val('advancedValue')||val('memValue'))+'&begin='+enc(val('memBegin'))+'&end='+enc(val('memEnd'));return mem('run',memParams(extra))}\n");
        html.append("function renderMemoryResult(j){const box=document.getElementById('resultsBox');let rows=[];let raw=(j&&j.stateJson)||'';if(!raw&&(j&&j.stdout&&String(j.stdout).trim().startsWith('{')))raw=j.stdout;try{const parsed=raw?JSON.parse(raw):j;rows=extractRows(parsed)}catch(e){rows=[]}if(!rows.length){box.className='empty';box.textContent='No structured result rows found. Raw command output is shown below.';return}box.className='';let html='<table class=\"results\"><thead><tr><th>#</th><th>Address</th><th>Value</th><th>Type</th><th>Extra</th></tr></thead><tbody>';rows.slice(0,500).forEach((r,i)=>{html+='<tr><td>'+i+'</td><td>'+esc(r.address||r.addr||r.pointer||'')+'</td><td>'+esc(r.value||r.hex||r.text||r.ascii||'')+'</td><td>'+esc(r.type||r.size||'')+'</td><td>'+esc(r.extra||r.region||r.note||'')+'</td></tr>'});html+='</tbody></table>';box.innerHTML=html}\n");
        html.append("function extractRows(obj){if(!obj)return[];if(Array.isArray(obj))return obj;if(typeof obj==='object'){for(const k of ['results','matches','addresses','rows','items']){if(Array.isArray(obj[k]))return obj[k]}for(const k in obj){const r=extractRows(obj[k]);if(r.length)return r}}return[]}\n");
        html.append("function esc(s){return String(s==null?'':s).replace(/[&<>\"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]))}\n");
        html.append("refreshAll().then(()=>{if(access.memory)memoryPackages()});\n");
        html.append("</script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    public ApiResult handleApi(String method, String path, String query, Map<String, String> headers) {
        if (path == null) path = "";
        if (path.equals("/api/access")) {
            String json = bridge == null ? "{\"ok\":true,\"access\":{}}" : bridge.accessJson();
            return ApiResult.ok(json);
        }
        if (path.equals("/api/status")) {
            if (!isSectionEnabled(SECTION_GLOBAL)) return disabled(SECTION_GLOBAL);
            String json = bridge == null ? "{}" : bridge.statusJson();
            return ApiResult.ok(json);
        }
        if (path.equals("/api/output")) {
            if (!isSectionEnabled(SECTION_GLOBAL)) return disabled(SECTION_GLOBAL);
            String output = bridge == null ? "" : bridge.outputText();
            return ApiResult.ok("{\"output\":\"" + jsonEscape(output) + "\"}");
        }
        if (!"POST".equals(method) && !"GET".equals(method)) {
            return new ApiResult(405, "Method Not Allowed", "{\"ok\":false,\"error\":\"method not allowed\"}");
        }
        if (!isAuthorized(query, headers)) {
            return new ApiResult(403, "Forbidden", "{\"ok\":false,\"error\":\"bad token\"}");
        }
        if (path.startsWith("/api/memory/")) {
            if (!isSectionEnabled(SECTION_MEMORY)) return disabled(SECTION_MEMORY);
            return ApiResult.ok(bridge == null ? "{}" : bridge.memoryApiJson(path, query));
        }
        if (path.equals("/api/ftp/start")) {
            if (!isSectionEnabled(SECTION_GLOBAL)) return disabled(SECTION_GLOBAL);
            if (bridge != null && bridge.isFtpRunning()) {
                return new ApiResult(409, "Conflict", "{\"ok\":false,\"message\":\"FTP is already running. Stop FTP before starting it again.\"}");
            }
            if (bridge != null) bridge.startFtp();
            return ApiResult.ok("{\"ok\":true,\"message\":\"FTP start requested\"}");
        }
        if (path.equals("/api/ftp/stop")) {
            if (!isSectionEnabled(SECTION_GLOBAL)) return disabled(SECTION_GLOBAL);
            if (bridge != null) bridge.stopFtp();
            return ApiResult.ok("{\"ok\":true,\"message\":\"FTP stop requested\"}");
        }
        if (path.equals("/api/http/stop")) {
            return new ApiResult(403, "Forbidden", "{\"ok\":false,\"error\":\"HTTP stop is not available from the Web Interface\"}");
        }
        return new ApiResult(404, "Not Found", "{\"ok\":false,\"error\":\"not found\"}");
    }

    private boolean isSectionEnabled(String section) {
        return bridge == null || bridge.isWebSectionEnabled(section);
    }

    private ApiResult disabled(String section) {
        return new ApiResult(403, "Forbidden", "{\"ok\":false,\"error\":\"web section disabled: " + jsonEscape(section) + "\"}");
    }

    private boolean isAuthorized(String query, Map<String, String> headers) {
        if (token.length() == 0) return true;
        if (token.equals(queryValue(query, "token"))) return true;
        String headerToken = headers == null ? null : headers.get("x-permstest-token");
        return token.equals(headerToken);
    }

    private static String queryValue(String query, String key) {
        if (query == null || key == null) return "";
        String[] parts = query.split("&");
        for (String part : parts) {
            int equals = part.indexOf('=');
            String name = equals >= 0 ? part.substring(0, equals) : part;
            if (key.equals(urlDecode(name))) return equals >= 0 ? urlDecode(part.substring(equals + 1)) : "";
        }
        return "";
    }

    private static String urlEncode(String value) {
        try { return URLEncoder.encode(value == null ? "" : value, "UTF-8"); } catch (Throwable ignored) { return value == null ? "" : value; }
    }

    private static String urlDecode(String value) {
        try { return java.net.URLDecoder.decode(value == null ? "" : value, "UTF-8"); } catch (Throwable ignored) { return value == null ? "" : value; }
    }

    private static String jsonEscape(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }

    public interface Bridge {
        String statusJson();
        String outputText();
        String accessJson();
        boolean isWebSectionEnabled(String section);
        String memoryApiJson(String path, String query);
        boolean isFtpRunning();
        void startFtp();
        void stopFtp();
    }

    public static final class ApiResult {
        public final int statusCode;
        public final String statusText;
        public final String bodyJson;

        public ApiResult(int statusCode, String statusText, String bodyJson) {
            this.statusCode = statusCode;
            this.statusText = statusText == null ? "OK" : statusText;
            this.bodyJson = bodyJson == null ? "{}" : bodyJson;
        }

        public static ApiResult ok(String bodyJson) {
            return new ApiResult(200, "OK", bodyJson);
        }
    }
}
