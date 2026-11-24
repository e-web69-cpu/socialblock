<?php
/**
 * Plugin Name: SociaLock SEO + AI
 * Description: Cím, meta leírás, megosztási kártya (Open Graph / X) és strukturált adat (JSON-LD) a socialock.hu oldalhoz. Az indexelést (noindex) NEM állítja: azt a WordPress „Olvasás” beállítása vezérli.
 * Author: SociaLock
 */
if (!defined('ABSPATH')) exit;

const SLK_SEO_DESC  = 'Ne küldd el, amit reggel megbánnál: a SociaLock idősávban tiltja a Messengert, Facebookot, Instagramot, TikTokot, e-mailt. Kevesebb képernyőidő, szülői felügyelet, Android + Windows. Ingyenes.';
const SLK_SEO_TITLE = 'SociaLock | Önkorlátozás józan ésszel az online térben';
const SLK_SEO_IMG   = 'https://socialock.hu/wp-content/uploads/2026/11/socialock-og.jpg';

function slk_seo_is_home(): bool { return is_front_page() || is_home(); }

add_filter('pre_get_document_title', function ($t) {
    if (slk_seo_is_home()) return SLK_SEO_TITLE;
    if (is_page()) return single_post_title('', false) . ' | SociaLock';
    return $t;
}, 99);

