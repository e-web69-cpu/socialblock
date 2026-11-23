<?php
/**
 * Plugin Name: SociaLock előregisztráció
 * Description: Értesítési feliratkozás az app megjelenéséről (e-mail + hozzájárulás). Lista és CSV-export: Eszközök → SociaLock előregisztrációk.
 * Author: E-WEB Consulting Kft.
 */
if (!defined('ABSPATH')) exit;

add_action('init', function () {
    register_post_type('sl_prereg', ['public' => false, 'show_ui' => false, 'supports' => ['title']]);
});

function sl_prereg_handle() {
    $back = wp_get_referer() ?: home_url('/');
    $back = remove_query_arg('ertesites', $back);
    if (!empty($_POST['website'])) { wp_safe_redirect(add_query_arg('ertesites', 'ok', $back) . '#ertesites'); exit; } // csapda robotoknak
    $email = sanitize_email(wp_unslash($_POST['email'] ?? ''));
    if (!is_email($email) || empty($_POST['consent'])) { wp_safe_redirect(add_query_arg('ertesites', 'hiba', $back) . '#ertesites'); exit; }
    $exists = get_posts(['post_type' => 'sl_prereg', 'title' => $email, 'post_status' => 'private', 'numberposts' => 1, 'fields' => 'ids']);
    if (!$exists) {
        $id = wp_insert_post(['post_type' => 'sl_prereg', 'post_title' => $email, 'post_status' => 'private']);
        if ($id) update_post_meta($id, 'consent_at', current_time('mysql'));
    }
    wp_safe_redirect(add_query_arg('ertesites', 'ok', $back) . '#ertesites');
    exit;
}
add_action('admin_post_nopriv_sl_prereg', 'sl_prereg_handle');
add_action('admin_post_sl_prereg', 'sl_prereg_handle');

add_action('admin_menu', function () {
    add_management_page('SociaLock előregisztrációk', 'SociaLock előregisztrációk', 'manage_options', 'sl-prereg', 'sl_prereg_admin');
});

add_action('admin_init', function () {
    if (($_GET['page'] ?? '') === 'sl-prereg' && isset($_GET['csv']) && current_user_can('manage_options')) {
        check_admin_referer('sl_prereg_csv');
        header('Content-Type: text/csv; charset=utf-8');
        header('Content-Disposition: attachment; filename="socialock-elore-regisztraciok.csv"');
        $out = fopen('php://output', 'w');
        fputcsv($out, ['email', 'hozzajarulas']);
        foreach (get_posts(['post_type' => 'sl_prereg', 'post_status' => 'private', 'numberposts' => -1]) as $p) fputcsv($out, [$p->post_title, get_post_meta($p->ID, 'consent_at', true)]);
        exit;
    }
    if (($_GET['page'] ?? '') === 'sl-prereg' && isset($_GET['del']) && current_user_can('manage_options')) {
        check_admin_referer('sl_prereg_del');
        $p = get_post((int) $_GET['del']);
        if ($p && $p->post_type === 'sl_prereg') wp_delete_post($p->ID, true);
        wp_safe_redirect(admin_url('tools.php?page=sl-prereg')); exit;
    }
});

function sl_prereg_admin() {
    $rows = get_posts(['post_type' => 'sl_prereg', 'post_status' => 'private', 'numberposts' => -1]);
    echo '<div class="wrap"><h1>SociaLock előregisztrációk (' . count($rows) . ')</h1>';
    echo '<p><a class="button button-primary" href="' . esc_url(wp_nonce_url(admin_url('tools.php?page=sl-prereg&csv=1'), 'sl_prereg_csv')) . '">CSV letöltése</a></p>';
    echo '<table class="widefat striped"><thead><tr><th>E-mail</th><th>Hozzájárulás ideje</th><th></th></tr></thead><tbody>';
    foreach ($rows as $p) {
        echo '<tr><td>' . esc_html($p->post_title) . '</td><td>' . esc_html(get_post_meta($p->ID, 'consent_at', true)) . '</td><td><a href="' . esc_url(wp_nonce_url(admin_url('tools.php?page=sl-prereg&del=' . $p->ID), 'sl_prereg_del')) . '">Törlés</a></td></tr>';
    }
    echo '</tbody></table></div>';
}
