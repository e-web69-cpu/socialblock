document.getElementById("o").onclick=()=>chrome.runtime.openOptionsPage();
chrome.storage.local.get({state:null,lastErr:"",readKey:""}).then(c=>{
 const s=document.getElementById("s");
 if(!c.readKey){s.textContent="Nincs kulcs megadva.";return;}
 if(c.lastErr){s.textContent="Hiba: "+c.lastErr;return;}
 const sites=(c.state&&c.state.rules&&c.state.rules.sites)||[];
 s.textContent=(c.state&&c.state.blocking_window_now?"Tiltási időszak: AKTÍV":"Tiltási időszak: INAKTÍV")+" · "+sites.length+" tiltott oldal";});
