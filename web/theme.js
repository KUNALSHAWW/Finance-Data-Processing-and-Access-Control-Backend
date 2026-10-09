// Runs before first paint so there is no flash of the wrong theme.
(function () {
  var t = null;
  try { t = localStorage.getItem('theme'); } catch (e) { /* storage blocked: fall back to the OS setting */ }
  if (t !== 'dark' && t !== 'light') {
    t = window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  document.documentElement.setAttribute('data-theme', t);
})();
