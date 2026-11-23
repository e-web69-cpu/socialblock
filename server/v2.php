<?php
/**
 * SociaLock szinkron API v2 – több felhasználós (fiókonkénti) változat.
 * A régi api.php (tulajdonosi, egy állapotos) érintetlen marad; ez külön végpont.
 *
 * Tárolás fájlokban (pdo_sqlite nincs a szerveren), a webgyökéren KÍVÜL: ../socialock-private/v2/
 *   accounts/<id>.json   – az állapot (rules, created, updated)
 *   keys/<sha256>.json   – kulcs → {acct, role: master|read}  (a kulcs maga nincs tárolva, csak a hash)
 *   pair/<code>.json     – párosítókód → {acct, exp}
 *   rl/<iphash>.json     – regisztrációs sebességkorlát
 *
 * Végpontok (JSON):
 *   POST ?r=register                 → {account, master}           (napi/IP korlát)
 *   POST ?r=rules   X-Key: master     body {enabled, windows[], apps[], sites[]}
 *   GET  ?r=state   X-Key: master|read → {server_time, rules, blocking_window_now, unlocked:false}
 *   POST ?r=pair    X-Key: master     → {code, expires_in}         (8 karakter, 10 perc, egyszer használható)
 *   POST ?r=claim   body {code}       → {read}                     (eszközönként új olvasókulcs)
 *   POST ?r=delete  X-Key: master     → fiók és összes kulcsa törölve
 */
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
date_default_timezone_set('Europe/Budapest');

const MAX_WINDOW_MIN = 23 * 60;
const PAIR_TTL = 600;
const REG_PER_IP_DAY = 10;

$DATA = dirname(__DIR__) . '/socialock-private/v2';
foreach (['accounts', 'keys', 'pair', 'rl'] as $d) { if (!is_dir("$DATA/$d")) @mkdir("$DATA/$d", 0700, true); }

function out(int $code, array $body): void { http_response_code($code); echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); exit; }
function rjson(string $f): ?array { if (!is_file($f)) return null; $j = json_decode((string)file_get_contents($f), true); return is_array($j) ? $j : null; }
function wjson(string $f, array $d): void { $t = $f . '.tmp' . bin2hex(random_bytes(3)); file_put_contents($t, json_encode($d, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), LOCK_EX); rename($t, $f); }
function khash(string $k): string { return hash('sha256', 'socialock-v2|' . $k); }
function newkey(): string { return rtrim(strtr(base64_encode(random_bytes(24)), '+/', '-_'), '='); }
function body(): array { $j = json_decode((string)file_get_contents('php://input'), true); return is_array($j) ? $j : []; }

function auth(string $DATA, array $roles): array {
    $k = $_SERVER['HTTP_X_KEY'] ?? '';
    if ($k === '' || strlen($k) > 200) out(401, ['error' => 'missing key']);
    $rec = rjson("$DATA/keys/" . khash($k) . '.json');
    if (!$rec || !in_array($rec['role'] ?? '', $roles, true)) out(401, ['error' => 'bad key']);
    $acct = rjson("$DATA/accounts/" . $rec['acct'] . '.json');
    if (!$acct) out(401, ['error' => 'no account']);
    return [$rec, $acct];
}

function hm(string $s): ?int { return preg_match('/^([01]\d|2[0-3]):([0-5]\d)$/', $s, $m) ? ((int)$m[1]) * 60 + (int)$m[2] : null; }

function validRules(array $b): ?array {
    $enabled = (bool)($b['enabled'] ?? false);
    $wins = [];
    foreach (($b['windows'] ?? []) as $w) {
        if (!is_array($w)) return null;
        $s = hm((string)($w['start'] ?? '')); $e = hm((string)($w['end'] ?? ''));
        if ($s === null || $e === null || $s === $e) return null;
        $len = ($e - $s + 1440) % 1440;
        if ($len > MAX_WINDOW_MIN) return null;
        $days = array_values(array_unique(array_filter(array_map('intval', (array)($w['days'] ?? [])), fn($d) => $d >= 1 && $d <= 7)));
        sort($days);
        if (!$days) return null;
        $wins[] = ['days' => $days, 'start' => $w['start'], 'end' => $w['end']];
    }
    if (count($wins) > 7) return null;
    $clean = function ($a, $re) { $o = []; foreach ((array)$a as $x) { $x = strtolower(trim((string)$x)); if ($x !== '' && strlen($x) <= 200 && preg_match($re, $x)) $o[$x] = 1; } $o = array_keys($o); sort($o); return array_slice($o, 0, 300); };
    return ['enabled' => $enabled, 'windows' => $wins,
        'apps' => $clean($b['apps'] ?? [], '/^[a-z0-9_.]+$/'),
        'sites' => $clean($b['sites'] ?? [], '/^[a-z0-9.-]+$/')];
}

