<?php
/**
 * Plugin Name: SociaLock – sütimentes oldalmegtekintés-számláló
 * Description: Hozzájárulástól függetlenül számolja az oldalmegtekintéseket, süti és személyes adat nélkül (nem tárol IP-t, azonosítót). Csak összesített napi számokat ment: oldal, forrás-domain, eszköztípus. Admin: Eszközök → SociaLock látogatás.
 * Author: SociaLock
 */
if (!defined('ABSPATH')) exit;

function slk_pv_bot(string $ua): bool {
    return $ua === '' || (bool) preg_match('/bot|crawl|spider|slurp|preview|facebookexternalhit|whatsapp|telegram|discord|headless|lighthouse|python|curl|wget|monitor|uptime/i', $ua);
}

function slk_pv_record(): void {
    $ua = (string) ($_SERVER['HTTP_USER_AGENT'] ?? '');
    if (slk_pv_bot($ua) || is_user_logged_in()) { wp_send_json(['ok' => 0]); }
    $path = substr(preg_replace('/[^a-zA-Z0-9\/_\-\.\?=&#%]/', '', (string) ($_POST['p'] ?? '/')), 0, 80);
    $path = preg_replace('/[?&](nc|nocache|gatest|fbclid|gclid)=[^&]*/', '', $path);
    if ($path === '') $path = '/';
    $ref = strtolower(substr(preg_replace('/[^a-zA-Z0-9\.\-]/', '', (string) ($_POST['r'] ?? '')), 0, 60));
    if ($ref === '' || $ref === 'socialock.hu' || $ref === 'www.socialock.hu') $ref = '(közvetlen)';
    $dev = preg_match('/Mobi|Android|iPhone|iPad/i', $ua) ? 'mobil' : 'gép';
    $day = wp_date('Y-m-d');
    $key = 'slk_pv_' . substr($day, 0, 7);
    $d = get_option($key, []);
    if (!is_array($d)) $d = [];
    $d[$day]['total'] = ($d[$day]['total'] ?? 0) + 1;
    $d[$day]['path'][$path] = ($d[$day]['path'][$path] ?? 0) + 1;
    $d[$day]['ref'][$ref] = ($d[$day]['ref'][$ref] ?? 0) + 1;
    $d[$day]['dev'][$dev] = ($d[$day]['dev'][$dev] ?? 0) + 1;
    update_option($key, $d, false);
    wp_send_json(['ok' => 1]);
}
add_action('wp_ajax_nopriv_slk_pv', 'slk_pv_record');
add_action('wp_ajax_slk_pv', 'slk_pv_record');

add_action('wp_footer', function () {
    if (is_admin() || is_user_logged_in()) return;
    $u = esc_url(admin_url('admin-ajax.php'));
    echo "<script>(function(){try{var f=new FormData();f.append('action','slk_pv');f.append('p',location.pathname+location.search);var r='';try{r=document.referrer?new URL(document.referrer).hostname:'';}catch(e){}f.append('r',r);if(navigator.sendBeacon){navigator.sendBeacon('{$u}',f);}else{fetch('{$u}',{method:'POST',body:f,keepalive:true});}}catch(e){}})();</script>\n";
}, 100);

add_action('admin_menu', function () {
    add_management_page('SociaLock látogatás', 'SociaLock látogatás', 'manage_options', 'slk-pv', function () {
        $days = [];
        for ($i = 0; $i < 3; $i++) {
            $m = wp_date('Y-m', strtotime("-$i month"));
            $d = get_option('slk_pv_' . $m, []);
            if (is_array($d)) $days += $d;
        }
        krsort($days);
        $sum = function (array $rows, string $k): array { $o = []; foreach ($rows as $r) foreach (($r[$k] ?? []) as $n => $c) $o[$n] = ($o[$n] ?? 0) + $c; arsort($o); return $o; };
        $last30 = array_slice($days, 0, 30, true);
        $tot = array_sum(array_map(fn($r) => $r['total'] ?? 0, $last30));
        echo '<div class="wrap"><h1>SociaLock látogatás (sütimentes számláló)</h1>';
        echo '<p>Minden oldalmegtekintés, hozzájárulástól függetlenül, süti és személyes adat nélkül. Robotok és bejelentkezett felhasználók nélkül. Utolsó 30 nap: <strong>' . intval($tot) . '</strong> megtekintés.</p>';
        echo '<h2>Napi bontás</h2><table class="widefat striped" style="max-width:520px"><thead><tr><th>Nap</th><th>Megtekintés</th><th>Mobil</th><th>Gép</th></tr></thead><tbody>';
        foreach ($last30 as $day => $r) echo '<tr><td>' . esc_html($day) . '</td><td>' . intval($r['total'] ?? 0) . '</td><td>' . intval($r['dev']['mobil'] ?? 0) . '</td><td>' . intval($r['dev']['gép'] ?? 0) . '</td></tr>';
        echo '</tbody></table>';
        foreach (['ref' => 'Honnan jöttek (30 nap)', 'path' => 'Oldalak (30 nap)'] as $k => $t) {
            echo '<h2>' . esc_html($t) . '</h2><table class="widefat striped" style="max-width:520px"><tbody>';
            foreach (array_slice($sum($last30, $k), 0, 20, true) as $n => $c) echo '<tr><td>' . esc_html($n) . '</td><td>' . intval($c) . '</td></tr>';
            echo '</tbody></table>';
        }
        echo '</div>';
    });
});
