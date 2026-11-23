<footer class="foot">
  <div class="wrap">
    <div>
      <a href="<?php echo esc_url(home_url('/adatvedelem/')); ?>">Adatvédelmi tájékoztató</a>
      <?php if ($m = get_theme_mod('socialock_contact')): ?><a href="mailto:<?php echo esc_attr($m); ?>"><?php echo esc_html($m); ?></a><?php endif; ?>
      <a href="<?php echo esc_url(home_url('/#gyik')); ?>">GYIK</a>
    </div>
    <div>© <?php echo date('Y'); ?> E-WEB Consulting Kft. · All Rights Reserved</div>
  </div>
</footer>
<script>
(function(){var io=('IntersectionObserver' in window)?new IntersectionObserver(function(es){es.forEach(function(e){if(e.isIntersecting){e.target.classList.add('in');io.unobserve(e.target);}});},{threshold:.12}):null;
document.querySelectorAll('.rv').forEach(function(el){io?io.observe(el):el.classList.add('in');});})();
</script>
<?php wp_footer(); ?>
</body>
</html>
