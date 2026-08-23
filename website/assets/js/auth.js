/* ==========================================================================
   Bayo Bilingual Complex — connexion depuis le site institutionnel.

   Cette page s'adosse au système existant : elle appelle le même endpoint que
   l'application (POST /api/auth/login) et écrit exactement les mêmes clés de
   session que frontend/src/app/core/auth.service.ts. L'application, au
   chargement, restaure la session depuis ces clés — l'utilisateur arrive donc
   déjà connecté.

   CONTRAINTE À CONNAÎTRE : localStorage est cloisonné par origine. Le site et
   l'application doivent être servis depuis le MÊME hôte et le même port, sinon
   les jetons écrits ici sont invisibles pour l'application. Voir la section
   « Connexion » de website/README.md.
   ========================================================================== */
(function () {
  'use strict';

  // Mêmes clés que AuthService.persist() côté application. Les changer ici
  // sans les changer là-bas casserait silencieusement la session.
  var ACCESS_KEY = 'bbc-access';
  var REFRESH_KEY = 'bbc-refresh';
  var USER_KEY = 'bbc-user';
  var EXPIRES_KEY = 'bbc-access-expires-at';

  var form = document.querySelector('[data-login-form]');
  if (!form) return;

  var apiBase = (form.getAttribute('data-api-base') || '/api').replace(/\/$/, '');
  var appBase = form.getAttribute('data-app-base') || '/app/';
  if (appBase.slice(-1) !== '/') appBase += '/';

  var box = form.querySelector('[data-login-error]');
  var button = form.querySelector('[type="submit"]');
  var buttonLabel = button ? button.textContent : '';

  function fr() { return document.documentElement.getAttribute('lang') !== 'en'; }

  function showError(message) {
    if (!box) return;
    box.textContent = message;
    box.hidden = false;
  }

  function clearError() {
    if (box) { box.hidden = true; box.textContent = ''; }
  }

  function busy(state) {
    if (!button) return;
    button.disabled = state;
    button.textContent = state
      ? (fr() ? 'Connexion en cours…' : 'Signing in…')
      : buttonLabel;
  }

  /* Message d'erreur : on ne dit jamais si le compte existe. Le serveur ne le
     dit pas non plus — inutile de le déduire côté navigateur. */
  function messageFor(status) {
    if (status === 401 || status === 400) {
      return fr()
        ? 'Identifiant ou mot de passe incorrect.'
        : 'Incorrect username or password.';
    }
    if (status === 429) {
      return fr()
        ? 'Trop de tentatives. Patientez quelques minutes avant de réessayer.'
        : 'Too many attempts. Please wait a few minutes before trying again.';
    }
    if (status === 0) {
      return fr()
        ? 'Serveur injoignable. Vérifiez votre connexion, puis réessayez.'
        : 'Server unreachable. Check your connection, then try again.';
    }
    return fr()
      ? 'Le service de connexion est momentanément indisponible. Réessayez plus tard.'
      : 'The sign-in service is temporarily unavailable. Please try again later.';
  }

  function persist(res) {
    localStorage.setItem(ACCESS_KEY, res.accessToken);
    localStorage.setItem(REFRESH_KEY, res.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    localStorage.setItem(EXPIRES_KEY, String(Date.now() + res.expiresInMs));
  }

  /* Même aiguillage que l'écran de connexion de l'application : un parent va
     sur son portail, tout autre rôle passe par le choix du parcours. */
  function landingFor(user) {
    return appBase + (user && user.role === 'parent' ? 'parent' : 'parcours');
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    clearError();

    var username = (form.elements.username.value || '').trim();
    var password = form.elements.password.value || '';
    if (!username || !password) {
      showError(fr() ? 'Renseignez votre identifiant et votre mot de passe.'
                     : 'Enter your username and your password.');
      return;
    }

    busy(true);

    // Promise.resolve() en tête : si fetch échoue de façon synchrone (API
    // absente, argument refusé), l'exception devient un rejet traité plus bas.
    // Sans cela le bouton resterait bloqué sur « Connexion en cours… ».
    Promise.resolve()
      .then(function () {
        return fetch(apiBase + '/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
          body: JSON.stringify({ username: username, password: password })
        });
      })
      .then(function (response) {
        if (!response.ok) {
          var status = response.status;
          return response.json().catch(function () { return null; })
            .then(function () { throw { status: status }; });
        }
        return response.json();
      })
      .then(function (res) {
        if (!res || !res.accessToken || !res.refreshToken || !res.user) {
          throw { status: -1 };
        }
        persist(res);

        var target = landingFor(res.user);
        // Évènement annulable : les tests s'y accrochent pour vérifier la
        // session sans déclencher de navigation.
        var evt = new CustomEvent('bbc:authenticated', {
          detail: { user: res.user, target: target },
          cancelable: true
        });
        if (document.dispatchEvent(evt)) window.location.assign(target);
      })
      .catch(function (err) {
        busy(false);
        var status = err && typeof err.status === 'number' ? err.status : 0;
        showError(messageFor(status));
        var pwd = form.elements.password;
        if (pwd) { pwd.value = ''; pwd.focus(); }
      });
  });

  /* Afficher / masquer le mot de passe — un parent qui saisit sur téléphone
     doit pouvoir vérifier ce qu'il tape. */
  var toggle = document.querySelector('[data-toggle-password]');
  if (toggle) {
    toggle.addEventListener('click', function () {
      var input = form.elements.password;
      var shown = input.type === 'text';
      input.type = shown ? 'password' : 'text';
      toggle.setAttribute('aria-pressed', String(!shown));
      toggle.textContent = shown ? (fr() ? 'Afficher' : 'Show') : (fr() ? 'Masquer' : 'Hide');
    });
    document.addEventListener('bbc:langchange', function () {
      var shown = form.elements.password.type === 'text';
      toggle.textContent = shown ? (fr() ? 'Masquer' : 'Hide') : (fr() ? 'Afficher' : 'Show');
    });
  }

  /* ------------------------------------------------ mot de passe oublié --
     Même endpoint que l'application. La réponse est volontairement identique
     que le compte existe ou non : on ne confirme jamais l'existence d'un
     identifiant à un visiteur non authentifié.
  ------------------------------------------------------------------------ */
  var forgotForm = document.querySelector('[data-forgot-form]');
  if (forgotForm) {
    var forgotBase = (forgotForm.getAttribute('data-api-base') || apiBase).replace(/\/$/, '');
    var forgotNote = forgotForm.querySelector('[data-forgot-feedback]');
    var forgotButton = forgotForm.querySelector('[type="submit"]');

    forgotForm.addEventListener('submit', function (event) {
      event.preventDefault();
      var username = (forgotForm.elements.username.value || '').trim();
      if (!username) return;

      if (forgotButton) forgotButton.disabled = true;

      fetch(forgotBase + '/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify({ username: username })
      })
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (res) {
          if (!forgotNote) return;
          forgotNote.hidden = false;
          forgotNote.textContent = (res && res.message) || (fr()
            ? 'Si ce compte existe, la marche à suivre vient d’être envoyée.'
            : 'If that account exists, instructions have just been sent.');
        })
        .catch(function () {
          if (!forgotNote) return;
          forgotNote.hidden = false;
          forgotNote.textContent = fr()
            ? 'Serveur injoignable. Réessayez plus tard ou appelez le secrétariat.'
            : 'Server unreachable. Try again later or call the school office.';
        })
        .then(function () { if (forgotButton) forgotButton.disabled = false; });
    });
  }

  // Le libellé du bouton change avec la langue : on garde la référence à jour.
  document.addEventListener('bbc:langchange', function () {
    if (button && !button.disabled) buttonLabel = button.textContent;
  });
})();
