<?php get_header(); $video = get_theme_mod('socialock_video_url'); ?>

<section class="hero">
  <div class="wrap">
    <div>
      <span class="eyebrow"><i></i> Android app · ingyenes</span>
      <h1>Amit este elküldenél, <em>reggel már bánnád.</em></h1>
      <p class="lead">A SociaLock az általad megadott napi idősávban letiltja a közösségi és e-mail appokat. Így a fáradt, ideges vagy pár pohár utáni üzenetek el sem mennek.</p>
      <div class="cta-row" id="letoltes">
        <?php socialock_play_button(); ?>
        <a class="btn btn-ghost" href="#hogyan">Hogyan működik?</a>
      </div>
      <p class="note">Android 8 vagy újabb · Fiók és regisztráció nélkül</p>
    </div>
    <div class="phone rv">
      <img src="<?php echo socialock_asset('screen-block.jpg'); ?>" alt="A SociaLock blokkoló képernyője: Állj meg egy pillanatra!">
      <span class="badge"><i></i>Messenger tiltva</span>
      <span class="badge badge2"><i></i>Holnap is ott lesz</span>
    </div>
  </div>
</section>

<section class="pain">
  <div class="wrap">
    <div class="center rv">
      <div class="kicker">Ismerős?</div>
      <h2>Az éjszakai üzenet mindig jobb ötletnek tűnik, mint reggel.</h2>
      <p class="sub">Este fáradtabbak vagyunk, hamarabb felhúzzuk magunkat, és könnyebben írunk olyat, amit józanul sosem küldenénk el. Reggel pedig már késő.</p>
    </div>
    <div class="pain-grid">
      <div class="card rv"><div class="ico">🌙</div><h3>Késő esti viták</h3><p>Egy félreértett üzenet, egy gyors válasz – és már éjfélkor is a telefont nyomkodod, egyre élesebb mondatokkal.</p></div>
      <div class="card rv"><div class="ico">🥂</div><h3>Pár pohár után</h3><p>Az exnek, a főnöknek, a csoportba. Ami akkor bátorságnak tűnt, reggel kínos magyarázkodás lesz belőle.</p></div>
      <div class="card rv"><div class="ico">📧</div><h3>Munka, ami nem várhat… de várhatna</h3><p>Az este 11-kor elküldött e-mail ritkán a legjobb formád. Egy éjszaka alvás után többnyire másképp írnád.</p></div>
    </div>
    <div class="chat">
      <div class="bubbles rv" aria-hidden="true">
        <span class="bub time">23:47</span>
        <span class="bub me">Csak azt akarom mondani, hogy…</span>
        <span class="bub me">Tudod mit, mindegy. Igazából mindig is…</span>
        <span class="bub me">És még valami!!!</span>
        <span class="bub seen">Látta: 23:48</span>
      </div>
      <blockquote class="rv">Az elküldött üzenetet nem lehet visszaszívni. <span>Az el nem küldöttet nem kell megbánni.</span></blockquote>
    </div>
  </div>
</section>

<section class="how" id="hogyan">
  <div class="wrap">
    <div class="center rv">
      <div class="kicker">Hogyan működik</div>
      <h2>Döntsd el józanul, mielőtt nem leszel az.</h2>
      <p class="sub">Napközben, nyugodt fejjel beállítod, mikor ne férj hozzá az appokhoz. A SociaLock pedig este akkor is tartja magát a döntésedhez, amikor te már nem akarnád.</p>
    </div>
    <div class="steps">
      <div class="step rv"><div class="card"><h3>Add meg az idősávot</h3><p>Például este 10-től reggel 7-ig. Bármilyen napi idősáv lehet, legfeljebb 23 óra.</p></div></div>
      <div class="step rv"><div class="card"><h3>Válaszd ki az appokat</h3><p>Messenger, WhatsApp, Instagram, Facebook, Gmail – amelyik nálad a kockázatos. A kijelöltek a lista elejére kerülnek.</p></div></div>
      <div class="step rv"><div class="card"><h3>Az idősávban áll a tiltás</h3><p>Ha megnyitnál egy tiltott appot, a SociaLock blokkoló képernyője jön fel helyette. Tiltás közben a beállítás csak szigorítható.</p></div></div>
    </div>
  </div>
</section>

<section class="show">
  <div class="wrap show-grid">
    <div class="rv">
      <div class="kicker" style="color:var(--mint)">Ilyen a gyakorlatban</div>
      <h2>„Állj meg egy pillanatra!”</h2>
      <p class="sub">Nem kioktat, nem büntet. Csak megállít abban a pillanatban, amikor a legnagyobb szükséged van rá.</p>
      <ul>
        <li><i>✓</i><span><b>Egyértelmű állapot.</b> A fejlécben mindig látod, hogy a SociaLock éppen AKTÍV vagy INAKTÍV.</span></li>
        <li><i>✓</i><span><b>Nincs kiskapu.</b> Az idősávban a tiltást nem lehet lazítani vagy kikapcsolni – csak kivárni.</span></li>
        <li><i>✓</i><span><b>Amit írnál, megvár.</b> A tiltás vége után minden ott lesz, ahol hagytad – csak addigra már józanul döntesz.</span></li>
      </ul>
    </div>
    <div class="duo rv">
      <div class="phone"><img src="<?php echo socialock_asset('screen-main.jpg'); ?>" alt="A SociaLock beállítóképernyője: tiltási idősáv"></div>
      <div class="phone"><img src="<?php echo socialock_asset('screen-block.jpg'); ?>" alt="A SociaLock blokkoló képernyője"></div>
    </div>
  </div>