add_action('wp_head', function () {
    $home = slk_seo_is_home();
    $url  = $home ? home_url('/') : get_permalink();
    $title = $home ? SLK_SEO_TITLE : (single_post_title('', false) . ' | SociaLock');
    $desc = $home ? SLK_SEO_DESC : 'A SociaLock adatkezelési tájékoztatója: milyen adatot kezel az app, a számítógépes szinkron és az előregisztráció.';
    $e = fn($s) => esc_attr($s);
    echo "\n<!-- SociaLock SEO -->\n";
    echo '<meta name="description" content="' . $e($desc) . "\">\n";
    echo '<meta name="theme-color" content="#2D65F8">' . "\n";
    echo '<meta property="og:type" content="website">' . "\n";
    echo '<meta property="og:locale" content="hu_HU">' . "\n";
    echo '<meta property="og:site_name" content="SociaLock">' . "\n";
    echo '<meta property="og:title" content="' . $e($title) . "\">\n";
    echo '<meta property="og:description" content="' . $e($desc) . "\">\n";
    echo '<meta property="og:url" content="' . $e($url) . "\">\n";
    echo '<meta property="og:image" content="' . SLK_SEO_IMG . "\">\n";
    echo '<meta property="og:image:width" content="1200"><meta property="og:image:height" content="630">' . "\n";
    echo '<meta property="og:image:alt" content="SociaLock – Amit este elküldesz, reggel már bánod.">' . "\n";
    echo '<meta name="twitter:card" content="summary_large_image">' . "\n";
    echo '<meta name="twitter:title" content="' . $e($title) . "\">\n";
    echo '<meta name="twitter:description" content="' . $e($desc) . "\">\n";
    echo '<meta name="twitter:image" content="' . SLK_SEO_IMG . "\">\n";
    echo '<link rel="alternate" type="text/plain" title="llms.txt" href="https://socialock.hu/llms.txt">' . "\n";

    $graph = [[
        '@type' => 'WebSite', '@id' => 'https://socialock.hu/#website', 'url' => 'https://socialock.hu/',
        'name' => 'SociaLock', 'inLanguage' => 'hu-HU',
    ]];
    if ($home) {
        $graph[] = [
            '@type' => 'WebPage', '@id' => 'https://socialock.hu/#webpage', 'url' => 'https://socialock.hu/',
            'name' => SLK_SEO_TITLE, 'description' => SLK_SEO_DESC, 'inLanguage' => 'hu-HU',
            'isPartOf' => ['@id' => 'https://socialock.hu/#website'], 'about' => ['@id' => 'https://socialock.hu/#app'],
            'primaryImageOfPage' => SLK_SEO_IMG,
        ];
        $graph[] = [
            '@type' => 'MobileApplication', '@id' => 'https://socialock.hu/#app',
            'name' => 'SociaLock', 'alternateName' => 'SociaLock – Önkorlátozás józan ésszel',
            'description' => SLK_SEO_DESC, 'operatingSystem' => 'Android 8.0 vagy újabb',
            'applicationCategory' => 'LifestyleApplication', 'softwareVersion' => '1.12 beta',
            'inLanguage' => 'hu', 'image' => SLK_SEO_IMG, 'url' => 'https://socialock.hu/',
            'offers' => ['@type' => 'Offer', 'price' => '0', 'priceCurrency' => 'HUF', 'description' => 'Az alap tiltás ingyenes.'],
            'featureList' => [
                'Napi tiltási idősáv, legfeljebb 23 óra',
                'Bármelyik telepített alkalmazás tiltható (közösségi, e-mail, videó, játék, böngésző)',
                'Tiltás közben csak szigorítani lehet',
                'Eltávolítás elleni védelem eszközkezelővel',
                'Ingyenes Windows-modul: a böngészőben is tiltja a megadott oldalakat ugyanabban az idősávban',
                'Fiók és regisztráció nélkül',
            ],
        ];
        $faq = [
            ['Milyen telefonon működik?', 'Androidon, a 8-as verziótól. iPhone-ra jelenleg nincs változat.'],
            ['Miért kéri a Kisegítő lehetőség engedélyét?', 'Ebből tudja az app, hogy melyik alkalmazás kerül előtérbe, és csak így tud a tiltott app helyett blokkoló képernyőt mutatni. A SociaLock nem olvassa a képernyő tartalmát, az üzeneteidet és a beírt szöveget, és semmit nem küld el belőle.'],
            ['Fel lehet oldani a tiltást, ha nagyon kell?', 'Ebben a változatban nem: az idősávban a tiltás tart, ki kell várni. Ezért érdemes reális idősávot választani. Az idősávon kívül bármit módosíthatsz.'],
            ['Mi történik az üzenetekkel, amiket közben kapok?', 'Semmi különös: megérkeznek, csak a tiltás alatt nem nyithatod meg az appot. A tiltás vége után minden ott vár.'],
            ['Kell hozzá fiók vagy regisztráció?', 'Nem. A számítógépes szinkronhoz az app egy névtelen azonosítót hoz létre; ehhez sem név, sem e-mail, sem telefonszám nem kell. A szinkron bármikor kikapcsolható, ilyenkor a szerverről is törlődik.'],
            ['Csak közösségi és e-mail appokat lehet tiltani?', 'Nem. A telefonodra telepített bármelyik alkalmazást felveheted a listára: videó- és streamingappokat, játékokat, böngészőt is.'],
            ['Használhatom a gyerekem telefonján?', 'Igen. A szülő beállíthatja a gyerek telefonján a tiltási idősávot és az appokat. Az idősávban a tiltás nem lazítható és nem kapcsolható ki, az eszközkezelő funkció pedig megnehezíti az app eltávolítását.'],
            ['Mennyibe kerül?', 'Az alap tiltás ingyenes.'],
            ['Mikor lesz letölthető?', 'Most készítjük elő a Google Play-megjelenést. Iratkozz fel az előregisztrációra, és szólunk, amint elérhető.'],
        ];
        $graph[] = [
            '@type' => 'FAQPage', '@id' => 'https://socialock.hu/#faq', 'inLanguage' => 'hu-HU',
            'mainEntity' => array_map(fn($q) => ['@type' => 'Question', 'name' => $q[0], 'acceptedAnswer' => ['@type' => 'Answer', 'text' => $q[1]]], $faq),
        ];
    }
    echo '<script type="application/ld+json">' . wp_json_encode(['@context' => 'https://schema.org', '@graph' => $graph], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) . "</script>\n";
}, 1);
