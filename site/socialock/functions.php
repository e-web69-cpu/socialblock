<?php
/** SociaLock sablon – alapbeállítások. */
add_action('after_setup_theme', function () {
    add_theme_support('title-tag');
    add_theme_support('html5', ['search-form', 'gallery', 'caption', 'style', 'script']);
    register_nav_menus(['footer' => 'Lábléc menü']);
});

add_action('wp_enqueue_scripts', function () {
    wp_enqueue_style('socialock', get_stylesheet_uri(), [], wp_get_theme()->get('Version'));
});

/** Testreszabó: Google Play-link és PAS-videó címe (üresen: „Hamarosan” gomb, illetve helyőrző). */
add_action('customize_register', function ($c) {
    $c->add_section('socialock', ['title' => 'SociaLock', 'priority' => 30]);
    foreach ([
        'socialock_play_url'  => 'Google Play-link (üresen: „Hamarosan”)',
        'socialock_video_url' => 'PAS-videó MP4 címe',
        'socialock_contact'   => 'Kapcsolati e-mail',
    ] as $id => $label) {
        $c->add_setting($id, ['default' => '', 'sanitize_callback' => $id === 'socialock_contact' ? 'sanitize_email' : 'esc_url_raw']);
        $c->add_control($id, ['label' => $label, 'section' => 'socialock', 'type' => $id === 'socialock_contact' ? 'email' : 'url']);
    }
});

function socialock_asset($f) { return esc_url(get_template_directory_uri() . '/assets/' . $f); }

/** Google Play gomb: link esetén letöltés, különben „Hamarosan”. */
function socialock_play_button() {
    $url = get_theme_mod('socialock_play_url');
    $icon = '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#34A853" d="M3.6 1.9 13.4 12l-9.8 10.1c-.4-.2-.6-.7-.6-1.2V3.1c0-.5.2-1 .6-1.2z"/><path fill="#FBBC04" d="m17 8.4-3.6 3.6 3.6 3.6 4.1-2.3c1.1-.6 1.1-2.1 0-2.7L17 8.4z"/><path fill="#EA4335" d="M13.4 12 3.6 22.1c.4.2.9.2 1.4-.1L17 15.6 13.4 12z"/><path fill="#4285F4" d="M13.4 12 17 8.4 5 2c-.5-.3-1-.3-1.4-.1L13.4 12z"/></svg>';
    if ($url) {
        printf('<a class="btn btn-play" href="%s" target="_blank" rel="noopener">%s<div><small>Töltsd le a</small><span>Google Playről</span></div></a>', esc_url($url), $icon);
    } else {
        printf('<span class="btn btn-play soon">%s<div><small>Hamarosan a</small><span>Google Playen</span></div></span>', $icon);
    }
}
