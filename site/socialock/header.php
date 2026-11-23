<!doctype html>
<html <?php language_attributes(); ?>>
<head>
<meta charset="<?php bloginfo('charset'); ?>">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="SociaLock: Android app, amely az általad megadott idősávban letiltja a közösségi és e-mail appokat – hogy az esti, meggondolatlan üzenetek el se menjenek.">
<link rel="icon" href="<?php echo socialock_asset('logo.svg'); ?>" type="image/svg+xml">
<?php wp_head(); ?>
</head>
<body <?php body_class(); ?>>
<?php wp_body_open(); ?>
<header class="top">
  <div class="wrap">
    <a class="brand" href="<?php echo esc_url(home_url('/')); ?>"><img src="<?php echo socialock_asset('logo.svg'); ?>" alt=""><span>Socia<b>Lock</b></span></a>
    <nav class="nav">
      <a href="<?php echo esc_url(home_url('/#hogyan')); ?>">Hogyan működik</a>
      <a href="<?php echo esc_url(home_url('/#funkciok')); ?>">Funkciók</a>
      <a href="<?php echo esc_url(home_url('/#gyik')); ?>">GYIK</a>
      <a class="btn btn-primary" href="<?php echo esc_url(home_url('/#letoltes')); ?>">Letöltés</a>
    </nav>
  </div>
</header>
