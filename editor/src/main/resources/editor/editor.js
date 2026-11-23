// SPDX-License-Identifier: GPL-3.0-or-later
// Edit in a Browser: favorites, channels, a source's categories, numbering and locks, organized
// from a computer's or phone's browser on the TV's own data. The TV serves this page; a browser
// comes in with the code the TV shows, and every change goes to the TV as it's made.
(() => {
  'use strict';
  const T = JSON.parse(document.getElementById('texts').textContent);
  const LANG = document.documentElement.lang || 'en';
  const NUMBER = new Intl.NumberFormat(LANG);
  const PLURAL = new Intl.PluralRules(LANG);
  const STORE = 'plasma.editor.session';
  const POLL_MS = 3000;
  const ROW = 52;
  const SEARCH_DEBOUNCE_MS = 250;
  const MAX_ADD_ALL = 500;
  const API = '/edit/api/';
  const SVG = 'http://www.w3.org/2000/svg';

  // ---- Words ------------------------------------------------------------------------------------

  /** A word with its %s, %d and %1$s filled in, in order. */
  function fmt(text, ...args) {
    let next = 0;
    return String(text || '').replace(/%(?:(\d+)\$)?[sd]/g, (_, at) => {
      const value = at ? args[Number(at) - 1] : args[next++];
      return value === undefined ? '' : String(value);
    });
  }

  /** A count with its word: the TV's words give the one form, a bar, then the others ("%s channel|%s channels"). */
  function plural(key, n) {
    const forms = String(T[key] || '%s').split('|');
    const form = PLURAL.select(n) === 'one' ? forms[0] : (forms[1] || forms[0]);
    return fmt(form, NUMBER.format(n));
  }

  const fold = (text) => String(text || '').normalize('NFD').replace(/\p{Diacritic}/gu, '').toLocaleLowerCase(LANG);

  // ---- Making the page ------------------------------------------------------------------------

  /** An element: [props] sets classes, text, attributes and `on…` handlers; [children] are nodes or text. */
  function h(tag, props, ...children) {
    const el = document.createElement(tag);
    if (props) {
      for (const [name, value] of Object.entries(props)) {
        if (value === undefined || value === null || value === false) continue;
        if (name === 'class') el.className = value;
        else if (name === 'text') el.textContent = value;
        else if (name.startsWith('on')) el.addEventListener(name.slice(2), value);
        else if (name === 'dataset') Object.assign(el.dataset, value);
        else if (name in el && name !== 'list' && typeof value !== 'string') el[name] = value;
        else el.setAttribute(name, value === true ? '' : value);
      }
    }
    for (const child of children.flat()) if (child !== null && child !== undefined && child !== false) el.append(child.nodeType ? child : document.createTextNode(String(child)));
    return el;
  }

  // Line icons on a 24-unit grid, as the TV's (brief 2.8); the star is filled when on.
  const ICONS = {
    grip: '<circle cx="9" cy="6" r="1.4"/><circle cx="15" cy="6" r="1.4"/><circle cx="9" cy="12" r="1.4"/><circle cx="15" cy="12" r="1.4"/><circle cx="9" cy="18" r="1.4"/><circle cx="15" cy="18" r="1.4"/>',
    star: '<path d="m12 3.6 2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z"/>',
    eye: '<path d="M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12z"/><circle cx="12" cy="12" r="2.8"/>',
    eyeOff: '<path d="M3 3l18 18"/><path d="M10.6 5.6c.5-.1.9-.1 1.4-.1 6 0 9.5 6.5 9.5 6.5a17 17 0 0 1-2.6 3.4M6.5 6.6C3.9 8.2 2.5 12 2.5 12S6 18.5 12 18.5c1.8 0 3.3-.5 4.6-1.2"/><path d="M9.9 9.9a2.8 2.8 0 0 0 4.1 3.9"/>',
    lock: '<rect x="5" y="10.5" width="14" height="10" rx="2.2"/><path d="M8 10.5V7.8a4 4 0 0 1 8 0v2.7"/>',
    unlock: '<rect x="5" y="10.5" width="14" height="10" rx="2.2"/><path d="M8 10.5V7.8a4 4 0 0 1 7.6-1.7"/>',
    up: '<path d="m6 14 6-6 6 6"/>',
    down: '<path d="m6 10 6 6 6-6"/>',
    top: '<path d="M6 4.5h12"/><path d="m7 14 5-5 5 5"/><path d="M12 9v11"/>',
    bottom: '<path d="M6 19.5h12"/><path d="m7 10 5 5 5-5"/><path d="M12 15V4"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    search: '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/>',
    pencil: '<path d="M14.5 5.5l4 4L8 20H4v-4z"/><path d="m12.5 7.5 4 4"/>',
    trash: '<path d="M4.5 7h15"/><path d="M9.5 7V4.5h5V7"/><path d="M6.5 7l1 13h9l1-13"/>',
    folder: '<path d="M3.5 6.5a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2v8.5a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z"/><path d="M12 11v5M9.5 13.5h5"/>',
    list: '<path d="M9 6.5h11M9 12h11M9 17.5h11"/><circle cx="4.5" cy="6.5" r=".9"/><circle cx="4.5" cy="12" r=".9"/><circle cx="4.5" cy="17.5" r=".9"/>',
    chevron: '<path d="m6 9 6 6 6-6"/>',
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
    tv: '<rect x="2.5" y="6.5" width="19" height="13" rx="2.4"/><path d="m8 2.8 4 3.7 4-3.7"/>',
  };

  function icon(name, size = 18, filled = false) {
    const svg = document.createElementNS(SVG, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('width', size);
    svg.setAttribute('height', size);
    svg.setAttribute('fill', filled ? 'currentColor' : 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', name === 'grip' ? '0' : '1.8');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    svg.setAttribute('aria-hidden', 'true');
    if (name === 'grip') svg.setAttribute('fill', 'currentColor');
    svg.innerHTML = ICONS[name];
    return svg;
  }

  /** An icon button, its word read aloud and shown on hover. */
  function iconButton(name, label, onclick, extra) {
    const on = extra && extra.on;
    return h('button', { class: `ib ${extra && extra.kind ? extra.kind : ''} ${on ? 'on' : ''}`, type: 'button', title: label, 'aria-label': label, 'aria-pressed': extra && 'on' in extra ? String(!!on) : null, disabled: extra && extra.disabled, onclick },
      icon(name, 18, name === 'star' && on));
  }

  function toast(text, error) {
    const box = document.getElementById('toasts');
    const item = h('div', { class: `toast ${error ? 'error' : ''}` }, text);
    box.append(item);
    setTimeout(() => item.remove(), error ? 6000 : 3200);
  }

  /** A centred question, answered true or false. */
  function ask(title, text, confirm, danger) {
    return new Promise((resolve) => {
      const done = (answer) => { scrim.remove(); document.removeEventListener('keydown', keys, true); resolve(answer); };
      const keys = (event) => { if (event.key === 'Escape') { event.stopPropagation(); done(false); } };
      const yes = h('button', { class: `btn ${danger ? 'danger' : 'primary'}`, type: 'button', onclick: () => done(true) }, confirm);
      const scrim = h('div', { class: 'scrim', onclick: (event) => { if (event.target === scrim) done(false); } },
        h('div', { class: 'dialog', role: 'dialog', 'aria-modal': 'true', 'aria-label': title },
          h('h2', { text: title }), h('p', { class: 'lead', text }),
          h('div', { class: 'buttons' }, h('button', { class: 'btn', type: 'button', onclick: () => done(false) }, T.cancel), yes)));
      document.addEventListener('keydown', keys, true);
      document.body.append(scrim);
      yes.focus();
    });
  }

  // ---- Talking to the TV ------------------------------------------------------------------------

  class Ended extends Error {}

  /** The list a row is being dragged in, if one is: nothing draws that list again until it's dropped. */
  let dragging = null;
  const DRAG_EDGE = 64;
  const DRAG_SPEED = 900;

  let session = load();

  function load() {
    try { return JSON.parse(sessionStorage.getItem(STORE) || 'null'); } catch (e) { return null; }
  }

  function keep() {
    try {
      if (session) sessionStorage.setItem(STORE, JSON.stringify(session)); else sessionStorage.removeItem(STORE);
    } catch (e) { /* A browser that keeps nothing: this tab keeps it in memory. */ }
  }

  /** Asks the TV; a session that ended shows why, and anything else throws. */
  async function call(method, name, body) {
    if (!session) throw new Ended();
    let response;
    try {
      response = await fetch(API + name, {
        method,
        headers: Object.assign({ 'X-Plasma-Session': session.token }, body ? { 'Content-Type': 'application/json' } : {}),
        body: body ? JSON.stringify(body) : undefined,
        cache: 'no-store',
        credentials: 'omit',
      });
    } catch (offline) {
      reachable(false);
      throw offline;
    }
    reachable(true);
    const json = await response.json().catch(() => ({}));
    if (response.status === 401) {
      ended(json.reason || 'tv');
      throw new Ended();
    }
    if (!response.ok) {
      const error = new Error(json.error || String(response.status));
      error.status = response.status;
      error.json = json;
      throw error;
    }
    return json;
  }

  function browserName() {
    const ua = navigator.userAgent;
    const browser = /Edg\//.test(ua) ? 'Edge' : /OPR\//.test(ua) ? 'Opera' : /Firefox\//.test(ua) ? 'Firefox'
      : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : '';
    const device = /iPhone/.test(ua) ? 'iPhone' : (/iPad/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)) ? 'iPad'
      : /Android/.test(ua) ? 'Android' : /CrOS/.test(ua) ? 'ChromeOS' : /Mac OS X|Macintosh/.test(ua) ? 'Mac'
        : /Windows/.test(ua) ? 'Windows' : /Linux/.test(ua) ? 'Linux' : '';
    return [browser, device].filter(Boolean).join(' · ') || T.browser;
  }

  // ---- What the page knows ----------------------------------------------------------------------

  const S = {
    lib: null,
    /** Indexes made from [lib]. */
    byId: new Map(),
    groupById: new Map(),
    sourceById: new Map(),
    favorites: new Set(),
    members: new Map(),
    /** A source's categories of one kind, by "source|kind". */
    categories: new Map(),
    /** Whether the TV changed while changes from here were on their way. */
    stale: false,
    view: null,
    online: true,
  };

  function index(lib) {
    S.lib = lib;
    S.byId = new Map(lib.channels.map((channel) => {
      channel.fold = fold(channel.name);
      return [channel.id, channel];
    }));
    S.groupById = new Map((lib.groups || []).map((group) => [group.id, group]));
    S.sourceById = new Map(lib.sources.map((source) => [source.id, source]));
    S.favorites = new Set(lib.favorites);
    S.members = new Map((lib.groups || []).filter((group) => group.custom).map((group) => [group.id, new Set(group.members || [])]));
  }

  const channelOf = (id) => S.byId.get(id);
  const isFavorite = (id) => S.favorites.has(id);
  const isHidden = (channel) => !!(channel.hidden || channel.groupHidden);
  const isLocked = (channel) => !!(channel.locked || channel.groupLocked);
  const manySources = () => S.lib && S.lib.sources.filter((source) => source.enabled).length > 1;

  /** "Sports · Home Portal", or the group alone with one source. */
  function channelMeta(channel) {
    const group = S.groupById.get(channel.group);
    const parts = [];
    if (group) parts.push(group.name);
    if (manySources()) parts.push((S.sourceById.get(channel.source) || {}).name);
    return parts.filter(Boolean).join(' · ');
  }

  /** The favorites as the TV numbers them: from 1 among those that show, or their own numbers (5.2). */
  function favoriteNumbers() {
    const numbers = new Map();
    let place = 0;
    for (const id of S.lib.favorites) {
      const channel = channelOf(id);
      if (!channel) continue;
      if (isHidden(channel)) numbers.set(id, '–');
      else numbers.set(id, S.lib.favoritesFromOne ? String(++place) : String(channel.n));
    }
    return numbers;
  }

  // ---- Changes, one after another ----------------------------------------------------------------

  // Each change shows at once, and goes to the TV in the order it was made: two drags in a row
  // can't arrive the other way round.
  const queue = [];
  let flushing = false;

  function change(name, body, after) {
    queue.push({ name, body, after });
    syncState();
    flush();
  }

  async function flush() {
    if (flushing) return;
    flushing = true;
    let failed = false;
    while (queue.length) {
      const next = queue[0];
      try {
        const result = await call('POST', next.name, next.body);
        if (next.after) next.after(result);
      } catch (error) {
        if (error instanceof Ended) { queue.length = 0; break; }
        failed = true;
        if (error.status === 403 && error.json && error.json.error === 'pin') S.lib.unlocked = false;
      }
      queue.shift();
    }
    flushing = false;
    syncState();
    if (failed) toast(T.saveFailed, true);
    // What the TV has now, so a change that didn't go through doesn't stay on the page.
    if (failed || S.stale) refresh();
  }

  function syncState() {
    const sync = document.getElementById('sync');
    if (sync) sync.textContent = queue.length || flushing ? T.saving : '';
  }

  // ---- Keeping up with the TV --------------------------------------------------------------------

  async function refresh() {
    if (!session) return;
    S.stale = false;
    try {
      const lib = await call('GET', 'library');
      if (queue.length || flushing) { S.stale = true; return; }
      // Not while a row is dragged or a name typed: the next look at the TV brings it in.
      if (busy()) return;
      index(lib);
      const view = S.view;
      if (view && view.kind === 'categories') await loadCategories(view.source, view.categoryKind, true);
      renderNav();
      if (S.view && S.view.update) S.view.update();
    } catch (error) {
      if (!(error instanceof Ended)) S.stale = true;
    }
  }

  function busy() {
    const typing = document.activeElement && document.activeElement.closest && document.activeElement.closest('.rename, .new-category');
    return !!dragging || !!typing;
  }

  async function poll() {
    if (!session || document.hidden || !S.lib) return;
    try {
      const { rev } = await call('GET', 'rev');
      if (rev !== S.lib.rev) {
        if (queue.length || flushing) S.stale = true; else refresh();
      }
    } catch (error) { /* Shown in the header. */ }
  }

  function reachable(yes) {
    if (S.online === yes) return;
    S.online = yes;
    const tv = document.getElementById('tv');
    if (!tv) return;
    tv.querySelector('.dot').className = `dot ${yes ? 'ok' : 'warn'}`;
    tv.querySelector('span').textContent = yes ? tvLine() : fmt(T.unreachable, T.tv);
  }

  const tvLine = () => [T.tv, session && session.profile ? fmt(T.editingAs, session.profile) : ''].filter(Boolean).join(' · ');

  async function loadCategories(source, kind, force) {
    const key = `${source}|${kind}`;
    if (!force && S.categories.has(key)) return S.categories.get(key);
    const result = await call('GET', `categories?source=${encodeURIComponent(source)}&kind=${kind}`);
    S.categories.set(key, result);
    return result;
  }

  // ---- Logos, through the TV ----------------------------------------------------------------------

  const noLogo = new Set();

  function plate(channel) {
    const box = h('div', { class: 'plate' });
    if (channel.logo && !noLogo.has(channel.id) && session) {
      const img = h('img', { alt: '', loading: 'lazy', decoding: 'async', src: `/edit/logo/${session.media}/${encodeURIComponent(channel.id)}` });
      img.addEventListener('error', () => { noLogo.add(channel.id); img.replaceWith(channel.name); });
      box.append(img);
    } else {
      box.textContent = channel.name;
    }
    return box;
  }

  // ---- Lists ---------------------------------------------------------------------------------------

  /**
   * A long list that only makes the rows in view: thousands of channels scroll as lightly as a
   * dozen. Up and Down move focus from row to row.
   */
  class VirtualList {
    constructor(scroller, row) {
      this.scroller = scroller;
      this.row = row;
      this.items = [];
      this.box = h('div', { class: 'vlist', role: 'list' });
      scroller.append(this.box);
      scroller.addEventListener('scroll', () => this.paint(false), { passive: true });
      this.resize = new ResizeObserver(() => this.paint(true));
      this.resize.observe(scroller);
      this.box.addEventListener('keydown', (event) => this.keys(event));
    }

    set(items) {
      this.items = items;
      this.box.style.height = `${items.length * ROW}px`;
      this.paint(true);
    }

    paint(force) {
      const top = this.scroller.scrollTop - this.box.offsetTop;
      const height = this.scroller.clientHeight;
      const first = Math.max(0, Math.floor(top / ROW) - 8);
      const last = Math.min(this.items.length, Math.ceil((top + height) / ROW) + 8);
      if (!force && first === this.first && last === this.last) return;
      this.first = first;
      this.last = last;
      const focused = document.activeElement && this.box.contains(document.activeElement) ? document.activeElement.closest('.row') : null;
      const focusedId = focused ? focused.dataset.id : null;
      const rows = [];
      for (let i = first; i < last; i++) {
        const row = this.row(this.items[i], i);
        row.style.top = `${i * ROW}px`;
        row.setAttribute('role', 'listitem');
        rows.push(row);
      }
      this.box.replaceChildren(...rows);
      if (focusedId) {
        const again = this.box.querySelector(`.row[data-id="${CSS.escape(focusedId)}"]`);
        if (again) again.focus({ preventScroll: true });
      }
    }

    /** Puts focus on the row at [at], scrolled into view. */
    focusAt(at) {
      if (at < 0 || at >= this.items.length) return;
      const top = this.box.offsetTop + at * ROW;
      if (top < this.scroller.scrollTop) this.scroller.scrollTop = top;
      else if (top + ROW > this.scroller.scrollTop + this.scroller.clientHeight) this.scroller.scrollTop = top + ROW - this.scroller.clientHeight;
      this.paint(true);
      const row = this.box.querySelector(`.row[data-index="${at}"]`);
      if (row) row.focus({ preventScroll: true });
    }

    keys(event) {
      if (event.altKey || event.ctrlKey || event.metaKey) return;
      const row = event.target.closest && event.target.closest('.row');
      if (!row || event.target !== row) return;
      const at = Number(row.dataset.index);
      if (event.key === 'ArrowDown') { event.preventDefault(); this.focusAt(at + 1); }
      if (event.key === 'ArrowUp') { event.preventDefault(); this.focusAt(at - 1); }
    }
  }

  /**
   * Rows that can be put in another order: dragged by their grip, with a mouse or a finger, the
   * list scrolling near its ends; or moved with Alt and the arrow keys. [moved] hears from where
   * to where.
   */
  function sortable(list, scroller, moved) {
    list.addEventListener('pointerdown', (event) => {
      const grip = event.target.closest('.grip');
      if (!grip || event.button !== 0 || !list.contains(grip) || dragging) return;
      const row = grip.closest('.row');
      const rows = [...list.querySelectorAll(':scope > .row')];
      const from = rows.indexOf(row);
      if (from < 0) return;
      event.preventDefault();
      grip.setPointerCapture(event.pointerId);
      const box = row.getBoundingClientRect();
      const offset = event.clientY - box.top;
      const gap = h('div', { class: 'drop-gap' });
      row.after(gap);
      row.classList.add('dragging');
      Object.assign(row.style, { position: 'fixed', left: `${box.left}px`, width: `${box.width}px`, top: `${box.top}px`, pointerEvents: 'none' });
      document.body.classList.add('is-dragging');
      dragging = { list };
      let pointerY = event.clientY;
      let frame = 0;
      let last = 0;
      let carry = 0;

      // The gap goes before the first row whose middle is below the pointer.
      const place = () => {
        row.style.top = `${pointerY - offset}px`;
        const others = [...list.querySelectorAll(':scope > .row:not(.dragging)')];
        let before = null;
        for (const other of others) {
          const rect = other.getBoundingClientRect();
          if (pointerY < rect.top + rect.height / 2) { before = other; break; }
        }
        if (before) { if (gap.nextElementSibling !== before) list.insertBefore(gap, before); } else if (list.lastElementChild !== gap) list.append(gap);
      };
      // Near the list's top or bottom it scrolls, faster the nearer the edge, never faster than
      // DRAG_SPEED however far past the edge the pointer goes, and as fast on a slow frame as a quick one.
      const scroll = (time) => {
        const seconds = last ? Math.min(0.05, (time - last) / 1000) : 1 / 60;
        last = time;
        const edge = scroller.getBoundingClientRect();
        const zone = Math.min(DRAG_EDGE, edge.height / 4);
        const into = pointerY < edge.top + zone ? (pointerY - edge.top - zone) / zone : pointerY > edge.bottom - zone ? (pointerY - edge.bottom + zone) / zone : 0;
        if (into) {
          const depth = Math.min(1, Math.abs(into));
          carry += Math.sign(into) * DRAG_SPEED * (0.15 + 0.85 * depth * depth) * seconds;
          const whole = Math.trunc(carry);
          if (whole) {
            carry -= whole;
            const before = scroller.scrollTop;
            scroller.scrollTop = before + whole;
            if (scroller.scrollTop !== before) place();
          }
        } else {
          carry = 0;
        }
        frame = requestAnimationFrame(scroll);
      };
      frame = requestAnimationFrame(scroll);
      const move = (e) => { pointerY = e.clientY; place(); };
      // The wheel or a trackpad scrolling the list while a row is held.
      const scrolled = () => place();
      let done = false;
      const end = (keep) => {
        if (done) return;
        done = true;
        dragging = null;
        cancelAnimationFrame(frame);
        grip.removeEventListener('pointermove', move);
        grip.removeEventListener('pointerup', drop);
        grip.removeEventListener('pointercancel', cancel);
        grip.removeEventListener('lostpointercapture', cancel);
        scroller.removeEventListener('scroll', scrolled);
        window.removeEventListener('blur', cancel);
        document.removeEventListener('keydown', escape, true);
        document.body.classList.remove('is-dragging');
        const to = [...list.children].filter((child) => child === gap || (child.classList.contains('row') && child !== row)).indexOf(gap);
        // Cancelled, the row never left its place: only the gap goes.
        gap.remove();
        row.classList.remove('dragging');
        row.removeAttribute('style');
        if (keep && to >= 0 && to !== from) moved(from, to);
        else if (S.view && S.view.waiting) S.view.update();
      };
      const drop = () => end(true);
      const cancel = () => end(false);
      const escape = (e) => { if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); cancel(); } };
      grip.addEventListener('pointermove', move);
      grip.addEventListener('pointerup', drop);
      grip.addEventListener('pointercancel', cancel);
      grip.addEventListener('lostpointercapture', cancel);
      scroller.addEventListener('scroll', scrolled, { passive: true });
      window.addEventListener('blur', cancel);
      document.addEventListener('keydown', escape, true);
    });
    list.addEventListener('keydown', (event) => {
      if (!event.altKey || (event.key !== 'ArrowUp' && event.key !== 'ArrowDown')) return;
      const row = event.target.closest('.row');
      const rows = [...list.querySelectorAll(':scope > .row')];
      const from = rows.indexOf(row);
      if (from < 0) return;
      const to = from + (event.key === 'ArrowUp' ? -1 : 1);
      event.preventDefault();
      if (to < 0 || to >= rows.length) return;
      moved(from, to, row.dataset.id);
    });
  }

  /** [list] with the item at [from] put at [to]. */
  function moveItem(list, from, to) {
    const copy = list.slice();
    const [item] = copy.splice(from, 1);
    copy.splice(to, 0, item);
    return copy;
  }

  /** Puts focus back on the row for [id] once the list is drawn again. */
  function refocus(container, id) {
    requestAnimationFrame(() => {
      const row = container.querySelector(`.row[data-id="${CSS.escape(id)}"]`);
      if (row) row.focus({ preventScroll: false });
    });
  }

  function searchField(placeholder, oninput) {
    const input = h('input', { type: 'search', placeholder, 'aria-label': placeholder, autocomplete: 'off', spellcheck: 'false', oninput });
    return { input, field: h('label', { class: 'field search' }, icon('search', 18), input) };
  }

  /** A group to pick, the groups of each source together. */
  function groupPicker(onchange, live) {
    const select = h('select', { 'aria-label': T.allGroups, onchange });
    select.append(h('option', { value: '' }, T.allGroups));
    const groups = (S.lib.groups || []).filter((group) => live || !group.custom || (S.members.get(group.id) || new Set()).size);
    for (const source of S.lib.sources.filter((s) => s.enabled)) {
      const own = groups.filter((group) => group.source === source.id);
      if (!own.length) continue;
      const box = manySources() ? h('optgroup', { label: source.name }) : select;
      for (const group of own) box.append(h('option', { value: group.id }, group.name));
      if (box !== select) select.append(box);
    }
    return { select, field: h('label', { class: 'field picker' }, select, icon('chevron', 14)) };
  }

  /** The channels [query] and [groupId] leave: by name, or by number for digits. */
  function filterChannels(query, groupId, keep) {
    const q = fold(query.trim());
    const digits = /^\d+$/.test(q);
    const members = groupId ? S.members.get(groupId) : null;
    return S.lib.channels.filter((channel) => {
      if (groupId && (members ? !members.has(channel.id) : channel.group !== groupId)) return false;
      if (keep && !keep(channel)) return false;
      if (!q) return true;
      return digits ? String(channel.n).startsWith(q) || channel.fold.includes(q) : channel.fold.includes(q);
    });
  }

  /**
   * Channels to pick from, searched by name or number and narrowed to a group, each with a
   * button that puts it in or takes it out: Favorites, a category of the user's own, or Locks.
   */
  class ChannelPicker {
    constructor(container, options) {
      this.options = options;
      const search = searchField(T.searchChannels, () => this.update());
      this.search = search.input;
      const groups = groupPicker(() => this.update(), true);
      this.group = groups.select;
      this.all = h('button', { class: 'btn small', type: 'button', onclick: () => this.addAll() }, '');
      this.meta = h('span', { class: 'meta' });
      const head = h('div', { class: options.drawer ? 'drawer-head' : 'pane-head' },
        h('div', { class: 'title-line' }, h('h2', { text: options.title }), options.close || null),
        search.field, groups.field,
        options.addAll ? h('div', { class: 'tools' }, this.meta, h('div', { class: 'grow' }), this.all) : null);
      this.scroller = h('div', { class: 'scroll' });
      container.append(head, this.scroller);
      this.list = new VirtualList(this.scroller, (channel, i) => this.row(channel, i));
      this.update();
    }

    update() {
      this.items = filterChannels(this.search.value, this.group.value, this.options.keep);
      this.list.set(this.items);
      if (this.options.addAll) {
        const missing = this.items.filter((channel) => !this.options.isOn(channel));
        this.meta.textContent = plural('channelCount', this.items.length);
        this.all.textContent = fmt(T.addAllShown, NUMBER.format(missing.length));
        this.all.hidden = !(this.search.value || this.group.value) || !missing.length || missing.length > MAX_ADD_ALL;
      }
    }

    addAll() {
      this.options.addAll(this.items.filter((channel) => !this.options.isOn(channel)).map((channel) => channel.id));
    }

    row(channel, i) {
      const on = this.options.isOn(channel);
      const label = on ? this.options.offLabel : this.options.onLabel;
      return h('div', { class: `row ${isHidden(channel) ? 'faint' : ''}`, tabindex: '0', dataset: { id: channel.id, index: i },
        onkeydown: (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); this.flip(channel); } } },
      h('div', { class: 'num', text: channel.n }),
      plate(channel),
      h('div', { class: 'what' }, h('div', { class: 'nm', text: channel.name }), h('div', { class: 'meta', text: channelMeta(channel) })),
      iconButton(this.options.icon(on), label, () => this.flip(channel), { on, kind: this.options.kind }));
    }

    flip(channel) {
      this.options.toggle(channel);
      this.update();
    }
  }

  function drawer(build) {
    const close = () => { scrim.remove(); panel.remove(); document.removeEventListener('keydown', keys, true); if (panel.onclose) panel.onclose(); };
    const keys = (event) => { if (event.key === 'Escape' && !document.querySelector('.scrim')) { event.stopPropagation(); close(); } };
    const scrim = h('div', { class: 'drawer-scrim', onclick: close });
    const panel = h('aside', { class: 'drawer', role: 'dialog', 'aria-modal': 'true' });
    document.addEventListener('keydown', keys, true);
    document.body.append(scrim, panel);
    build(panel, iconButton('close', T.close, close));
    const first = panel.querySelector('input, button');
    if (first) first.focus();
    return { panel, close };
  }

  // ---- Favorites ---------------------------------------------------------------------------------

  /** The profile's favorites in their order, with channels to add beside them (brief 5.2). */
  class FavoritesView {
    constructor(main) {
      this.kind = 'favorites';
      this.count = h('span', { class: 'meta' });
      this.lead = h('p', { class: 'lead' });
      this.numbers = h('p', { class: 'meta' });
      const add = h('div', { class: 'tools' }, h('button', { class: 'btn primary', type: 'button', onclick: () => this.openPicker() }, icon('plus', 16), T.addChannels));
      main.append(h('div', { class: 'view-head' },
        h('div', { class: 'title-line' }, h('h1', { text: T.favorites }), this.count),
        this.lead, this.numbers, add));
      this.listScroll = h('div', { class: 'scroll' });
      this.list = h('div', { role: 'list' });
      this.listScroll.append(this.list);
      const side = h('div', { class: 'pane side-pane' });
      main.append(h('div', { class: 'view-body' }, h('div', { class: 'pane' }, this.listScroll), side));
      this.picker = new ChannelPicker(side, this.pickerOptions());
      sortable(this.list, this.listScroll, (from, to, id) => this.move(from, to, id));
      this.wide = window.matchMedia('(max-width: 1180px)');
      const narrow = () => { add.hidden = !this.wide.matches; };
      this.wide.addEventListener('change', narrow);
      narrow();
      this.update();
    }

    pickerOptions(drawerClose) {
      return {
        title: T.addChannels,
        drawer: !!drawerClose,
        close: drawerClose,
        kind: 'star',
        icon: () => 'star',
        isOn: (channel) => isFavorite(channel.id),
        onLabel: T.addFavorite,
        offLabel: T.removeFavorite,
        toggle: (channel) => this.setFavorite(channel.id, !isFavorite(channel.id)),
        addAll: (ids) => this.addMany(ids),
      };
    }

    openPicker() {
      drawer((panel, close) => {
        const picker = new ChannelPicker(panel, this.pickerOptions(close));
        this.drawerPicker = picker;
        panel.onclose = () => { this.drawerPicker = null; };
      });
    }

    update() {
      // Drawn again once the row being dragged is dropped.
      this.waiting = !!dragging;
      if (this.waiting) return;
      const ids = S.lib.favorites.filter((id) => channelOf(id));
      this.count.textContent = plural('channelCount', ids.length);
      this.lead.textContent = fmt(T.favoritesText, S.lib.profile || (session && session.profile) || '');
      this.numbers.replaceChildren(S.lib.favoritesFromOne ? T.countsFromOne : T.keepsNumbers, ' ',
        h('a', { class: 'link', href: '#numbering', text: T.change }));
      const numbers = favoriteNumbers();
      const focused = document.activeElement && this.list.contains(document.activeElement) ? document.activeElement.closest('.row') : null;
      if (!ids.length) {
        this.list.replaceChildren(h('div', { class: 'empty' }, icon('star', 34), T.noFavorites));
      } else {
        this.list.replaceChildren(...ids.map((id, i) => this.row(channelOf(id), numbers.get(id), i, ids.length)));
      }
      if (focused && focused.dataset.id) refocus(this.list, focused.dataset.id);
      this.picker.update();
      if (this.drawerPicker) this.drawerPicker.update();
    }

    row(channel, number, i, count) {
      const id = channel.id;
      return h('div', { class: `row ${isHidden(channel) ? 'faint' : ''}`, tabindex: '0', role: 'listitem', dataset: { id } },
        h('button', { class: 'grip', type: 'button', title: T.dragToMove, 'aria-label': T.dragToMove }, icon('grip', 16)),
        h('div', { class: 'num', text: number }),
        plate(channel),
        h('div', { class: 'what' },
          h('div', { class: 'nm', text: channel.name }),
          h('div', { class: 'meta', text: [channelMeta(channel), isHidden(channel) ? (channel.groupHidden ? T.hiddenWithGroup : T.hidden) : ''].filter(Boolean).join(' · ') })),
        h('div', { class: 'acts quiet' },
          iconButton('top', T.moveTop, () => this.move(i, 0, id), { disabled: i === 0 }),
          iconButton('up', T.moveUp, () => this.move(i, i - 1, id), { disabled: i === 0 }),
          iconButton('down', T.moveDown, () => this.move(i, i + 1, id), { disabled: i === count - 1 }),
          iconButton('bottom', T.moveBottom, () => this.move(i, count - 1, id), { disabled: i === count - 1 })),
        iconButton('star', T.removeFavorite, () => this.setFavorite(id, false), { on: true, kind: 'star' }));
    }

    move(from, to, id) {
      const ids = S.lib.favorites.filter((fav) => channelOf(fav));
      if (to < 0 || to >= ids.length || from === to) return;
      const order = moveItem(ids, from, to);
      S.lib.favorites = order;
      this.update();
      if (id) refocus(this.list, id);
      change('favorites', { order });
    }

    setFavorite(id, on) {
      if (on === isFavorite(id)) return;
      if (on) { S.lib.favorites.push(id); S.favorites.add(id); } else { S.lib.favorites = S.lib.favorites.filter((fav) => fav !== id); S.favorites.delete(id); }
      this.update();
      renderNav();
      change('favorites', on ? { add: [id] } : { remove: [id] });
    }

    addMany(ids) {
      const fresh = ids.filter((id) => !isFavorite(id));
      if (!fresh.length) return;
      fresh.forEach((id) => { S.lib.favorites.push(id); S.favorites.add(id); });
      this.update();
      renderNav();
      change('favorites', { add: fresh });
    }
  }

  // ---- Channels ----------------------------------------------------------------------------------

  /** Every channel: star it, hide it, lock it, or put it in a category of your own, one or many at once. */
  class ChannelsView {
    constructor(main) {
      this.kind = 'channels';
      this.filter = 'all';
      this.picked = new Set();
      this.count = h('span', { class: 'meta' });
      const search = searchField(T.searchChannels, () => this.update());
      this.search = search.input;
      const groups = groupPicker(() => this.update(), true);
      this.group = groups.select;
      this.chips = h('div', { class: 'chips', role: 'group' });
      main.append(h('div', { class: 'view-head' },
        h('div', { class: 'title-line' }, h('h1', { text: T.channels }), this.count),
        h('p', { class: 'lead', text: T.channelsText }),
        h('div', { class: 'tools' }, search.field, groups.field, this.chips)));
      this.scroller = h('div', { class: 'scroll' });
      this.bar = h('div', { class: 'selbar', hidden: true });
      main.append(this.scroller, this.bar);
      this.list = new VirtualList(this.scroller, (channel, i) => this.row(channel, i));
      this.update();
    }

    filters() {
      const all = S.lib.channels;
      const list = [
        ['all', T.filterAll, all.length],
        ['favorites', T.filterFavorites, all.filter((c) => isFavorite(c.id)).length],
        ['hidden', T.filterHidden, all.filter(isHidden).length],
      ];
      if (S.lib.pin) list.push(['locked', T.filterLocked, all.filter(isLocked).length]);
      return list;
    }

    keep() {
      switch (this.filter) {
        case 'favorites': return (c) => isFavorite(c.id);
        case 'hidden': return isHidden;
        case 'locked': return isLocked;
        default: return null;
      }
    }

    update() {
      if (this.filter === 'locked' && !S.lib.pin) this.filter = 'all';
      this.chips.replaceChildren(...this.filters().map(([id, name, n]) => h('button', {
        class: `chip ${this.filter === id ? 'on' : ''}`, type: 'button', 'aria-pressed': String(this.filter === id),
        onclick: () => { this.filter = id; this.update(); },
      }, name, h('span', { class: 'count', text: NUMBER.format(n) }))));
      this.items = filterChannels(this.search.value, this.group.value, this.keep());
      for (const id of [...this.picked]) if (!channelOf(id)) this.picked.delete(id);
      this.count.textContent = plural('channelCount', this.items.length);
      this.list.set(this.items);
      this.drawBar();
    }

    row(channel, i) {
      const id = channel.id;
      const picked = this.picked.has(id);
      const fav = isFavorite(id);
      const meta = [channelMeta(channel)];
      if (channel.groupHidden) meta.push(T.hiddenWithGroup);
      if (S.lib.pin && channel.groupLocked && !channel.locked) meta.push(T.lockedWithGroup);
      const unlocked = S.lib.pin && S.lib.unlocked;
      return h('div', { class: `row ${picked ? 'selected' : ''} ${isHidden(channel) ? 'faint' : ''}`, tabindex: '0', dataset: { id, index: i },
        onkeydown: (event) => { if (event.key === ' ' && event.target === event.currentTarget) { event.preventDefault(); this.pick(id); } } },
      h('input', { class: 'check', type: 'checkbox', checked: picked, 'aria-label': channel.name, tabindex: '-1', onchange: () => this.pick(id) }),
      h('div', { class: 'num', text: channel.n }),
      plate(channel),
      h('div', { class: 'what' }, h('div', { class: 'nm', text: channel.name }), h('div', { class: 'meta', text: meta.filter(Boolean).join(' · ') })),
      h('div', { class: 'acts' },
        iconButton('star', fav ? T.removeFavorite : T.addFavorite, () => this.favorite([id], !fav), { on: fav, kind: 'star' }),
        iconButton(channel.hidden ? 'eyeOff' : 'eye', channel.hidden ? T.show : T.hide, () => this.hide([id], !channel.hidden), { on: channel.hidden, kind: 'hide' }),
        S.lib.pin ? iconButton(channel.locked ? 'lock' : 'unlock', channel.locked ? T.unlock : T.lock, () => this.lock([id], !channel.locked),
          { on: channel.locked, kind: 'lock', disabled: !unlocked }) : null,
        iconButton('folder', T.addToCategory, (event) => this.categories([id], event.currentTarget), { kind: 'wide-only' })));
    }

    pick(id) {
      if (this.picked.has(id)) this.picked.delete(id); else this.picked.add(id);
      this.list.paint(true);
      this.drawBar();
    }

    drawBar() {
      const ids = [...this.picked];
      this.bar.hidden = !ids.length;
      // Room to scroll the last rows out from under the bar.
      this.scroller.style.paddingBottom = ids.length ? '140px' : '';
      if (!ids.length) return;
      const unlocked = S.lib.pin && S.lib.unlocked;
      this.bar.replaceChildren(...[
        h('span', { class: 'meta', text: fmt(T.selected, NUMBER.format(ids.length)) }),
        h('button', { class: 'btn small', type: 'button', onclick: () => this.favorite(ids, true) }, icon('star', 15), T.addFavorite),
        h('button', { class: 'btn small', type: 'button', onclick: () => this.favorite(ids, false) }, T.removeFavorite),
        h('button', { class: 'btn small', type: 'button', onclick: () => this.hide(ids, true) }, icon('eyeOff', 15), T.hide),
        h('button', { class: 'btn small', type: 'button', onclick: () => this.hide(ids, false) }, icon('eye', 15), T.show),
        unlocked ? h('button', { class: 'btn small', type: 'button', onclick: () => this.lock(ids, true) }, icon('lock', 15), T.lock) : null,
        unlocked ? h('button', { class: 'btn small', type: 'button', onclick: () => this.lock(ids, false) }, T.unlock) : null,
        h('button', { class: 'btn small', type: 'button', onclick: (event) => this.categories(ids, event.currentTarget) }, icon('folder', 15), T.addToCategory),
        h('button', { class: 'btn small', type: 'button', onclick: () => { this.picked.clear(); this.update(); } }, T.clearSelection),
        this.items.length && this.items.length <= 5000 ? h('button', { class: 'btn small', type: 'button', onclick: () => { this.items.forEach((c) => this.picked.add(c.id)); this.update(); } }, T.selectAll) : null,
      ].filter(Boolean));
    }

    favorite(ids, on) {
      const changed = ids.filter((id) => isFavorite(id) !== on);
      if (!changed.length) return;
      if (on) changed.forEach((id) => { S.lib.favorites.push(id); S.favorites.add(id); });
      else { const gone = new Set(changed); S.lib.favorites = S.lib.favorites.filter((id) => !gone.has(id)); changed.forEach((id) => S.favorites.delete(id)); }
      this.update();
      renderNav();
      change('favorites', on ? { add: changed } : { remove: changed });
    }

    hide(ids, hidden) {
      ids.forEach((id) => { const c = channelOf(id); if (c) c.hidden = hidden; });
      this.update();
      renderNav();
      change('channels', { ids, hidden });
    }

    lock(ids, locked) {
      if (!S.lib.unlocked) return;
      ids.forEach((id) => { const c = channelOf(id); if (c) c.locked = locked; });
      this.update();
      change('channels', { ids, locked });
    }

    categories(ids, anchor) {
      categoryPopover(ids, anchor, () => this.update());
    }
  }

  /** The user's own Live TV categories, ticked where the channels are in them, to put them in or take them out. */
  function categoryPopover(ids, anchor, done) {
    document.querySelectorAll('.popover').forEach((open) => open.remove());
    const own = (S.lib.groups || []).filter((group) => group.custom);
    const box = h('div', { class: 'popover', role: 'dialog', 'aria-label': T.addToCategory });
    const close = () => { box.remove(); document.removeEventListener('pointerdown', outside, true); document.removeEventListener('keydown', keys, true); };
    const outside = (event) => { if (!box.contains(event.target) && event.target !== anchor) close(); };
    const keys = (event) => { if (event.key === 'Escape') { event.stopPropagation(); close(); anchor.focus(); } };
    if (!own.length) box.append(h('div', { class: 'empty', text: T.noOwnCategories }));
    for (const group of own) {
      const members = S.members.get(group.id) || new Set();
      const inIt = ids.filter((id) => members.has(id)).length;
      const check = h('input', { class: 'check', type: 'checkbox', checked: inIt === ids.length });
      check.indeterminate = inIt > 0 && inIt < ids.length;
      check.addEventListener('change', () => {
        const add = check.checked;
        const target = ids.filter((id) => members.has(id) !== add);
        target.forEach((id) => (add ? members.add(id) : members.delete(id)));
        S.members.set(group.id, members);
        group.members = [...members];
        change('categories', { op: 'members', kind: 'live', id: group.id, add: add ? target : [], remove: add ? [] : target });
        done();
      });
      const source = manySources() ? ` · ${(S.sourceById.get(group.source) || {}).name || ''}` : '';
      box.append(h('label', { class: 'opt' }, check, h('span', { text: group.name + source })));
    }
    document.body.append(box);
    const rect = anchor.getBoundingClientRect();
    const width = 300;
    box.style.left = `${Math.max(8, Math.min(window.innerWidth - width - 8, rect.right - width))}px`;
    const below = rect.bottom + 6;
    const height = Math.min(360, box.scrollHeight);
    box.style.top = `${below + height > window.innerHeight - 8 ? Math.max(8, rect.top - height - 6) : below}px`;
    document.addEventListener('pointerdown', outside, true);
    document.addEventListener('keydown', keys, true);
    const first = box.querySelector('input');
    if (first) first.focus();
  }

  // ---- A source's categories -----------------------------------------------------------------------

  const KIND_NAMES = { live: 'liveTv', movie: 'movies', series: 'series' };

  /**
   * A source's Live TV, movie or series categories, as its Categories page has them (#103):
   * shown or hidden, renamed, in an order, and the user's own with what's in them.
   */
  class CategoriesView {
    constructor(main, source, kind) {
      this.kind = 'categories';
      this.source = source;
      this.categoryKind = kind;
      this.title = h('h1');
      this.count = h('span', { class: 'meta' });
      this.order = h('div', { class: 'seg', role: 'group', 'aria-label': T.order });
      this.creating = false;
      main.append(h('div', { class: 'view-head' },
        h('div', { class: 'title-line' }, this.title, this.count),
        h('p', { class: 'lead', text: T.categoriesText }),
        h('div', { class: 'tools' }, this.order, h('div', { class: 'grow' }),
          h('button', { class: 'btn', type: 'button', onclick: () => this.hideAll(false) }, T.showAll),
          h('button', { class: 'btn', type: 'button', onclick: () => this.hideAll(true) }, T.hideAll),
          h('button', { class: 'btn primary', type: 'button', onclick: () => this.startCreating() }, icon('plus', 16), T.newCategory))));
      this.scroller = h('div', { class: 'scroll' });
      this.form = h('div');
      this.list = h('div', { role: 'list' });
      this.scroller.append(this.form, this.list);
      main.append(this.scroller);
      sortable(this.list, this.scroller, (from, to, id) => this.move(from, to, id));
      this.list.replaceChildren(h('div', { class: 'empty', text: '…' }));
      this.load();
    }

    async load() {
      try {
        this.data = await loadCategories(this.source, this.categoryKind, true);
        this.update();
      } catch (error) {
        if (!(error instanceof Ended)) this.list.replaceChildren(h('div', { class: 'empty', text: T.saveFailed }));
      }
    }

    update() {
      // Drawn again once the row being dragged is dropped.
      this.waiting = !!dragging;
      if (this.waiting) return;
      const source = S.sourceById.get(this.source);
      this.data = S.categories.get(`${this.source}|${this.categoryKind}`) || this.data;
      this.title.textContent = T[KIND_NAMES[this.categoryKind]];
      if (!this.data) return;
      const hidden = this.data.categories.filter((c) => c.hidden).length;
      this.count.textContent = [source ? source.name : '', plural('categoryCount', this.data.categories.length), hidden ? fmt(T.hiddenCount, NUMBER.format(hidden)) : ''].filter(Boolean).join(' · ');
      const orders = [['provider', T.providerOrder], ['az', T.aToZ], ['mine', T.myOrder]];
      this.order.replaceChildren(...orders.map(([id, name]) => h('button', {
        class: this.data.order === id ? 'on' : '', type: 'button', 'aria-pressed': String(this.data.order === id), onclick: () => this.setOrder(id),
      }, name)));
      this.drawForm();
      const rows = this.data.categories.map((category, i) => this.row(category, i));
      this.list.replaceChildren(...(rows.length ? rows : [h('div', { class: 'empty', text: T.nothingInCategory })]));
    }

    row(category, i) {
      const unlocked = S.lib.pin && S.lib.unlocked;
      const live = this.categoryKind === 'live';
      const what = this.renaming === category.id ? this.renameField(category) : h('div', { class: 'what' },
        h('div', { class: 'nm', text: category.name }),
        category.provider ? h('div', { class: 'meta', text: fmt(T.providerName, category.provider) }) : null);
      const edit = { live: T.editChannels, movie: T.editMovies, series: T.editSeries }[this.categoryKind];
      return h('div', { class: `row ${category.hidden ? 'faint' : ''}`, tabindex: '0', dataset: { id: category.id } },
        h('button', { class: 'grip', type: 'button', title: T.dragToMove, 'aria-label': T.dragToMove }, icon('grip', 16)),
        h('button', { class: `switch ${category.hidden ? '' : 'on'}`, type: 'button', role: 'switch', 'aria-checked': String(!category.hidden),
          'aria-label': category.hidden ? T.hidden : T.shown, title: category.hidden ? T.hidden : T.shown, onclick: () => this.setHidden(category, !category.hidden) }),
        what,
        category.custom ? h('span', { class: 'tag', text: T.yours }) : null,
        category.adult ? h('span', { class: 'tag warn', text: T.adult }) : null,
        live && S.lib.pin ? (category.custom ? h('span', { class: 'ib-space' })
          : iconButton(category.locked ? 'lock' : 'unlock', category.locked ? T.unlock : T.lock, () => this.lock(category), { on: category.locked, kind: 'lock', disabled: !unlocked })) : null,
        h('div', { class: 'count', text: NUMBER.format(category.count) }),
        h('div', { class: 'acts quiet' },
          iconButton('pencil', T.rename, () => { this.renaming = category.id; this.update(); refocus(this.list, category.id); }),
          category.custom ? iconButton('list', edit, () => this.members(category)) : null,
          category.custom ? iconButton('trash', T.deleteCategory, () => this.remove(category)) : null));
    }

    renameField(category) {
      const input = h('input', { type: 'text', value: category.name, maxlength: '60', 'aria-label': T.categoryName });
      const done = (save) => {
        const name = input.value.trim();
        this.renaming = null;
        if (save && name !== category.name) this.rename(category, name);
        else this.update();
        refocus(this.list, category.id);
      };
      input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') { event.preventDefault(); done(true); }
        if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); done(false); }
      });
      requestAnimationFrame(() => { input.focus(); input.select(); });
      return h('div', { class: 'rename' }, h('label', { class: 'field' }, input),
        h('button', { class: 'btn small primary', type: 'button', onclick: () => done(true) }, T.save),
        category.provider ? h('button', { class: 'btn small', type: 'button', onclick: () => { input.value = ''; done(true); } }, T.resetName) : null,
        h('button', { class: 'btn small', type: 'button', onclick: () => done(false) }, T.cancel));
    }

    drawForm() {
      if (!this.creating) { this.form.replaceChildren(); return; }
      const input = h('input', { type: 'text', maxlength: '60', placeholder: T.categoryName, 'aria-label': T.categoryName });
      const create = () => { const name = input.value.trim(); if (name) this.create(name); };
      input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') { event.preventDefault(); create(); }
        if (event.key === 'Escape') { event.preventDefault(); this.creating = false; this.drawForm(); }
      });
      this.form.replaceChildren(h('div', { class: 'new-category' }, h('label', { class: 'field' }, input),
        h('button', { class: 'btn primary', type: 'button', onclick: create }, T.create),
        h('button', { class: 'btn', type: 'button', onclick: () => { this.creating = false; this.drawForm(); } }, T.cancel)));
      requestAnimationFrame(() => input.focus());
    }

    startCreating() {
      this.creating = true;
      this.drawForm();
      this.scroller.scrollTop = 0;
    }

    body(extra) {
      return Object.assign({ kind: this.categoryKind, source: this.source }, extra);
    }

    setOrder(order) {
      if (this.data.order === order) return;
      this.data.order = order;
      this.update();
      change('categories', this.body({ op: 'order', order }), () => this.reload());
    }

    move(from, to, id) {
      const list = this.data.categories;
      if (to < 0 || to >= list.length) return;
      const wasMine = this.data.order === 'mine';
      this.data.categories = moveItem(list, from, to);
      this.data.order = 'mine';
      this.update();
      if (id) refocus(this.list, id);
      if (!wasMine) toast(T.movedToMyOrder);
      change('categories', this.body({ op: 'move', ids: this.data.categories.map((c) => c.id) }), () => this.afterLive());
    }

    setHidden(category, hidden) {
      category.hidden = hidden;
      this.update();
      refocus(this.list, category.id);
      change('categories', this.body({ op: 'hide', id: category.id, hidden }), () => this.afterLive());
    }

    hideAll(hidden) {
      this.data.categories.forEach((c) => { c.hidden = hidden; });
      this.update();
      change('categories', this.body({ op: 'hideAll', hidden }), () => this.afterLive());
    }

    rename(category, name) {
      category.name = name || category.provider || category.name;
      if (!name) category.provider = null;
      this.update();
      change('categories', this.body({ op: 'rename', id: category.id, name }), () => this.reload());
    }

    create(name) {
      this.creating = false;
      this.drawForm();
      change('categories', this.body({ op: 'create', name }), async (result) => {
        await this.reload();
        const made = result && result.id && this.data.categories.find((c) => c.id === result.id);
        if (made) { refocus(this.list, made.id); this.members(made); }
      });
    }

    async remove(category) {
      if (!(await ask(T.deleteCategory, fmt(T.deleteText, category.name), T.delete, true))) return;
      this.data.categories = this.data.categories.filter((c) => c !== category);
      this.update();
      change('categories', this.body({ op: 'delete', id: category.id }), () => this.afterLive());
    }

    lock(category) {
      if (!S.lib.unlocked) return;
      category.locked = !category.locked;
      this.update();
      change('group', { id: category.id, locked: category.locked }, () => refresh());
    }

    /** What the TV made of a change it orders or names itself. */
    async reload() {
      try {
        await loadCategories(this.source, this.categoryKind, true);
        if (this.categoryKind === 'live') await refresh();
        if (S.view === this) this.update();
      } catch (error) { /* The next look picks it up. */ }
    }

    afterLive() {
      if (this.categoryKind === 'live') refresh();
    }

    members(category) {
      if (this.categoryKind === 'live') liveMembers(category, () => this.reload());
      else titleMembers(category, this.categoryKind, this.source, () => this.reload());
    }
  }

  /** What's in one of the user's own Live TV categories: its channels, and channels to add. */
  function liveMembers(category, changed) {
    drawer((panel, close) => {
      const members = () => S.members.get(category.id) || new Set();
      const toggle = (channel) => {
        const set = members();
        const add = !set.has(channel.id);
        if (add) set.add(channel.id); else set.delete(channel.id);
        S.members.set(category.id, set);
        change('categories', { op: 'members', kind: 'live', id: category.id, add: add ? [channel.id] : [], remove: add ? [] : [channel.id] }, changed);
      };
      new ChannelPicker(panel, {
        title: category.name,
        drawer: true,
        close,
        kind: 'check',
        icon: (on) => (on ? 'check' : 'plus'),
        isOn: (channel) => members().has(channel.id),
        onLabel: T.add,
        offLabel: T.remove,
        toggle,
        addAll: (ids) => {
          const set = members();
          const fresh = ids.filter((id) => !set.has(id));
          fresh.forEach((id) => set.add(id));
          S.members.set(category.id, set);
          change('categories', { op: 'members', kind: 'live', id: category.id, add: fresh, remove: [] }, changed);
        },
      });
    });
  }

  /** What's in one of the user's own movie or series categories, and titles of its source to add, searched on the TV. */
  function titleMembers(category, kind, source, changed) {
    drawer((panel, close) => {
      const inIt = new Map();
      const seg = h('div', { class: 'seg', role: 'group' });
      const search = searchField(kind === 'movie' ? T.searchMovies : T.searchSeries, () => { clearTimeout(timer); timer = setTimeout(look, SEARCH_DEBOUNCE_MS); });
      const meta = h('span', { class: 'meta' });
      const scroller = h('div', { class: 'scroll' });
      const list = h('div', { role: 'list' });
      scroller.append(list);
      let tab = 'in';
      let found = [];
      let timer = 0;
      panel.append(h('div', { class: 'drawer-head' },
        h('div', { class: 'title-line' }, h('h2', { text: category.name }), close), seg, search.field, meta), scroller);
      const tabs = () => seg.replaceChildren(
        h('button', { class: tab === 'in' ? 'on' : '', type: 'button', onclick: () => { tab = 'in'; draw(); } }, T.inThisCategory),
        h('button', { class: tab === 'add' ? 'on' : '', type: 'button', onclick: () => { tab = 'add'; draw(); search.input.focus(); } }, T.add));
      const flip = (title) => {
        const add = !inIt.has(title.id);
        if (add) inIt.set(title.id, title); else inIt.delete(title.id);
        draw();
        change('categories', { op: 'members', kind, id: category.id, add: add ? [title.id] : [], remove: add ? [] : [title.id] }, changed);
      };
      const draw = () => {
        tabs();
        search.field.hidden = tab !== 'add';
        const items = tab === 'in' ? [...inIt.values()] : found;
        meta.textContent = tab === 'in' ? plural('titleCount', inIt.size) : '';
        list.replaceChildren(...(items.length ? items.map((title) => {
          const on = inIt.has(title.id);
          return h('div', { class: 'row', tabindex: '0', dataset: { id: title.id } },
            h('div', { class: 'what' }, h('div', { class: 'nm', text: title.name }), title.year ? h('div', { class: 'meta', text: String(title.year) }) : null),
            iconButton(on ? 'check' : 'plus', on ? T.remove : T.add, () => flip(title), { on }));
        }) : [h('div', { class: 'empty', text: tab === 'in' ? T.nothingInCategory : (search.input.value.trim() ? T.noMatches : '') })]));
      };
      const look = async () => {
        const q = search.input.value.trim();
        if (!q) { found = []; draw(); return; }
        try {
          const result = await call('GET', `titles?kind=${kind}&source=${encodeURIComponent(source)}&q=${encodeURIComponent(q)}`);
          if (search.input.value.trim() === q) { found = result.titles; draw(); }
        } catch (error) { /* Shown in the header. */ }
      };
      call('GET', `titles?kind=${kind}&category=${encodeURIComponent(category.id)}`).then((result) => {
        result.titles.forEach((title) => inIt.set(title.id, title));
        draw();
      }).catch(() => {});
      draw();
    });
  }

  // ---- Numbering ----------------------------------------------------------------------------------

  /** Numbers in Favorites and Numbers in Groups (4.29), with the favorites' own example (board 29). */
  class NumberingView {
    constructor(main) {
      this.kind = 'numbering';
      main.append(h('div', { class: 'view-head' },
        h('div', { class: 'title-line' }, h('h1', { text: T.numbering })),
        h('p', { class: 'lead', text: T.numberingText })));
      this.cards = h('div', { class: 'cards' });
      const scroller = h('div', { class: 'scroll' });
      scroller.append(this.cards);
      main.append(scroller);
      this.update();
    }

    update() {
      const shown = S.lib.favorites.map(channelOf).filter((c) => c && !isHidden(c));
      const favorites = (shown.length >= 3 ? shown : S.lib.channels.filter((c) => !isHidden(c))).slice(0, 3);
      const group = (S.lib.groups || []).find((g) => !g.custom && !g.hidden && S.lib.channels.filter((c) => c.group === g.id && !isHidden(c)).length >= 3);
      const inGroup = group ? S.lib.channels.filter((c) => c.group === group.id && !isHidden(c)).slice(0, 3) : [];
      this.cards.replaceChildren(
        this.card(T.numbersInFavorites, T.numbersInFavoritesText, S.lib.favoritesFromOne, (fromOne) => this.set({ favoritesFromOne: fromOne }), favorites),
        this.card(T.numbersInGroups, T.numbersInGroupsText, S.lib.groupsFromOne, (fromOne) => this.set({ groupsFromOne: fromOne }), inGroup));
    }

    card(title, text, fromOne, choose, sample) {
      const example = (one) => h('button', { class: `example ${fromOne === one ? 'on' : ''}`, type: 'button', 'aria-pressed': String(fromOne === one), onclick: () => choose(one) },
        h('div', { class: 'label' }, fromOne === one ? icon('check', 14) : null, one ? T.countFromOne : T.keepNumbers),
        ...sample.map((channel, i) => h('div', { class: 'line' }, h('div', { class: 'num', text: one ? i + 1 : channel.n }), plate(channel), h('div', { class: 'nm', text: channel.name }))));
      return h('section', { class: 'panel' }, h('h2', { text: title }), h('p', { class: 'lead', text }),
        h('div', { class: 'examples' }, example(true), example(false)));
    }

    set(body) {
      Object.assign(S.lib, body);
      this.update();
      change('numbering', body);
    }
  }

  // ---- Locks --------------------------------------------------------------------------------------

  /** Locked groups and channels (5.12), once the parental PIN is typed here. */
  class LocksView {
    constructor(main) {
      this.kind = 'locks';
      this.main = main;
      this.head = h('div', { class: 'view-head' },
        h('div', { class: 'title-line' }, h('h1', { text: T.locks })),
        h('p', { class: 'lead', text: T.locksText }));
      this.scroller = h('div', { class: 'scroll' });
      main.append(this.head, this.scroller);
      this.update();
    }

    update() {
      if (!S.lib.pin) {
        this.scroller.replaceChildren(h('div', { class: 'cards' }, h('section', { class: 'panel' }, icon('lock', 28), h('p', { class: 'lead', text: T.locksOff }))));
        return;
      }
      if (!S.lib.unlocked) {
        // What's typed, and what was wrong with it, stay while the TV changes something else.
        if (!this.asking) this.askPin();
        return;
      }
      this.asking = false;
      const groups = (S.lib.groups || []).filter((g) => !g.custom);
      const lockedChannels = S.lib.channels.filter((c) => c.locked);
      const groupRows = groups.map((group) => h('div', { class: 'row', tabindex: '0', dataset: { id: group.id } },
        h('button', { class: `switch ${group.locked ? 'on' : ''}`, type: 'button', role: 'switch', 'aria-checked': String(group.locked), 'aria-label': group.name, onclick: () => this.lockGroup(group) }),
        h('div', { class: 'what' }, h('div', { class: 'nm', text: group.name }), h('div', { class: 'meta', text: manySources() ? (S.sourceById.get(group.source) || {}).name : '' })),
        group.adult ? h('span', { class: 'tag warn', text: T.adult }) : null,
        h('div', { class: 'count', text: NUMBER.format(S.lib.channels.filter((c) => c.group === group.id).length) })));
      const channelRows = lockedChannels.map((channel) => h('div', { class: 'row', tabindex: '0', dataset: { id: channel.id } },
        h('div', { class: 'num', text: channel.n }), plate(channel),
        h('div', { class: 'what' }, h('div', { class: 'nm', text: channel.name }), h('div', { class: 'meta', text: channelMeta(channel) })),
        h('button', { class: 'btn small', type: 'button', onclick: () => this.lock(channel, false) }, icon('unlock', 15), T.unlock)));
      this.scroller.replaceChildren(
        h('p', { class: 'note', text: T.locksUnlocked }),
        h('section', { class: 'section' },
          h('div', { class: 'title-line' }, h('h2', { text: T.lockedChannels }), h('div', { class: 'grow' }),
            h('button', { class: 'btn small', type: 'button', onclick: () => this.pickChannels() }, icon('lock', 15), T.lockChannels)),
          h('p', { class: 'lead', text: T.lockedChannelsText }),
          ...(channelRows.length ? channelRows : [h('div', { class: 'note', text: T.noLockedChannels })])),
        h('section', { class: 'section' },
          h('div', { class: 'title-line' }, h('h2', { text: T.lockedGroups })),
          h('p', { class: 'lead', text: T.lockedGroupsText }),
          ...groupRows));
    }

    askPin() {
      const input = h('input', { class: 'pin', type: 'password', inputmode: 'numeric', autocomplete: 'off', maxlength: '4', 'aria-label': T.pinLabel });
      const problem = h('div', { class: 'problem', role: 'alert' });
      const button = h('button', { class: 'btn primary', type: 'button' }, T.unlockLocks);
      const send = async () => {
        const pin = input.value.replace(/\D/g, '');
        if (pin.length !== 4) { input.focus(); return; }
        button.disabled = true;
        try {
          await call('POST', 'pin', { pin });
          S.lib.unlocked = true;
          this.update();
          renderNav();
        } catch (error) {
          if (error instanceof Ended) return;
          const json = error.json || {};
          input.value = '';
          problem.textContent = json.tries === 0 ? T.pinTooMany : json.wait ? T.pinBlocked
            : json.left !== undefined ? plural('wrongPin', json.left) : json.off ? T.locksOff : T.saveFailed;
          if (json.off) refresh();
          input.focus();
        } finally {
          button.disabled = false;
        }
      };
      button.addEventListener('click', send);
      input.addEventListener('keydown', (event) => { if (event.key === 'Enter') send(); });
      this.asking = true;
      this.scroller.replaceChildren(h('div', { class: 'card pin-card' }, icon('lock', 28), h('h2', { text: T.pinTitle }), h('p', { text: T.pinText }), input, problem, button));
      requestAnimationFrame(() => input.focus());
    }

    lockGroup(group) {
      group.locked = !group.locked;
      this.update();
      refocus(this.scroller, group.id);
      change('group', { id: group.id, locked: group.locked }, () => refresh());
    }

    lock(channel, locked) {
      channel.locked = locked;
      this.update();
      change('channels', { ids: [channel.id], locked });
    }

    pickChannels() {
      drawer((panel, close) => {
        new ChannelPicker(panel, {
          title: T.lockChannels,
          drawer: true,
          close,
          kind: 'lock',
          icon: (on) => (on ? 'lock' : 'unlock'),
          isOn: (channel) => !!channel.locked,
          onLabel: T.lock,
          offLabel: T.unlock,
          toggle: (channel) => {
            channel.locked = !channel.locked;
            change('channels', { ids: [channel.id], locked: channel.locked });
          },
        });
        panel.onclose = () => this.update();
      });
    }
  }

  // ---- The frame ---------------------------------------------------------------------------------

  function route() {
    const parts = location.hash.replace(/^#/, '').split('/').map(decodeURIComponent);
    switch (parts[0]) {
      case 'channels': return { name: 'channels' };
      case 'numbering': return { name: 'numbering' };
      case 'locks': return { name: 'locks' };
      case 'categories': {
        const source = S.sourceById.get(parts[1]);
        const kind = KIND_NAMES[parts[2]] ? parts[2] : 'live';
        if (source) return { name: 'categories', source: source.id, kind };
        return { name: 'favorites' };
      }
      default: return { name: 'favorites' };
    }
  }

  const linkTo = (r) => (r.name === 'categories' ? `#categories/${encodeURIComponent(r.source)}/${r.kind}` : `#${r.name}`);

  function renderNav() {
    const nav = document.getElementById('nav');
    if (!nav || !S.lib) return;
    const now = route();
    const here = linkTo(now);
    const item = (r, name, count, sub) => h('a', { class: `${sub ? 'sub' : ''} ${linkTo(r) === here ? 'on' : ''}`, href: linkTo(r), 'aria-current': linkTo(r) === here ? 'page' : null,
      onclick: () => document.querySelector('.side').classList.remove('open') }, h('span', { text: name }), count !== null ? h('span', { class: 'count', text: count }) : null);
    const parts = [
      item({ name: 'favorites' }, T.favorites, NUMBER.format(S.lib.favorites.filter((id) => channelOf(id)).length)),
      item({ name: 'channels' }, T.channels, NUMBER.format(S.lib.channels.length)),
      h('div', { class: 'label', text: T.categories }),
    ];
    for (const source of S.lib.sources) {
      parts.push(h('div', { class: 'source' }, h('span', { text: source.name }), source.enabled ? null : h('span', { class: 'tag', text: T.sourceOff })));
      parts.push(item({ name: 'categories', source: source.id, kind: 'live' }, T.liveTv, NUMBER.format(source.channels), true));
      if (source.movies) parts.push(item({ name: 'categories', source: source.id, kind: 'movie' }, T.movies, NUMBER.format(source.movies), true));
      if (source.series) parts.push(item({ name: 'categories', source: source.id, kind: 'series' }, T.series, NUMBER.format(source.series), true));
    }
    parts.push(h('div', { class: 'label', text: '' }));
    parts.push(item({ name: 'numbering' }, T.numbering, null));
    parts.push(item({ name: 'locks' }, T.locks, null));
    nav.replaceChildren(...parts);
  }

  function frame() {
    const app = document.getElementById('app');
    const side = h('nav', { class: 'side', 'aria-label': T.heading }, h('div', { class: 'nav', id: 'nav' }));
    const menu = iconButton('menu', T.heading, () => side.classList.toggle('open'));
    menu.classList.add('menu-button');
    const end = h('button', { class: 'btn', type: 'button', onclick: endSession }, T.end);
    app.replaceChildren(
      h('header', { class: 'top' }, menu, h('div', { class: 'wordmark', text: 'plasma' }), h('div', { class: 'heading', text: T.heading }),
        h('div', { class: 'grow' }), h('div', { class: 'tv', id: 'tv' }, h('i', { class: 'dot ok' }), h('span', { text: tvLine() })),
        h('div', { class: 'sync', id: 'sync' }), end),
      h('div', { class: 'frame' }, side, h('main', { class: 'main', id: 'main' })));
    // On a phone the lists slide in over the page, and a tap beside them puts them away.
    document.addEventListener('pointerdown', (event) => {
      if (side.classList.contains('open') && !side.contains(event.target) && !menu.contains(event.target)) side.classList.remove('open');
    });
  }

  function show() {
    const main = document.getElementById('main');
    if (!main || !S.lib) return;
    document.querySelectorAll('.drawer, .drawer-scrim, .popover').forEach((open) => open.remove());
    const r = route();
    main.replaceChildren();
    S.view = r.name === 'channels' ? new ChannelsView(main)
      : r.name === 'categories' ? new CategoriesView(main, r.source, r.kind)
        : r.name === 'numbering' ? new NumberingView(main)
          : r.name === 'locks' ? new LocksView(main)
            : new FavoritesView(main);
    renderNav();
  }

  async function start() {
    S.categories.clear();
    try {
      const lib = await call('GET', 'library');
      index(lib);
      frame();
      show();
      reachable(true);
    } catch (error) {
      if (!(error instanceof Ended)) joinCard(fmt(T.unreachable, T.tv));
    }
  }

  async function endSession() {
    try { await call('POST', 'end', {}); } catch (error) { /* Ended either way. */ }
    ended('you');
  }

  // ---- Joining, and a session that ended -----------------------------------------------------------

  function ended(reason) {
    session = null;
    keep();
    S.lib = null;
    S.view = null;
    queue.length = 0;
    const why = { idle: T.endedIdle, you: T.endedYou }[reason] || T.endedTv;
    joinCard(null, T.endedTitle, `${why} ${T.endedText}`);
  }

  function joinCard(problemText, title, text) {
    document.querySelectorAll('.drawer, .drawer-scrim, .popover, .scrim').forEach((open) => open.remove());
    const input = h('input', { class: 'code', type: 'text', inputmode: 'text', autocomplete: 'one-time-code', autocapitalize: 'characters', spellcheck: 'false',
      maxlength: '7', 'aria-label': T.codeLabel });
    const problem = h('div', { class: 'problem', role: 'alert', text: problemText || '' });
    const button = h('button', { class: 'btn primary', type: 'button' }, T.join);
    const send = () => join(input.value, problem, button);
    button.addEventListener('click', send);
    input.addEventListener('keydown', (event) => { if (event.key === 'Enter') send(); });
    input.addEventListener('input', () => {
      const clean = input.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6);
      input.value = clean.length > 3 ? `${clean.slice(0, 3)} ${clean.slice(3)}` : clean;
    });
    document.getElementById('app').replaceChildren(h('div', { class: 'center' }, h('div', { class: 'card' },
      h('div', { class: 'wordmark', text: 'plasma' }), h('h1', { text: title || T.heading }),
      h('p', { text: text || fmt(T.joinText, T.tv) }), input, problem, button)));
    input.focus();
  }

  async function join(typed, problem, button) {
    const code = String(typed || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
    if (code.length !== 6) { problem.textContent = T.wrongCode; return; }
    button.disabled = true;
    try {
      const response = await fetch(`${API}join`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, cache: 'no-store', credentials: 'omit',
        body: JSON.stringify({ code, browser: browserName() }),
      });
      const json = await response.json().catch(() => ({}));
      if (!response.ok) {
        problem.textContent = { code: T.wrongCode, tries: T.tooManyTries, closed: T.notOpen, full: T.full }[json.error] || fmt(T.unreachable, T.tv);
        return;
      }
      session = { token: json.token, media: json.media, profile: json.profile };
      keep();
      history.replaceState(null, '', location.pathname + location.hash);
      await start();
    } catch (error) {
      problem.textContent = fmt(T.unreachable, T.tv);
    } finally {
      button.disabled = false;
    }
  }

  // ---- Off we go -------------------------------------------------------------------------------------

  document.title = T.title;
  window.addEventListener('hashchange', () => { if (S.lib) show(); });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) poll(); });
  setInterval(poll, POLL_MS);

  const fromQr = new URLSearchParams(location.search).get('code');
  if (fromQr) {
    session = null;
    keep();
    joinCard();
    const input = document.querySelector('.code');
    input.value = fromQr.toUpperCase();
    join(fromQr, document.querySelector('.problem'), document.querySelector('.card .btn'));
  } else if (session) {
    start();
  } else {
    joinCard();
  }
})();
