<?php get_header(); ?>
<div class="page-hero"><div class="wrap"><h1><?php is_singular() ? single_post_title() : bloginfo('name'); ?></h1></div></div>
<main class="prose">
<?php while (have_posts()) { the_post(); the_content(); } ?>
</main>
<?php get_footer(); ?>
