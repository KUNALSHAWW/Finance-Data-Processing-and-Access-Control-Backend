(() => {
  'use strict';
  const C = window.APP_CONFIG;
  const API = C.apiBase.replace(/\/$/, '');
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  // storage that never throws (private windows, blocked storage)
  const wrap = (st) => ({
    get(k) { try { return st.getItem(k); } catch { return null; } },
    set(k, v) { try { st.setItem(k, v); } catch { /* ignore */ } },
    del(k) { try { st.removeItem(k); } catch { /* ignore */ } },
  });
  const ls = wrap(window.localStorage);
  const ss = wrap(window.sessionStorage);

  /** Tiny DOM builder. Text is always added as text nodes, never parsed as HTML (record data is user input). */
  function h(tag, attrs, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v == null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else if (v === true) el.setAttribute(k, '');
      else el.setAttribute(k, v);
    }
    const add = (c) => {
      if (c == null || c === false) return;
      if (Array.isArray(c)) c.forEach(add);
      else el.append(c.nodeType ? c : document.createTextNode(String(c)));
    };
    kids.forEach(add);
    return el;
  }

  const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
  const money = (n) => usd.format(Number(n));
  const monthLabel = (ym) => new Date(`${ym}-01T00:00:00Z`).toLocaleString('en-US', { month: 'short', timeZone: 'UTC' });
  const when = (ms) => new Date(ms).toLocaleString();

  // ------------------------------------------------------------------ toasts, confirm, copy, busy
  function toast(msg, kind = '') {
    const t = h('div', { class: `toast ${kind}`, role: 'status' }, msg);
    $('#toasts').append(t);
    setTimeout(() => t.remove(), 4200);
  }

  function confirmDialog({ title, body, confirmText = 'Confirm', danger = false }) {
    const dlg = $('#confirmDlg');
    const ok = $('#confirmOk');
    const cancel = $('#confirmCancel');
    $('#confirmTitle').textContent = title;
    $('#confirmBody').textContent = body;
    ok.textContent = confirmText;
    ok.className = `btn ${danger ? 'danger' : 'primary'}`;
    return new Promise((resolve) => {
      // Resolve from the buttons themselves; Esc closes the dialog natively and counts as cancel.
      const done = (value) => { if (dlg.open) dlg.close(); resolve(value); };
      ok.onclick = (e) => { e.preventDefault(); done(true); };
      cancel.onclick = (e) => { e.preventDefault(); done(false); };
      dlg.addEventListener('close', () => resolve(false), { once: true });
      dlg.showModal();
      cancel.focus();
    });
  }

  async function copyText(text, btn) {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      const ta = h('textarea', { 'aria-hidden': 'true' });
      ta.value = text;
      document.body.append(ta);
      ta.select();
      document.execCommand('copy');
      ta.remove();
    }
    if (btn) {
      const old = btn.textContent;
      btn.textContent = 'Copied';
      btn.classList.add('done');
      setTimeout(() => { btn.textContent = old; btn.classList.remove('done'); }, 1500);
    }
  }
  const copyBtn = (getText, label = 'Copy') =>
    h('button', { type: 'button', class: 'btn small', onclick: (e) => copyText(getText(), e.currentTarget) }, label);

  async function busy(btn, fn) {
    btn.disabled = true;
    btn.classList.add('loading');
    btn.setAttribute('aria-busy', 'true');
    try { return await fn(); } finally {
      btn.disabled = false;
      btn.classList.remove('loading');
      btn.removeAttribute('aria-busy');
    }
  }

  // ------------------------------------------------------------------ API with cold-start handling
  class ApiError extends Error {
    constructor(status, body) { super(`HTTP ${status}`); this.status = status; this.body = body; }
  }

  function setChip(state) {
    const chip = $('#apiStatus');
    chip.className = `chip ${state}`;
    $('#apiStatusText').textContent = { wake: 'API waking up', ok: 'API ready', down: 'API unreachable' }[state];
  }

  /** Free hosting sleeps when idle; ping on page load so the API is awake by the time someone clicks. */
  const ready = (async () => {
    setChip('wake');
    for (let i = 0; i < 48; i++) {
      try {
        const r = await fetch(`${API}/actuator/health`, { cache: 'no-store' });
        if (r.ok) { setChip('ok'); return true; }
      } catch { /* still asleep */ }
      await sleep(2500);
    }
    setChip('down');
    return false;
  })();

  let cfgPromise = null;
  /** Loaded once; both the login screen and a restored session need the demo flag. */
  function loadConfig() {
    if (!cfgPromise) {
      cfgPromise = api('/api/public/config', { auth: false }).then((c) => { S.demo = c.demo; S.accounts = c.accounts; return c; });
      cfgPromise.catch(() => { cfgPromise = null; });
    }
    return cfgPromise;
  }

  const S = { token: ss.get('token'), me: JSON.parse(ss.get('me') || 'null'), view: 'dashboard', demo: false, accounts: [] };

  async function api(path, { method = 'GET', body, auth = true } = {}) {
    await ready;
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (auth && S.token) headers.Authorization = `Bearer ${S.token}`;
    const res = await fetch(API + path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
    let data = null;
    if ((res.headers.get('content-type') || '').includes('json')) data = await res.json().catch(() => null);
    if (res.status === 401 && auth && S.token) { logout('Your session expired. Please sign in again.'); }
    if (!res.ok) throw new ApiError(res.status, data);
    return data;
  }

  function describe(e) {
    if (e instanceof ApiError) return (e.body && e.body.message) || `Request failed (${e.status})`;
    return 'Could not reach the server. It may still be waking up, try again in a moment.';
  }

  // ------------------------------------------------------------------ page chrome
  function initChrome() {
    $('#repoLink').href = $('#repoLink2').href = $('#linkGithub').href = C.repo;
    $('#linkLinkedin').href = C.linkedin;
    $('#linkPortfolio').href = C.portfolio;
    $('#contactFab').href = `mailto:${C.contactEmail}?subject=${encodeURIComponent('About Finance Ledger')}`;
    const lu = $('#lastUpdated');
    lu.dateTime = C.lastUpdated;
    lu.textContent = new Date(`${C.lastUpdated}T00:00:00Z`).toLocaleDateString('en-US', { dateStyle: 'long', timeZone: 'UTC' });

    // dark mode
    const themeBtn = $('#themeBtn');
    const paint = () => {
      const dark = document.documentElement.getAttribute('data-theme') === 'dark';
      themeBtn.setAttribute('aria-label', dark ? 'Switch to light mode' : 'Switch to dark mode');
      $('#themeIcon').textContent = dark ? '☀' : '☾';
    };
    themeBtn.addEventListener('click', () => {
      const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      ls.set('theme', next);
      paint();
    });
    paint();

    // mobile menu
    const nav = $('#nav');
    const menuBtn = $('#menuBtn');
    const setMenu = (open) => { nav.classList.toggle('open', open); menuBtn.setAttribute('aria-expanded', String(open)); };
    menuBtn.addEventListener('click', () => setMenu(!nav.classList.contains('open')));
    nav.addEventListener('click', (e) => { if (e.target.closest('a')) setMenu(false); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') setMenu(false); });

    // scroll progress + back to top
    const bar = $('#progress');
    const top = $('#toTop');
    let ticking = false;
    addEventListener('scroll', () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        const el = document.documentElement;
        const max = el.scrollHeight - el.clientHeight;
        bar.style.width = `${max > 0 ? (el.scrollTop / max) * 100 : 0}%`;
        top.hidden = el.scrollTop < 600;
        ticking = false;
      });
    }, { passive: true });
    top.addEventListener('click', () => scrollTo({ top: 0 }));

    // copy buttons on static code blocks
    const base = API;
    $('#curl1').textContent = `curl -X POST ${base}/api/auth/login \\\n  -H 'Content-Type: application/json' \\\n  -d '{"email":"viewer@demo.example","password":"Demo@Viewer1"}'`;
    $('#curl2').textContent = `curl ${base}/api/dashboard/summary \\\n  -H 'Authorization: Bearer <token>'`;
    $$('[data-copy-target]').forEach((b) =>
      b.addEventListener('click', () => copyText($(`#${b.dataset.copyTarget}`).textContent, b)));

    // print: open every FAQ so the printout is complete, then restore
    let openBefore = [];
    addEventListener('beforeprint', () => { openBefore = $$('details.faq').filter((d) => d.open); $$('details.faq').forEach((d) => { d.open = true; }); });
    addEventListener('afterprint', () => { $$('details.faq').forEach((d) => { d.open = openBefore.includes(d); }); });
    $('#printBtn').addEventListener('click', (e) => { e.preventDefault(); print(); });

    // privacy notice
    const cookie = $('#cookie');
    if (ls.get('notice') !== '1') cookie.hidden = false;
    $('#cookieOk').addEventListener('click', () => { ls.set('notice', '1'); cookie.hidden = true; });
    $('#cookieNo').addEventListener('click', () => { ls.set('notice', '1'); ls.set('optout', '1'); cookie.hidden = true; toast('Okay, your visits will not be counted.'); });
  }

  // ------------------------------------------------------------------ UTM: remember first touch, report once per session
  function initUtm() {
    const p = new URLSearchParams(location.search);
    let utm = JSON.parse(ss.get('utm') || 'null');
    if (!utm) {
      utm = {};
      for (const k of ['source', 'medium', 'campaign', 'content', 'term']) {
        const v = p.get(`utm_${k}`);
        if (v) utm[k] = v.slice(0, 100);
      }
      ss.set('utm', JSON.stringify(utm));
    }
    if (ss.get('visit') || ls.get('optout') === '1') return;
    ss.set('visit', '1');
    ready.then((up) => {
      if (!up) return;
      fetch(`${API}/api/public/visit`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, keepalive: true,
        body: JSON.stringify({ ...utm, source: utm.source || 'direct', path: location.pathname }),
      }).catch(() => { /* counting is best effort */ });
    });
  }

  // ------------------------------------------------------------------ site search
  function initSearch() {
    const dlg = $('#searchDlg');
    const input = $('#searchInput');
    const list = $('#searchList');
    const entries = [
      { t: 'How it works', s: 'hash chain audit entries tamper verify', href: '#how' },
      { t: 'Live demo', s: 'sign in as viewer analyst admin try', href: '#try' },
      { t: 'Tamper Lab', s: 'edit the database directly then verify the ledger', href: '#try' },
      { t: 'Roles and permissions', s: 'viewer analyst admin access control RBAC lockout JWT', href: '#tech' },
      { t: 'Correct money handling', s: 'BigDecimal DECIMAL GROUP BY optimistic locking', href: '#tech' },
      { t: 'Anomaly detection', s: 'modified z-score median MAD outliers insights', href: '#tech' },
      { t: 'Call the API with curl', s: 'login token dashboard summary', href: '#tech' },
      { t: 'API: POST /api/auth/login', s: 'authenticate and receive a bearer token', href: '#tech' },
      { t: 'API: GET /api/audit/verify', s: 'recompute hash chain and compare rows with audited state', href: '#tech' },
      { t: 'API: GET /api/dashboard/insights', s: 'explainable anomalies per category', href: '#tech' },
      { t: 'API: /api/records', s: 'list filter create update soft delete records', href: '#tech' },
      { t: 'GitHub repository', s: 'source code README tests CI', href: C.repo, external: true },
      ...$$('details.faq').map((d) => ({ t: $('summary', d).textContent, s: $('p', d).textContent, href: `#${d.id}`, faq: true })),
    ];
    let shown = [];
    let active = 0;

    const render = () => {
      const terms = input.value.toLowerCase().split(/\s+/).filter(Boolean);
      shown = entries
        .map((e) => {
          const title = e.t.toLowerCase();
          const hay = `${title} ${e.s.toLowerCase()}`;
          if (!terms.every((t) => hay.includes(t))) return null;
          return { e, score: terms.reduce((n, t) => n + (title.includes(t) ? 2 : 1), 0) };
        })
        .filter(Boolean).sort((a, b) => b.score - a.score).slice(0, 8).map((x) => x.e);
      active = 0;
      list.replaceChildren(...(shown.length ? shown : [{ t: 'No matches', s: 'Try "ledger", "roles" or "anomaly"', none: true }]).map((e, i) =>
        h('li', { role: 'option', 'aria-selected': String(i === active), onclick: () => !e.none && go(e) }, h('strong', null, e.t), h('small', null, (e.s || '').slice(0, 90)))));
    };
    const mark = () => $$('li', list).forEach((li, i) => li.setAttribute('aria-selected', String(i === active)));
    const go = (e) => {
      dlg.close();
      if (e.external) { window.open(e.href, '_blank', 'noopener'); return; }
      location.hash = e.href;
      if (e.faq) $(e.href).open = true;
      $(e.href).scrollIntoView();
    };
    const open = () => { input.value = ''; render(); dlg.showModal(); input.focus(); };
    $('#searchBtn').addEventListener('click', open);
    input.addEventListener('input', render);
    input.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown') { active = Math.min(active + 1, shown.length - 1); mark(); e.preventDefault(); }
      else if (e.key === 'ArrowUp') { active = Math.max(active - 1, 0); mark(); e.preventDefault(); }
      else if (e.key === 'Enter') { e.preventDefault(); if (shown[active]) go(shown[active]); }
      else if (e.key === 'Escape') { e.preventDefault(); dlg.close(); } // a search field would otherwise just clear its text
    });
    dlg.addEventListener('click', (e) => { if (e.target === dlg) dlg.close(); });
    document.addEventListener('keydown', (e) => {
      const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName);
      if (!dlg.open && ((e.key === '/' && !typing) || ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k'))) { e.preventDefault(); open(); }
    });
  }

  // ------------------------------------------------------------------ console: shared pieces
  const root = () => $('#console');
  const skeleton = () => h('div', { class: 'pad', 'aria-busy': 'true' },
    h('div', { class: 'stats' }, [1, 2, 3, 4].map(() => h('div', { class: 'skel block-s' }))),
    [1, 2, 3, 4].map(() => h('div', { class: 'skel line' })));
  const errorBox = (msg) => h('div', { class: 'alert bad', role: 'alert' }, msg);

  function logout(message) {
    S.token = null; S.me = null; ss.del('token'); ss.del('me');
    renderConsole();
    if (message) toast(message);
  }

  const ROLE_BLURB = {
    ADMIN: 'Everything: write records, manage users, and the Tamper Lab.',
    ANALYST: 'Read everything, anomaly insights, and the audit ledger.',
    VIEWER: 'Read records and the dashboard only.',
  };

  // ------------------------------------------------------------------ login view
  async function login(email, password, btn, box) {
    box.replaceChildren();
    try {
      await busy(btn, async () => {
        const r = await api('/api/auth/login', { method: 'POST', body: { email, password }, auth: false });
        S.token = r.token; ss.set('token', r.token);
        S.me = await api('/api/auth/me'); ss.set('me', JSON.stringify(S.me));
      });
      S.view = 'dashboard';
      renderConsole();
      toast(`Signed in as ${S.me.role.toLowerCase()}`, 'ok');
    } catch (e) {
      S.token = null;
      box.replaceChildren(errorBox(describe(e)));
    }
  }

  async function loginView() {
    const box = h('div');
    const cards = h('div', { class: 'roles' });
    const wake = h('div', { class: 'wake-box', role: 'status' }, h('span', { class: 'spin' }), 'Waking the API. Free hosting sleeps when idle, buttons start working the moment it is up.');
    ready.then(() => wake.remove());

    const pw = h('input', { id: 'pw', type: 'password', autocomplete: 'current-password', required: true });
    const toggle = h('button', { type: 'button', 'aria-label': 'Show password', 'aria-pressed': 'false', onclick: () => {
      const show = pw.type === 'password';
      pw.type = show ? 'text' : 'password';
      toggle.textContent = show ? 'Hide' : 'Show';
      toggle.setAttribute('aria-pressed', String(show));
      toggle.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
    } }, 'Show');
    const email = h('input', { id: 'em', type: 'email', autocomplete: 'username', required: true });
    const submit = h('button', { type: 'submit', class: 'btn primary' }, 'Sign in');
    const form = h('form', { class: 'form', onsubmit: (e) => { e.preventDefault(); login(email.value.trim(), pw.value, submit, box); } },
      h('label', { for: 'em' }, 'Email', email),
      h('label', { for: 'pw' }, 'Password', h('div', { class: 'pw' }, pw, toggle)),
      submit);

    root().replaceChildren(h('div', { class: 'pad' },
      h('h3', null, 'Sign in'), wake, cards, h('div', { class: 'divider' }, 'or use your own account'), form, box));

    try {
      const cfg = await loadConfig();
      cards.replaceChildren(...cfg.accounts.map((a) => {
        const b = h('button', { type: 'button', class: 'role-card', onclick: () => login(a.email, a.password, b, box) },
          h('strong', null, `Enter as ${a.role[0] + a.role.slice(1).toLowerCase()}`), h('span', null, ROLE_BLURB[a.role]));
        return b;
      }));
      if (!cfg.demo) { cards.remove(); $('.divider', root()).remove(); }
    } catch (e) {
      box.replaceChildren(errorBox(describe(e)));
    }
  }

  // ------------------------------------------------------------------ app view
  function tabsFor(role) {
    const t = [['dashboard', 'Dashboard'], ['records', 'Records']];
    if (role !== 'VIEWER') t.push(['audit', 'Audit ledger']);
    if (role === 'ADMIN' && S.demo) t.push(['lab', 'Tamper Lab']);
    if (role === 'ADMIN') t.push(['users', 'Users']);
    return t;
  }

  function appView() {
    const panel = h('div', { class: 'pad', id: 'panel', role: 'tabpanel', tabindex: '0' });
    const tabs = tabsFor(S.me.role);
    const tablist = h('div', { class: 'tabs', role: 'tablist', 'aria-label': 'Console sections' });
    const show = (view) => {
      S.view = view;
      $$('button', tablist).forEach((b) => b.setAttribute('aria-selected', String(b.dataset.view === view)));
      panel.replaceChildren(skeleton());
      VIEWS[view](panel).catch((e) => panel.replaceChildren(errorBox(describe(e))));
    };
    tabs.forEach(([id, label]) => tablist.append(h('button', { role: 'tab', 'data-view': id, onclick: () => show(id) }, label)));
    tablist.addEventListener('keydown', (e) => {
      const btns = $$('button', tablist);
      const i = btns.indexOf(document.activeElement);
      if (i < 0 || !['ArrowRight', 'ArrowLeft'].includes(e.key)) return;
      const n = btns[(i + (e.key === 'ArrowRight' ? 1 : btns.length - 1)) % btns.length];
      n.focus(); n.click();
    });

    root().replaceChildren(
      h('div', { class: 'topbar' },
        h('strong', null, S.me.email), h('span', { class: 'badge' }, S.me.role),
        h('span', { class: 'spacer' }),
        copyBtn(() => S.token, 'Copy API token'),
        h('button', { class: 'btn small', type: 'button', onclick: () => logout('Signed out') }, 'Sign out')),
      tablist, panel);
    show(tabs.some(([id]) => id === S.view) ? S.view : 'dashboard');
  }

  const stat = (label, value, cls = '') => h('div', { class: 'stat' }, h('small', null, label), h('b', { class: cls }, value));

  // ---- dashboard
  async function dashboardView(panel) {
    const insights = S.me.role !== 'VIEWER';
    const [sum, trends, ins] = await Promise.all([
      api('/api/dashboard/summary'), api('/api/dashboard/trends?months=6'),
      insights ? api('/api/dashboard/insights?months=12') : Promise.resolve(null)]);

    const max = Math.max(1, ...trends.flatMap((t) => [Number(t.income), Number(t.expense)]));
    const bar = (cls, v, label) => { const b = h('div', { class: cls, title: `${label}: ${money(v)}` }); b.style.height = `${(Number(v) / max) * 100}%`; return b; };
    const summary = trends.map((t) => `${monthLabel(t.month)}: income ${money(t.income)}, expenses ${money(t.expense)}`).join('; ');

    panel.replaceChildren(
      h('div', { class: 'stats' },
        stat('Income', money(sum.totalIncome), 'pos'), stat('Expenses', money(sum.totalExpense), 'neg'),
        stat('Net balance', money(sum.netBalance), Number(sum.netBalance) >= 0 ? 'pos' : 'neg'), stat('Records', String(sum.recordCount))),
      h('h3', null, 'Last 6 months'),
      h('div', { class: 'chart', role: 'img', 'aria-label': summary },
        trends.map((t) => h('div', { class: 'col' }, h('div', { class: 'pair' }, bar('bar-i', t.income, 'Income'), bar('bar-e', t.expense, 'Expenses'))))),
      h('div', { class: 'axis' }, trends.map((t) => h('span', null, monthLabel(t.month)))),
      h('div', { class: 'legend' }, h('span', null, h('i', { class: 'lg-i' }), 'Income'), h('span', null, h('i', { class: 'lg-e' }), 'Expenses')),
      h('h3', { class: 'sec' }, 'By category'),
      h('div', { class: 'tablewrap' }, h('table', null,
        h('thead', null, h('tr', null, h('th', null, 'Category'), h('th', { class: 'num-r' }, 'Income'), h('th', { class: 'num-r' }, 'Expense'), h('th', { class: 'num-r' }, 'Net'))),
        h('tbody', null, sum.categoryBreakdown.map((c) => h('tr', null, h('td', null, c.category),
          h('td', { class: 'num-r' }, money(c.income)), h('td', { class: 'num-r' }, money(c.expense)),
          h('td', { class: `num-r ${Number(c.net) >= 0 ? 'pos' : 'neg'}` }, money(c.net))))))),
      h('h3', { class: 'sec' }, 'Unusual transactions'),
      ins ? anomalies(ins) : h('p', { class: 'fine' }, 'Anomaly insights are available to Analysts and Admins.'));
  }

  function anomalies(ins) {
    if (!ins.anomalies.length) return h('p', { class: 'fine' }, 'Nothing unusual in the last 12 months.');
    return h('div', null,
      h('div', { class: 'anom' }, ins.anomalies.map((a) =>
        h('div', { class: 'anom-item' }, h('b', null, `${a.category}: ${money(a.amount)}`), h('div', null, a.reason)))),
      h('p', { class: 'fine' }, `Method: ${ins.method}.`));
  }

  // ---- records
  async function recordsView(panel) {
    const isAdmin = S.me.role === 'ADMIN';
    const f = { type: '', category: '', from: '', to: '' };
    const list = h('div');
    const pager = h('div', { class: 'pager' });

    const load = async (page = 0) => {
      list.replaceChildren(h('div', { class: 'skel line' }), h('div', { class: 'skel line' }), h('div', { class: 'skel line' }));
      const q = new URLSearchParams({ page, size: 10 });
      Object.entries(f).forEach(([k, v]) => v && q.set(k, v));
      try {
        const r = await api(`/api/records?${q}`);
        list.replaceChildren(h('div', { class: 'tablewrap' }, h('table', null,
          h('thead', null, h('tr', null, ['Date', 'Category', 'Type', 'Amount', 'Description', 'By', isAdmin ? '' : null].filter((x) => x !== null).map((c, i) => h('th', { class: c === 'Amount' ? 'num-r' : '' }, c)))),
          h('tbody', null, r.data.length ? r.data.map((x) => h('tr', null,
            h('td', null, x.date), h('td', null, x.category),
            h('td', null, h('span', { class: `badge ${x.type.toLowerCase()}` }, x.type)),
            h('td', { class: 'num-r' }, money(x.amount)), h('td', { class: 'wrap-c' }, x.description || ''), h('td', null, x.createdBy),
            isAdmin ? h('td', null, h('button', { class: 'btn small danger', type: 'button', onclick: () => remove(x, page) }, 'Delete')) : null))
            : h('tr', null, h('td', { colspan: 7 }, 'No records match.'))))));
        pager.replaceChildren(
          h('button', { class: 'btn small', type: 'button', disabled: page <= 0, onclick: () => load(page - 1) }, 'Previous'),
          h('span', null, `Page ${r.totalPages ? r.currentPage + 1 : 0} of ${r.totalPages} (${r.totalElements} records)`),
          h('button', { class: 'btn small', type: 'button', disabled: page + 1 >= r.totalPages, onclick: () => load(page + 1) }, 'Next'));
      } catch (e) { list.replaceChildren(errorBox(describe(e))); }
    };

    const remove = async (rec, page) => {
      if (!await confirmDialog({ title: 'Delete this record?', body: `${rec.category}, ${money(rec.amount)} on ${rec.date}. It is soft-deleted and the deletion is written to the audit ledger.`, confirmText: 'Delete', danger: true })) return;
      try { await api(`/api/records/${rec.id}`, { method: 'DELETE' }); toast('Record deleted', 'ok'); load(page); } catch (e) { toast(describe(e), 'bad'); }
    };

    const type = h('select', { id: 'f-type' }, h('option', { value: '' }, 'All'), h('option', { value: 'INCOME' }, 'Income'), h('option', { value: 'EXPENSE' }, 'Expense'));
    const cat = h('input', { id: 'f-cat', type: 'text', placeholder: 'e.g. Groceries' });
    const from = h('input', { id: 'f-from', type: 'date' });
    const to = h('input', { id: 'f-to', type: 'date' });
    const filters = h('form', { class: 'filters', onsubmit: (e) => { e.preventDefault(); Object.assign(f, { type: type.value, category: cat.value.trim(), from: from.value, to: to.value }); load(0); } },
      h('label', { for: 'f-type' }, 'Type', type), h('label', { for: 'f-cat' }, 'Category', cat),
      h('label', { for: 'f-from' }, 'From', from), h('label', { for: 'f-to' }, 'To', to),
      h('button', { class: 'btn small primary', type: 'submit' }, 'Filter'),
      h('button', { class: 'btn small', type: 'reset', onclick: () => setTimeout(() => { Object.assign(f, { type: '', category: '', from: '', to: '' }); load(0); }) }, 'Reset'));

    panel.replaceChildren(filters, list, pager, isAdmin ? addForm(() => load(0)) : h('p', { class: 'fine' }, 'Only Admins can create, edit or delete records.'));
    await load(0);
  }

  function addForm(onDone) {
    const fields = {
      amount: h('input', { id: 'a-amount', name: 'amount', type: 'number', step: '0.01', min: '0.01', inputmode: 'decimal' }),
      type: h('select', { id: 'a-type', name: 'type' }, h('option', { value: 'EXPENSE' }, 'Expense'), h('option', { value: 'INCOME' }, 'Income')),
      category: h('input', { id: 'a-cat', name: 'category', type: 'text', maxlength: '100', placeholder: 'Others' }),
      date: h('input', { id: 'a-date', name: 'date', type: 'date', max: new Date().toISOString().slice(0, 10) }),
      description: h('input', { id: 'a-desc', name: 'description', type: 'text', maxlength: '500' }),
    };
    const labels = { amount: 'Amount (USD)', type: 'Type', category: 'Category', date: 'Date', description: 'Description' };
    const errs = Object.fromEntries(Object.keys(fields).map((k) => [k, h('div', { class: 'field-error', id: `e-${k}`, role: 'alert' })]));
    const status = h('div');
    const btn = h('button', { class: 'btn primary', type: 'submit' }, 'Add record');

    const form = h('form', { class: 'form wide', novalidate: true, onsubmit: async (e) => {
      e.preventDefault();
      status.replaceChildren();
      Object.entries(errs).forEach(([k, el]) => { el.textContent = ''; fields[k].removeAttribute('aria-invalid'); fields[k].removeAttribute('aria-describedby'); });
      const body = { type: fields.type.value };
      ['amount', 'category', 'date', 'description'].forEach((k) => { if (fields[k].value.trim()) body[k] = fields[k].value.trim(); });
      try {
        await busy(btn, () => api('/api/records', { method: 'POST', body }));
        status.replaceChildren(h('div', { class: 'alert ok', role: 'status' }, 'Record added and written to the audit ledger.'));
        toast('Record added', 'ok');
        form.reset();
        onDone();
      } catch (err) {
        const fe = err instanceof ApiError && err.body && err.body.fieldErrors;
        if (fe) {
          Object.entries(fe).forEach(([k, msg]) => { if (fields[k]) { errs[k].textContent = msg; fields[k].setAttribute('aria-invalid', 'true'); fields[k].setAttribute('aria-describedby', `e-${k}`); } });
          status.replaceChildren(h('div', { class: 'alert bad', role: 'alert' }, 'Please fix the highlighted fields.'));
          (Object.keys(fe).map((k) => fields[k]).find(Boolean) || btn).focus();
        } else {
          status.replaceChildren(errorBox(describe(err)));
        }
      }
    } },
      Object.keys(fields).map((k) => h('label', { for: fields[k].id }, labels[k], fields[k], errs[k])), h('div', null, btn));
    return h('div', null, h('h3', { class: 'sec' }, 'Add a record'), form, status);
  }

  // ---- audit
  const PROBLEM = {
    MODIFIED: 'changed behind the API\'s back: its contents no longer match the last audited state',
    UNAUDITED: 'exists in the database but was never written through the API',
    MISSING: 'was removed from the database, but the ledger says it should exist',
  };

  function verdict(v) {
    const ok = v.valid;
    return h('div', { class: `verify ${ok ? 'ok' : 'bad'}`, role: 'status' },
      h('h4', null, ok ? '✓ Ledger intact' : '✗ Tampering detected'),
      h('p', null, ok ? `${v.entriesChecked} entries checked. Every record and user matches its last audited state.` : v.firstBrokenSeq ? v.chainMessage : 'The audit log itself is intact, but live data no longer matches it:'),
      v.findings.length ? h('ul', null, v.findings.map((f) => h('li', null, h('strong', null, `${f.entityType} #${f.entityId}`), ` ${PROBLEM[f.problem] || f.problem}.`))) : null,
      h('div', { class: 'hash' }, h('span', null, `head ${v.headHash}`), copyBtn(() => v.headHash, 'Copy head hash')));
  }

  async function runVerify(btn, slot) {
    slot.replaceChildren();
    try { slot.replaceChildren(verdict(await busy(btn, () => api('/api/audit/verify')))); } catch (e) { slot.replaceChildren(errorBox(describe(e))); }
  }

  async function auditView(panel) {
    const slot = h('div');
    const vbtn = h('button', { class: 'btn primary', type: 'button', onclick: () => runVerify(vbtn, slot) }, 'Verify ledger now');
    const list = h('div');
    const pager = h('div', { class: 'pager' });
    const load = async (page = 0) => {
      const r = await api(`/api/audit?page=${page}&size=10`);
      list.replaceChildren(h('div', { class: 'tablewrap' }, h('table', null,
        h('thead', null, h('tr', null, ['#', 'Time', 'Actor', 'Action', 'Entity', 'Hash'].map((c) => h('th', null, c)))),
        h('tbody', null, r.data.map((e) => h('tr', null, h('td', null, e.seq), h('td', null, when(e.occurredAt)), h('td', null, e.actor),
          h('td', null, e.action), h('td', null, `${e.entityType} #${e.entityId}`), h('td', { title: e.hash }, h('code', null, `${e.hash.slice(0, 12)}…`))))))));
      pager.replaceChildren(
        h('button', { class: 'btn small', type: 'button', disabled: page <= 0, onclick: () => load(page - 1).catch(() => {}) }, 'Newer'),
        h('span', null, `Page ${r.currentPage + 1} of ${r.totalPages}`),
        h('button', { class: 'btn small', type: 'button', disabled: page + 1 >= r.totalPages, onclick: () => load(page + 1).catch(() => {}) }, 'Older'));
    };
    panel.replaceChildren(
      h('p', { class: 'sub' }, 'Every record and user change is chained to the one before it. Verifying recomputes the whole chain and compares live rows with their last audited state.'),
      vbtn, slot, h('h3', { class: 'sec' }, 'Entries, newest first'), list, pager);
    await load(0);
  }

  // ---- tamper lab
  async function labView(panel) {
    const slotA = h('div');
    const slotB = h('div');
    const sqlSlot = h('div');
    const vA = h('button', { class: 'btn primary', type: 'button', onclick: () => runVerify(vA, slotA) }, '1. Verify the ledger');
    const tamper = h('button', { class: 'btn danger', type: 'button', onclick: async () => {
      if (!await confirmDialog({ title: 'Edit the database directly?', body: 'This runs a raw SQL UPDATE on the biggest expense, bypassing the API, like someone with database access hiding it. You can restore the demo data afterwards.', confirmText: 'Edit it', danger: true })) return;
      try {
        const r = await busy(tamper, () => api('/api/demo/tamper', { method: 'POST' }));
        sqlSlot.replaceChildren(
          h('div', { class: 'alert warn' }, `Record #${r.recordId} (${r.category}) was ${money(r.originalAmount)} and is now ${money(r.newAmount)}. No audit entry was written for this.`),
          h('div', { class: 'code' }, h('pre', { id: 'sqlPre' }, r.sql), h('button', { type: 'button', class: 'copy', onclick: (e) => copyText(r.sql, e.currentTarget) }, 'Copy')));
        slotB.replaceChildren();
      } catch (e) { toast(describe(e), 'bad'); }
    } }, '2. Edit the database directly');
    const vB = h('button', { class: 'btn primary', type: 'button', onclick: () => runVerify(vB, slotB) }, '3. Verify again');
    const restore = h('button', { class: 'btn', type: 'button', onclick: async () => {
      if (!await confirmDialog({ title: 'Restore the demo data?', body: 'Wipes the demo database and re-seeds it with a fresh, valid audit chain. Everyone using the demo sees this.', confirmText: 'Restore' })) return;
      try { await busy(restore, () => api('/api/demo/reset', { method: 'POST' })); toast('Demo data restored', 'ok'); [slotA, slotB, sqlSlot].forEach((s) => s.replaceChildren()); } catch (e) { toast(describe(e), 'bad'); }
    } }, 'Restore demo data');

    panel.replaceChildren(
      h('p', { class: 'sub' }, 'The whole idea in three clicks. This is the only place the demo bypasses the API, and it exists only in demo mode.'),
      h('div', { class: 'steps' },
        h('div', { class: 'step' }, h('h4', null, 'Start clean'), h('p', null, 'The ledger should verify.'), vA, slotA),
        h('div', { class: 'step' }, h('h4', null, 'Cheat'), h('p', null, 'Hide the largest expense by editing the row directly in the database.'), tamper, sqlSlot),
        h('div', { class: 'step' }, h('h4', null, 'Get caught'), h('p', null, 'Verification names the exact row that no longer matches its audit trail.'), vB, slotB),
        h('div', null, restore)));
  }

  // ---- users
  async function usersView(panel) {
    const r = await api('/api/users?size=100');
    const act = async (u, what) => {
      const verbs = { deactivate: ['Deactivate', false], activate: ['Activate', false], delete: ['Delete', true] };
      if (!await confirmDialog({ title: `${verbs[what][0]} ${u.email}?`, body: what === 'deactivate' ? 'Takes effect immediately: their existing tokens stop working.' : 'This is written to the audit ledger.', confirmText: verbs[what][0], danger: verbs[what][1] })) return;
      try { const m = await api(`/api/users/${u.id}${what === 'delete' ? '' : `/${what}`}`, { method: what === 'delete' ? 'DELETE' : 'PUT' }); toast(m.message, 'ok'); usersView(panel).catch(() => {}); } catch (e) { toast(describe(e), 'bad'); }
    };
    panel.replaceChildren(
      S.demo ? h('div', { class: 'alert warn' }, 'User management is read-only in the public demo, so nobody can lock the sample accounts. Locally it works fully.') : null,
      h('div', { class: 'tablewrap' }, h('table', null,
        h('thead', null, h('tr', null, ['ID', 'Name', 'Email', 'Role', 'Status', ''].map((c) => h('th', null, c)))),
        h('tbody', null, r.data.map((u) => h('tr', null, h('td', null, u.id), h('td', null, u.name), h('td', null, u.email),
          h('td', null, h('span', { class: 'badge' }, u.role)), h('td', null, u.active ? 'Active' : 'Inactive'),
          h('td', null, h('div', { class: 'row' },
            h('button', { class: 'btn small', type: 'button', onclick: () => act(u, u.active ? 'deactivate' : 'activate') }, u.active ? 'Deactivate' : 'Activate'),
            h('button', { class: 'btn small danger', type: 'button', onclick: () => act(u, 'delete') }, 'Delete')))))))));
  }

  const VIEWS = { dashboard: dashboardView, records: recordsView, audit: auditView, lab: labView, users: usersView };

  function renderConsole() {
    if (!S.token || !S.me) { S.token = null; loginView(); return; }
    root().replaceChildren(skeleton());
    loadConfig().catch(() => {}).then(appView);
  }

  // ------------------------------------------------------------------ boot
  initChrome();
  initSearch();
  initUtm();
  renderConsole();
})();
