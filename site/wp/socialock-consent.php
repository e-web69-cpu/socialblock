<?php
/**
 * Plugin Name: SociaLock – süti-hozzájárulás + GA4
 * Description: Saját süti-sáv (Elfogadom / Csak a szükségesek). A Google Analytics 4 (G-LJQVZMRECB) csak elfogadás után töltődik be (Consent Mode v2, alapból minden megtagadva). Előregisztrációnál generate_lead esemény.
 * Author: E-WEB Consulting Kft.
 */
if (!defined('ABSPATH')) exit;
const SLK_GA_ID = 'G-LJQVZMRECB';

add_action('wp_head', function () {
    if (is_admin()) return;
    $id = SLK_GA_ID;
    echo <<<HTML
<script>
window.dataLayer=window.dataLayer||[];function gtag(){dataLayer.push(arguments);}
gtag('consent','default',{ad_storage:'denied',analytics_storage:'denied',ad_user_data:'denied',ad_personalization:'denied',functionality_storage:'granted',security_storage:'granted'});
(function(){var c=null;try{c=localStorage.getItem('slk-consent');}catch(e){}
window.slkLoadGA=function(){if(window.slkGA)return;window.slkGA=1;gtag('consent','update',{analytics_storage:'granted'});var s=document.createElement('script');s.async=1;s.src='https://www.googletagmanager.com/gtag/js?id={$id}';document.head.appendChild(s);gtag('js',new Date());gtag('config','{$id}',{anonymize_ip:true});};
if(c==='granted'){window.slkLoadGA();}})();
</script>

HTML;
}, 0);

add_action('wp_footer', function () {
    if (is_admin()) return;
    $priv = esc_url(home_url('/?page_id=12#sutik'));
    echo <<<HTML
<div id="slk-cc" role="dialog" aria-live="polite" aria-label="Süti-beállítások" hidden>
  <div class="slk-cc-in">
    <p><b>Sütik</b> – Az oldal látogatottságát a Google Analytics segítségével mérjük, ha hozzájárulsz. Hirdetési sütit nem használunk. <a href="{$priv}">Részletek</a></p>
    <div class="slk-cc-b"><button type="button" data-c="denied">Csak a szükségesek</button><button type="button" data-c="granted">Elfogadom</button></div>
  </div>
</div>
<style>
#slk-cc{position:fixed;left:16px;right:16px;bottom:16px;z-index:9999;display:flex;justify-content:center;pointer-events:none}
#slk-cc[hidden]{display:none}
#slk-cc .slk-cc-in{pointer-events:auto;max-width:760px;width:100%;background:#FFFFFF;color:#111827;border:1px solid #E5E7EB;border-radius:18px;box-shadow:0 20px 50px rgba(17,24,39,.18);padding:16px 18px;display:flex;gap:16px;align-items:center;flex-wrap:wrap;font-family:Roboto,system-ui,"Segoe UI",Arial,sans-serif;font-size:14px;line-height:1.5}
#slk-cc p{margin:0;flex:1 1 320px}
#slk-cc a{color:#2D65F8;text-decoration:underline}
#slk-cc .slk-cc-b{display:flex;gap:10px;flex-wrap:wrap}
#slk-cc button{font:inherit;font-weight:600;border-radius:999px;padding:10px 18px;cursor:pointer;border:2px solid #2D65F8;background:#FFFFFF!important;color:#2D65F8!important}
#slk-cc button[data-c="granted"]{background:#2D65F8!important;color:#FFFFFF!important}
html[data-theme="dark"] #slk-cc .slk-cc-in{background:#111827;color:#FFFFFF;border-color:rgba(229,231,235,.15)}
html[data-theme="dark"] #slk-cc a{color:#9DB8FF}
html[data-theme="dark"] #slk-cc button[data-c="denied"]{background:#111827!important;color:#FFFFFF!important}
</style>
<script>
(function(){var box=document.getElementById('slk-cc');if(!box)return;var c=null;try{c=localStorage.getItem('slk-consent');}catch(e){}
function show(){box.hidden=false;}
if(c!=='granted'){if(c!=='denied')show();}
box.addEventListener('click',function(e){var b=e.target.closest('button[data-c]');if(!b)return;var v=b.getAttribute('data-c');try{localStorage.setItem('slk-consent',v);}catch(x){}box.hidden=true;if(v==='granted'){window.slkLoadGA();}});
document.querySelectorAll('[data-slk-cookies]').forEach(function(a){a.addEventListener('click',function(e){e.preventDefault();show();});});
var q=new URLSearchParams(location.search).get('ertesites');if(q==='ok'){var f=function(){gtag('event','generate_lead',{method:'eloregisztracio'});};if(window.slkGA){f();}else{window.addEventListener('slk-ga',f);}}
document.addEventListener('click',function(e){var a=e.target.closest('a');if(!a)return;var h=a.getAttribute('href')||'';if(h.indexOf('#ertesites')>-1){gtag('event','cta_ertesites_click');}if(h.indexOf('.zip')>-1){gtag('event','windows_modul_download');}});
})();
</script>

HTML;
}, 99);