/** Igaz, ha most tiltási idő van ezekkel a szabályokkal (1 = vasárnap … 7 = szombat). */
function blockingNow(array $r, ?int $ts = null): bool {
    if (empty($r['enabled'])) return false;
    $ts = $ts ?? time();
    $dow = (int)date('w', $ts) + 1; $prev = $dow === 1 ? 7 : $dow - 1;
    $now = (int)date('G', $ts) * 60 + (int)date('i', $ts);
    foreach ($r['windows'] ?? [] as $w) {
        $s = hm($w['start']); $e = hm($w['end']);
        if ($s < $e) { if (in_array($dow, $w['days'], true) && $now >= $s && $now < $e) return true; }
        else { if ((in_array($dow, $w['days'], true) && $now >= $s) || (in_array($prev, $w['days'], true) && $now < $e)) return true; }
    }
    return false;
}

/** Tiltási időben csak szigorítani lehet: bekapcsolva marad, appok/oldalak csak bővülhetnek. Az idősáv bármikor módosítható, azonnal szinkronba kerül. */
function onlyTightens(array $old, array $new): bool {
    if (empty($new['enabled'])) return false;
    if (array_diff($old['apps'], $new['apps'])) return false;
    if (array_diff($old['sites'], $new['sites'])) return false;
    return true;
}

$r = $_GET['r'] ?? '';
$m = $_SERVER['REQUEST_METHOD'];

if ($r === 'register' && $m === 'POST') {
    $ipf = "$DATA/rl/" . hash('sha256', ($_SERVER['REMOTE_ADDR'] ?? '') . date('Y-m-d')) . '.json';
    $rl = rjson($ipf) ?? ['n' => 0];
    if ($rl['n'] >= REG_PER_IP_DAY) out(429, ['error' => 'too many registrations']);
    $rl['n']++; wjson($ipf, $rl);
    $id = bin2hex(random_bytes(8));
    $master = newkey();
    wjson("$DATA/accounts/$id.json", ['id' => $id, 'created' => time(), 'updated' => 0,
        'rules' => ['enabled' => false, 'windows' => [], 'apps' => [], 'sites' => []]]);
    wjson("$DATA/keys/" . khash($master) . '.json', ['acct' => $id, 'role' => 'master']);
    out(200, ['account' => $id, 'master' => $master]);
}

if ($r === 'rules' && $m === 'POST') {
    [$k, $a] = auth($DATA, ['master']);
    $new = validRules(body());
    if ($new === null) out(400, ['error' => 'invalid rules']);
    if (blockingNow($a['rules']) && !onlyTightens($a['rules'], $new)) out(409, ['error' => 'locked during blocking window']);
    $a['rules'] = $new; $a['updated'] = time();
    wjson("$DATA/accounts/{$a['id']}.json", $a);
    out(200, ['ok' => true]);
}

if ($r === 'state' && $m === 'GET') {
    [$k, $a] = auth($DATA, ['master', 'read']);
    out(200, ['server_time' => date('c'), 'rules' => $a['rules'], 'blocking_window_now' => blockingNow($a['rules']),
        'unlocked' => false, 'unlocked_until' => null, 'updated' => $a['updated']]);
}

if ($r === 'pair' && $m === 'POST') {
    [$k, $a] = auth($DATA, ['master']);
    $alpha = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    $code = ''; for ($i = 0; $i < 8; $i++) $code .= $alpha[random_int(0, strlen($alpha) - 1)];
    wjson("$DATA/pair/$code.json", ['acct' => $a['id'], 'exp' => time() + PAIR_TTL]);
    out(200, ['code' => $code, 'expires_in' => PAIR_TTL]);
}

if ($r === 'claim' && $m === 'POST') {
    $code = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', (string)(body()['code'] ?? '')));
    if (strlen($code) !== 8) out(400, ['error' => 'bad code']);
    $f = "$DATA/pair/$code.json";
    $p = rjson($f);
    @unlink($f);
    if (!$p || $p['exp'] < time() || !is_file("$DATA/accounts/{$p['acct']}.json")) out(404, ['error' => 'code expired or unknown']);
    $read = newkey();
    wjson("$DATA/keys/" . khash($read) . '.json', ['acct' => $p['acct'], 'role' => 'read']);
    out(200, ['read' => $read]);
}

if ($r === 'delete' && $m === 'POST') {
    [$k, $a] = auth($DATA, ['master']);
    foreach (glob("$DATA/keys/*.json") ?: [] as $f) { $x = rjson($f); if (($x['acct'] ?? '') === $a['id']) @unlink($f); }
    foreach (glob("$DATA/pair/*.json") ?: [] as $f) { $x = rjson($f); if (($x['acct'] ?? '') === $a['id']) @unlink($f); }
    @unlink("$DATA/accounts/{$a['id']}.json");
    out(200, ['ok' => true]);
}

// lejárt párosítókódok takarítása
foreach (glob("$DATA/pair/*.json") ?: [] as $f) { $x = rjson($f); if (!$x || $x['exp'] < time()) @unlink($f); }
out(404, ['error' => 'unknown route']);
