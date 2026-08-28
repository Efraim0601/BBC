(() => {
  const supported = new Set(['fr', 'en']);
  const stored = localStorage.getItem('bbc-guide-lang');
  const browser = (navigator.language || '').toLowerCase().startsWith('fr') ? 'fr' : 'en';
  const initial = supported.has(stored) ? stored : browser;

  function setLang(lang) {
    if (!supported.has(lang)) return;
    document.documentElement.dataset.lang = lang;
    document.documentElement.lang = lang;
    localStorage.setItem('bbc-guide-lang', lang);
    document.querySelectorAll('[data-set-lang]').forEach((button) => {
      const active = button.dataset.setLang === lang;
      button.classList.toggle('active', active);
      button.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
  }

  document.querySelectorAll('[data-set-lang]').forEach((button) => {
    button.addEventListener('click', () => setLang(button.dataset.setLang));
  });
  setLang(initial);
})();
