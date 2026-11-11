const key=document.getElementById("key"),st=document.getElementById("st");
async function show(){const c=await chrome.storage.local.get({readKey:"",lastOk:0,lastErr:"",state:null});
 key.placeholder=c.readKey?"(kulcs mentve – új beírásával cserélhető)":"Olvasó kulcs";
 st.textContent=c.lastErr?("Hiba: "+c.lastErr):c.lastOk?("Rendben. Utolsó szinkron: "+new Date(c.lastOk).toLocaleString("hu-HU")):"Még nem volt szinkron.";}
document.getElementById("save").onclick=async()=>{const v=key.value.trim();if(v){await chrome.storage.local.set({readKey:v});key.value="";}
 await chrome.runtime.sendMessage("poll");show();};
show();