</section>

<section class="feats" id="funkciok">
  <div class="wrap">
    <div class="center rv">
      <div class="kicker">Funkciók</div>
      <h2>Kicsi app, komoly önfegyelem.</h2>
    </div>
    <div class="feat-grid">
      <div class="card feat rv"><div class="ico">⏰</div><h3>Napi tiltási idősáv</h3><p>1 perctől 23 óráig, éjfélen átnyúlva is. Te döntöd el, mikor vagy a legsebezhetőbb.</p></div>
      <div class="card feat g rv"><div class="ico">🔒</div><h3>Csak szigorítani lehet</h3><p>Tiltás közben további appot felvehetsz, de egyet sem vehetsz le, és az idősáv sem rövidíthető.</p></div>
      <div class="card feat rv"><div class="ico">🛡️</div><h3>Eltávolítás elleni védelem</h3><p>Az eszközkezelő funkcióval az app nem törölhető egy mozdulattal egy gyenge pillanatban.</p></div>
      <div class="card feat g rv"><div class="ico">💻</div><h3>A számítógépen is</h3><p>Az ingyenes Windows-modul a telefonon beállított idősávban a böngészőkben is letiltja a megadott weboldalakat.</p></div>
      <div class="card feat rv"><div class="ico">👤</div><h3>Fiók nélkül</h3><p>Nincs regisztráció, nincs bejelentkezés. A szinkron névtelen, és bármikor törölheted.</p></div>
      <div class="card feat g rv"><div class="ico">🔐</div><h3>Adataid a telefonodon</h3><p>Az app nem olvassa az üzeneteidet. Csak azt nézi, melyik app kerül előtérbe – kizárólag a tiltáshoz.</p></div>
    </div>
  </div>
</section>

<section class="video">
  <div class="wrap center">
    <div class="rv">
      <div class="kicker">Videó</div>
      <h2>Egy reggel, amit senki sem szeretne.</h2>
      <p class="sub">Nézd meg, miért született a SociaLock.</p>
    </div>
    <div class="vbox rv">
      <?php if ($video): ?>
        <video src="<?php echo esc_url($video); ?>" controls playsinline preload="metadata"></video>
      <?php else: ?>
        <div><div class="play">▶</div>A videó hamarosan érkezik.</div>
      <?php endif; ?>
    </div>
  </div>
</section>

<section class="pc">
  <div class="wrap">
    <div class="card rv">
      <div class="ico">💻</div>
      <div>
        <h3 style="font-size:24px;margin:0 0 6px">Windows-modul</h3>
        <p>A telefonos appban bekapcsolt szinkron után a számítógéped is követi a tiltást: az idősávban a megadott weboldalak minden böngészőben elérhetetlenek. A telepítéshez az app <b>Számítógép</b> menüjében kapott párosítókód kell.</p>
      </div>
      <a class="btn btn-primary" href="<?php echo socialock_asset('SociaLock_windows_modul.zip'); ?>" download>Letöltés (ZIP)</a>
    </div>
  </div>
</section>

<section class="faq" id="gyik">
  <div class="wrap">
    <div class="center rv">
      <div class="kicker">GYIK</div>
      <h2>Gyakori kérdések</h2>
    </div>
    <div class="list">
      <details class="rv"><summary>Milyen telefonon működik?</summary><p>Androidon, a 8-as verziótól. iPhone-ra jelenleg nincs változat.</p></details>
      <details class="rv"><summary>Miért kéri a Kisegítő lehetőség engedélyét?</summary><p>Ebből tudja az app, hogy melyik alkalmazás kerül előtérbe, és csak így tud a tiltott app helyett blokkoló képernyőt mutatni. A SociaLock nem olvassa a képernyő tartalmát, az üzeneteidet és a beírt szöveget, és semmit nem küld el belőle.</p></details>
      <details class="rv"><summary>Fel lehet oldani a tiltást, ha nagyon kell?</summary><p>Ebben a változatban nem: az idősávban a tiltás tart, ki kell várni. Ezért érdemes reális idősávot választani. Az idősávon kívül bármit módosíthatsz.</p></details>
      <details class="rv"><summary>Mi történik az üzenetekkel, amiket közben kapok?</summary><p>Semmi különös: megérkeznek, csak a tiltás alatt nem nyithatod meg az appot. A tiltás vége után minden ott lesz.</p></details>
      <details class="rv"><summary>Kell hozzá fiók vagy regisztráció?</summary><p>Nem. A számítógépes szinkronhoz az app egy névtelen azonosítót hoz létre; ehhez sem név, sem e-mail, sem telefonszám nem kell. A szinkron bármikor kikapcsolható, ilyenkor a szerverről is törlődik.</p></details>
      <details class="rv"><summary>Mennyibe kerül?</summary><p>A SociaLock ingyenes.</p></details>
    </div>
  </div>
</section>

<section class="final">
  <div class="wrap">
    <h2 class="rv">Legyél ma este is az, aki holnap reggel szeretnél lenni.</h2>
    <p class="sub rv" style="margin:0 auto">Töltsd le, állítsd be két perc alatt, és hagyd, hogy a SociaLock vigyázzon rád, amikor te már nem tudsz.</p>
    <div class="cta-row rv"><?php socialock_play_button(); ?></div>
  </div>
</section>

<?php get_footer(); ?>
