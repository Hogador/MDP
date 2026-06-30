/* ============================================================
   MDAOPay — Shared Notification Helper
   Drop-in: <script src="notif.js"></script>
   Then call: MDAONotif.show({ type, title, sub, ... })
   ============================================================ */

(function(){
  'use strict';

  const ICONS = {
    success: '<svg viewBox="0 0 24 24"><path d="M5 12l5 5L20 7"/></svg>',
    error:   '<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg>',
    info:    '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" stroke-width="2.4"/><path d="M12 11v5"/><circle cx="12" cy="8" r="0.6" fill="#fff" stroke="none"/></svg>'
  };

  let isNotifOpen = false;
  let notifTimer = null;
  let lastNotifAction = null;
  let notifAnimTimer = null;

  let notif, notifWave, notifIcon, notifTitle, notifSub,
      notifAmount, notifCommission, notifAction, contentWrap;

  function init() {
    notif = document.getElementById('notif');
    notifWave = document.getElementById('notifWave');
    notifIcon = document.getElementById('notifIcon');
    notifTitle = document.getElementById('notifTitle');
    notifSub = document.getElementById('notifSub');
    notifAmount = document.getElementById('notifAmount');
    notifCommission = document.getElementById('notifCommission');
    notifAction = document.getElementById('notifAction');
    contentWrap = document.getElementById('contentWrap');

    if (notif) {
      notif.addEventListener('click', (e) => {
        if (e.target === notifAction || notifAction.contains(e.target)) return;
        dismiss();
      });
    }
    if (notifAction) {
      notifAction.addEventListener('click', (e) => {
        e.stopPropagation();
        const cb = lastNotifAction;
        if (cb) cb();
        else dismiss();
      });
    }
  }

  function show(opts) {
    if (!notif) return;
    if (isNotifOpen) {
      dismiss();
      notifAnimTimer = setTimeout(() => show(opts), 380);
      return;
    }
    const type = opts.type || 'info';
    const usePill = opts.pill !== undefined
      ? opts.pill
      : (type === 'info' && !opts.amount && !opts.commission && !opts.actionLabel);

    notifIcon.className = `notif-icon ${type}`;
    notifIcon.innerHTML = ICONS[type] || ICONS.info;
    notifTitle.textContent = opts.title || '';
    notifSub.textContent = opts.sub || '';
    notif.classList.toggle('pill', usePill);

    if (usePill) {
      notif.classList.remove('has-details', 'has-action');
    } else {
      if (opts.amount || opts.commission) {
        notif.classList.add('has-details');
        if (opts.amount) {
          notifAmount.innerHTML = `<span class="lbl">Сумма</span><span class="val">${opts.amount}</span>`;
          notifAmount.style.display = 'flex';
        } else notifAmount.style.display = 'none';
        if (opts.commission) {
          notifCommission.innerHTML = `<span class="lbl">Комиссия</span><span class="val">${opts.commission}</span>`;
          notifCommission.style.display = 'flex';
        } else notifCommission.style.display = 'none';
      } else notif.classList.remove('has-details');

      if (!usePill && opts.actionLabel) {
        notif.classList.add('has-action');
        notifAction.textContent = opts.actionLabel;
        notifAction.classList.toggle('primary', !!opts.actionPrimary);
        lastNotifAction = opts.actionCb || null;
      } else {
        notif.classList.remove('has-action');
        lastNotifAction = null;
      }
    }

    notif.classList.remove('hide');
    void notif.offsetWidth;
    notif.classList.add('show');
    notifWave.classList.remove('show');
    void notifWave.offsetWidth;
    notifWave.classList.add('show');
    if (contentWrap) contentWrap.classList.add('shift');
    isNotifOpen = true;

    const defaultDelay = usePill ? 2600 : (type === 'error' ? 0 : 4000);
    const delay = opts.autoDismiss !== undefined ? opts.autoDismiss : defaultDelay;
    clearTimeout(notifTimer);
    if (delay > 0) notifTimer = setTimeout(dismiss, delay);
  }

  function dismiss() {
    if (!isNotifOpen) return;
    clearTimeout(notifTimer);
    notif.classList.remove('show');
    notif.classList.add('hide');
    notifWave.classList.remove('show');
    if (contentWrap) contentWrap.classList.remove('shift');
    isNotifOpen = false;
    setTimeout(() => {
      if (!isNotifOpen) {
        notif.classList.remove('hide');
        notif.classList.remove('show');
      }
    }, 420);
  }

  // Theme cycle helper (for prototype)
  function bindThemeCycle(phoneEl, titleSelector) {
    let currentTheme = 'dark';
    const title = document.querySelector(titleSelector || '.topbar .title');
    if (title) {
      title.addEventListener('click', () => {
        const themes = ['dark', 'light', 'amoled'];
        const idx = themes.indexOf(currentTheme);
        currentTheme = themes[(idx + 1) % themes.length];
        phoneEl.className = `phone theme-${currentTheme}`;
      });
    }
  }

  // Standard notif-zone HTML (drop in any screen)
  const NOTIF_ZONE_HTML = `
    <div class="notif-zone" id="notifZone">
      <div class="notif-wave" id="notifWave"></div>
      <div class="notif" id="notif">
        <div class="notif-content">
          <div class="notif-row">
            <div class="notif-icon info" id="notifIcon"></div>
            <div class="notif-body">
              <div class="notif-title" id="notifTitle">Готово</div>
              <div class="notif-sub" id="notifSub"></div>
            </div>
          </div>
          <div class="notif-details">
            <div class="notif-amount" id="notifAmount"></div>
            <div class="notif-commission" id="notifCommission"></div>
          </div>
          <button class="notif-action" id="notifAction">Готово</button>
        </div>
      </div>
    </div>
  `;

  window.MDAONotif = { init, show, dismiss, bindThemeCycle, NOTIF_ZONE_HTML };
})();
