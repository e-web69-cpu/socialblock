// SociaLock böngészőmodul – a szerver (sb.trendz.hu) állapotát olvassa (csak olvasó kulccsal),
// és a tiltási időszakban a listán lévő oldalakat a saját tiltó oldalra irányítja.
const API = "https://sb.trendz.hu/api.php?r=state";
const POLL_MIN = 1;

async function getCfg() {
  return chrome.storage.local.get({ readKey: "", state: null, lastOk: 0, lastErr: "" });
}

function hm(s) { const m = /^(\d\d):(\d\d)$/.exec(s || ""); return m ? (+m[1]) * 60 + (+m[2]) : null; }

// Ugyanaz a logika, mint a szerveren (napok: 1 = vasárnap … 7 = szombat), a gép helyi idejével.
function inSchedule(windows, d) {
  const dow = d.getDay() + 1, prev = dow === 1 ? 7 : dow - 1, now = d.getHours() * 60 + d.getMinutes();
  for (const w of windows || []) {
    const s = hm(w.start), e = hm(w.end), days = w.days || [];
    if (s === null || e === null) continue;
    if (s <= e) { if (days.includes(dow) && now >= s && now < e) return true; }
    else {
      if (days.includes(dow) && now >= s) return true;
      if (days.includes(prev) && now < e) return true;
    }
  }
  return false;
}

function isBlockingNow(st) {
  if (!st || !st.rules || !st.rules.enabled) return false;
  const nowSec = Date.now() / 1000;
  if (st.unlocked_until && nowSec < st.unlocked_until) return false; // feloldva
  return inSchedule(st.rules.windows, new Date());
}

async function applyRules() {
  const { state } = await getCfg();
  const sites = (state && state.rules && state.rules.sites) || [];
  const blocking = isBlockingNow(state) && sites.length > 0;
  const old = await chrome.declarativeNetRequest.getDynamicRules();
  const removeRuleIds = old.map(r => r.id);
  const addRules = blocking ? sites.map((d, i) => ({
    id: i + 1, priority: 1,
    action: { type: "redirect", redirect: { extensionPath: "/blocked.html?site=" + encodeURIComponent(d) } },
    condition: { requestDomains: [d], resourceTypes: ["main_frame"] }
  })) : [];
  await chrome.declarativeNetRequest.updateDynamicRules({ removeRuleIds, addRules });
  await chrome.action.setBadgeText({ text: blocking ? "ON" : "" });
  await chrome.action.setBadgeBackgroundColor({ color: "#2D65F8" });
}

async function poll() {
  const { readKey } = await getCfg();
  if (!readKey) { await chrome.storage.local.set({ lastErr: "Nincs kulcs megadva." }); return applyRules(); }
  try {
    const r = await fetch(API, { headers: { "X-Key": readKey }, cache: "no-store" });
    if (r.status === 401) throw new Error("Hibás kulcs (401)");
    if (!r.ok) throw new Error("Szerverhiba (" + r.status + ")");
    const st = await r.json();
    await chrome.storage.local.set({ state: st, lastOk: Date.now(), lastErr: "" });
  } catch (e) {
    // Hiba esetén az utoljára ismert állapotból dolgozunk tovább (nem lazítunk).
    await chrome.storage.local.set({ lastErr: String(e.message || e) });
  }
  await applyRules();
}

chrome.runtime.onInstalled.addListener(() => { chrome.alarms.create("poll", { periodInMinutes: POLL_MIN }); poll(); });
chrome.runtime.onStartup.addListener(() => { chrome.alarms.create("poll", { periodInMinutes: POLL_MIN }); poll(); });
chrome.alarms.onAlarm.addListener(a => { if (a.name === "poll") poll(); });
chrome.storage.onChanged.addListener((c, area) => { if (area === "local" && c.readKey) poll(); });
chrome.runtime.onMessage.addListener((m, _s, send) => { if (m === "poll") poll().then(() => send(true)); return true; });
