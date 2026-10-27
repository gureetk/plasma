// SPDX-License-Identifier: GPL-3.0-or-later
// Plasma's phone remote (brief 4.33 and 5.21): pairs with the TV's code, then sends keys, channels
// and text over a WebSocket, and shows what the TV says back.
(() => {
  'use strict';
  const T = JSON.parse(document.getElementById('texts').textContent);
  const $ = (id) => document.getElementById(id);
  const TOKEN = 'plasma.remote.token';
  const TV_NAME = 'plasma.remote.tv';
  const PING_MS = 15000;
  const TEXT_DEBOUNCE_MS = 150;
  const PAGE = 200;
  const IOS = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

  let ws = null;
  let token = read(TOKEN);
  let tvName = read(TV_NAME);
  let authed = false;
  let missed = 0;
  let retry = 0;
  let awayTimer = 0;
  let tab = 'remote';
  let front = true;
  let lists = [];
  let list = null;
  let channels = [];
  let playing = null;
  let playingId = null;
  let playingAt = 0;
  let field = null;
  let typing = false;
  let textTimer = 0;

  function read(key) { try { return localStorage.getItem(key); } catch (e) { return null; } }
  function write(key, value) { try { if (value == null) localStorage.removeItem(key); else localStorage.setItem(key, value); } catch (e) { /* private browsing */ } }
  function send(message) { if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(message)); }
  // The TV's words have their value as %1$s; a function, so "$" in the value stays as it is.
  function words(text, value) { return (text || '').replace(/%(1\$)?s/, () => value); }

  // ---- Words ----
  document.querySelectorAll('[data-text]').forEach((el) => { el.textContent = T[el.dataset.text] || ''; });
  document.querySelectorAll('[data-label]').forEach((el) => { el.setAttribute('aria-label', T[el.dataset.label] || ''); });
  $('search').placeholder = T.searchChannels || '';
  $('tv-name').textContent = tvName || T.connecting;

  // ---- The connection: hello, then the token or the code ----
  function connect() {
    try {
      ws = new WebSocket('ws://' + location.host + '/remote/ws');
    } catch (e) {
      later();
      return;
    }
    ws.onopen = () => { missed = 0; send({ t: 'hello', client: 'web', app: '1', protocol: 1 }); };
    ws.onmessage = (event) => { let m; try { m = JSON.parse(event.data); } catch (e) { return; } handle(m); };
    ws.onclose = () => {
      authed = false;
      $('tv').classList.remove('on');
      away();
      later();
    };
  }

  // Not reached within a few seconds: the page says so, and keeps trying.
  function away() {
    if (!awayTimer) awayTimer = setTimeout(() => { awayTimer = 0; showAway(); }, 4000);
  }

  function later() {
    ws = null;
    retry = Math.min(retry + 1, 5);
    setTimeout(() => { if (!ws) connect(); }, retry * 1000);
  }

  function showAway() {
    $('away').textContent = tvName ? words(T.unreachable, tvName) : T.unreachableUnnamed;
    $('away').hidden = false;
  }

  function reached() {
    clearTimeout(awayTimer);
    awayTimer = 0;
    retry = 0;
    $('away').hidden = true;
  }

  // Back to the phone after a while: connect at once rather than waiting out the retry.
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && (!ws || ws.readyState > WebSocket.OPEN)) { ws = null; connect(); }
  });

  function handle(m) {
    switch (m.t) {
      case 'welcome':
        reached();
        tvName = m.name;
        write(TV_NAME, tvName);
        $('tv-name').textContent = tvName;
        $('tv').classList.add('on');
        if (token) send({ t: 'auth', token: token }); else showPair();
        break;
      case 'paired':
        token = m.token;
        write(TOKEN, token);
        // The code was good once: it leaves the address, so the bookmark doesn't keep it.
        history.replaceState(null, '', '/remote');
        break;
      case 'error':
        if (m.reason === 'auth') { token = null; write(TOKEN, null); authed = false; showPair(); }
        else if (m.reason === 'tries') warn(T.tooManyTries);
        else if (m.reason === 'code') warn(T.wrongCode);
        break;
      case 'state':
        if (!authed) { authed = true; showRemote(); send({ t: 'lists' }); }
        applyState(m);
        break;
      case 'lists':
        applyLists(m);
        break;
      case 'pong':
        missed = 0;
        break;
    }
  }

  setInterval(() => {
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    // Two pings unanswered: the TV's gone, so connect again.
    if (++missed > 2) { ws.close(); return; }
    send({ t: 'ping' });
  }, PING_MS);

  // ---- Pairing ----
  function showPair() {
    $('pair').hidden = false;
    $('tabs').hidden = true;
    document.querySelectorAll('.tab').forEach((el) => { el.hidden = true; });
    const code = new URLSearchParams(location.search).get('code');
    if (code && !$('code').value) $('code').value = code;
    if (!$('name').value) $('name').value = guessName();
  }

  function guessName() {
    const agent = navigator.userAgent;
    if (/iPad/.test(agent) || (IOS && !/iPhone|iPod/.test(agent))) return 'iPad';
    if (/iPhone|iPod/.test(agent)) return 'iPhone';
    if (/Android/.test(agent)) return T.androidPhone;
    return T.computer;
  }

  function warn(text) { $('pair-warn').textContent = text || ''; }

  function pair() {
    warn('');
    send({ t: 'pair', code: $('code').value.trim(), name: $('name').value.trim() });
  }
  $('pair-button').addEventListener('click', pair);
  $('name').addEventListener('keydown', (event) => { if (event.key === 'Enter') { event.preventDefault(); pair(); } });

  function showRemote() {
    $('pair').hidden = true;
    $('tabs').hidden = false;
    show(tab);
  }

  // ---- Tabs ----
  function show(name) {
    tab = name;
    document.querySelectorAll('.tab').forEach((el) => { el.hidden = el.id !== name; });
    document.querySelectorAll('#tabs button').forEach((el) => el.classList.toggle('on', el.dataset.tab === name));
    if (name === 'type') showField(true);
  }
  document.querySelectorAll('#tabs button').forEach((el) => el.addEventListener('click', () => show(el.dataset.tab)));

  // ---- Keys: down when pressed, up when let go, so long-press OK and a held D-pad repeat on the TV ----
  function buzz() { if (navigator.vibrate) navigator.vibrate(12); }
  function holdable(el, key) {
    let held = false;
    const up = () => {
      if (!held) return;
      held = false;
      el.classList.remove('pressed');
      send({ t: 'key', key: key, action: 'up' });
    };
    el.addEventListener('pointerdown', (event) => {
      if (event.button > 0) return;
      event.preventDefault();
      held = true;
      el.classList.add('pressed');
      buzz();
      send({ t: 'key', key: key, action: 'down' });
    });
    el.addEventListener('pointerup', up);
    el.addEventListener('pointercancel', up);
    el.addEventListener('pointerleave', up);
    el.addEventListener('contextmenu', (event) => event.preventDefault());
    // A key from a keyboard or a screen reader: pressed and let go at once.
    el.addEventListener('click', (event) => {
      if (event.detail !== 0) return;
      send({ t: 'key', key: key, action: 'down' });
      send({ t: 'key', key: key, action: 'up' });
    });
  }
  document.querySelectorAll('[data-key]').forEach((el) => holdable(el, el.dataset.key));
  $('go-live').addEventListener('click', () => { buzz(); send({ t: 'live' }); });

  // Numbers: a 0–9 pad over the remote; the TV collects the digits as from the remote's own keys.
  const pad = $('pad');
  ['1', '2', '3', '4', '5', '6', '7', '8', '9'].forEach(digitKey);
  const close = document.createElement('button');
  close.className = 'flat';
  close.textContent = T.close || '';
  close.addEventListener('click', () => { $('pad-sheet').hidden = true; });
  pad.appendChild(close);
  digitKey('0');
  const ok = document.createElement('button');
  ok.className = 'okkey';
  ok.textContent = T.ok || '';
  holdable(ok, 'ok');
  pad.appendChild(ok);
  function digitKey(digit) {
    const button = document.createElement('button');
    button.textContent = digit;
    button.addEventListener('click', () => { buzz(); send({ t: 'digits', digits: digit }); });
    pad.appendChild(button);
  }
  $('numbers').addEventListener('click', () => { $('pad-sheet').hidden = false; });
  $('pad-sheet').addEventListener('click', (event) => { if (event.target === $('pad-sheet')) $('pad-sheet').hidden = true; });

  // ---- What the TV shows ----
  function applyState(m) {
    front = m.front !== false;
    document.body.classList.toggle('away-from-plasma', !front);
    if (!front) $('pad-sheet').hidden = true;
    playing = m.playing || null;
    playingAt = Date.now();
    showPlaying();
    const keys = m.keys || {};
    ['red', 'green', 'yellow', 'blue'].forEach((key) => { $('key-' + key).textContent = keys[key] || T[key] || ''; });
    const before = field;
    field = m.field || null;
    // A text field got focus on the TV: the Type tab opens by itself.
    if (field && !before) show('type');
    else if (tab === 'type') showField(false);
    const now = playing ? playing.channelId : null;
    if (now !== playingId) { playingId = now; renderRows(); }
  }

  function showPlaying() {
    $('now').hidden = !playing;
    $('play-icon').hidden = !(playing && playing.paused);
    $('pause-icon').hidden = !!(playing && playing.paused);
    if (!playing) return;
    const logo = $('now-logo');
    logo.hidden = false;
    logo.onerror = () => { logo.hidden = true; };
    logo.src = '/remote/logo/' + encodeURIComponent(playing.channelId);
    $('now-channel').textContent = playing.number + ' · ' + playing.name;
    $('now-title').textContent = playing.title || '';
    const live = playing.live;
    const picture = playing.progress;
    $('now-bar').style.width = Math.round((picture != null ? picture : (live || 0)) * 1000) / 10 + '%';
    $('now-live').hidden = live == null;
    if (live != null) $('now-live').style.left = Math.round(live * 1000) / 10 + '%';
    tick();
  }

  // Behind live: paused, it grows by itself until the TV says otherwise.
  function tick() {
    if (!playing) return;
    const behind = (playing.behindLive || 0) + (playing.paused ? Math.floor((Date.now() - playingAt) / 1000) : 0);
    $('now-behind').textContent = behind > 0 ? words(T.behindLive, clock(behind)) : '';
    $('go-live').hidden = behind <= 0;
  }
  setInterval(tick, 1000);

  function clock(seconds) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = String(seconds % 60).padStart(2, '0');
    return h > 0 ? h + ':' + String(m).padStart(2, '0') + ':' + s : m + ':' + s;
  }

  // ---- Type ----
  function showField(opened) {
    const input = $('typed');
    $('icon-search').hidden = !!field && field.kind !== 'search';
    $('icon-keys').hidden = !field || field.kind === 'search';
    if (field) {
      $('type-title').textContent = words(T.typingInto, field.label);
      $('type-text').textContent = T.typeText;
    } else {
      $('type-title').textContent = T.typeTitle;
      $('type-text').textContent = front ? T.nothingToType : T.typingElsewhere;
    }
    // Nothing to type into: no field to type in either.
    document.querySelector('.typed-wrap').hidden = !field;
    document.querySelector('.actions').hidden = !field;
    input.disabled = !field;
    $('paste').hidden = !field || !(navigator.clipboard && navigator.clipboard.readText);
    // iOS opens the keyboard only after a tap.
    input.placeholder = field && IOS ? T.tapToType : '';
    if (!field) input.value = '';
    else if (!typing) input.value = field.text;
    $('results').hidden = !(field && field.results);
    if (field && field.results) $('results-text').textContent = words(T.resultsOnTv, field.results);
    if (opened && field && !IOS) input.focus();
  }

  const typed = $('typed');
  typed.addEventListener('focus', () => { typing = true; });
  typed.addEventListener('blur', () => { typing = false; });
  typed.addEventListener('input', () => {
    clearTimeout(textTimer);
    textTimer = setTimeout(() => send({ t: 'text', text: typed.value }), TEXT_DEBOUNCE_MS);
  });
  typed.addEventListener('keydown', (event) => { if (event.key === 'Enter') { event.preventDefault(); done(); } });
  function done() {
    clearTimeout(textTimer);
    send({ t: 'text', text: typed.value });
    send({ t: 'submit' });
    typed.blur();
  }
  function clear() {
    typed.value = '';
    clearTimeout(textTimer);
    send({ t: 'text', text: '' });
  }
  $('done').addEventListener('click', done);
  $('clear').addEventListener('click', clear);
  $('clear-x').addEventListener('click', () => { clear(); typed.focus(); });
  $('paste').addEventListener('click', () => {
    navigator.clipboard.readText().then((text) => {
      typed.value = (typed.value + text).slice(0, 200);
      send({ t: 'text', text: typed.value });
    }).catch(() => {});
  });

  // ---- Channels ----
  function applyLists(m) {
    lists = m.lists || [];
    if (m.list !== list || m.from === 0) channels = [];
    list = m.list;
    channels = channels.concat(m.channels || []);
    const chips = $('chips');
    chips.textContent = '';
    lists.forEach((each) => {
      const chip = document.createElement('button');
      chip.textContent = each.name;
      chip.classList.toggle('on', each.id === list);
      chip.addEventListener('click', () => {
        if (each.id === list) return;
        channels = [];
        send({ t: 'lists', list: each.id, from: 0 });
      });
      chips.appendChild(chip);
    });
    const on = chips.querySelector('.on');
    if (on && m.from === 0) on.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    renderRows();
    // The rest of a long list, 200 at a time.
    const current = lists.find((each) => each.id === list);
    if (current && channels.length < current.count && (m.channels || []).length === PAGE) send({ t: 'lists', list: list, from: channels.length });
  }

  function renderRows() {
    const current = lists.find((each) => each.id === list);
    $('list-meta').textContent = current ? current.name + ' · ' + current.count : '';
    $('list-numbers').textContent = current && current.countsFromOne ? T.countsFromOne : '';
    const query = $('search').value.trim().toLowerCase();
    const shown = channels.filter((ch) => !query || ch.name.toLowerCase().includes(query) || ch.number === query);
    // The numbers line up, however many digits the list's longest has.
    const digits = channels.reduce((most, ch) => Math.max(most, ch.number.length), 2);
    const rows = $('rows');
    rows.textContent = '';
    shown.forEach((ch) => rows.appendChild(row(ch, digits)));
  }

  function row(ch, digits) {
    const playingHere = playing && playing.channelId === ch.id;
    const el = document.createElement('button');
    el.className = 'row' + (playingHere || ch.playing ? ' playing' : '');
    const num = document.createElement('span');
    num.className = 'num';
    num.style.width = Math.max(22, digits * 10) + 'px';
    num.textContent = ch.number;
    const plate = document.createElement('span');
    plate.className = 'plate';
    const logo = document.createElement('img');
    logo.loading = 'lazy';
    logo.alt = '';
    logo.onerror = () => { logo.remove(); };
    logo.src = '/remote/logo/' + encodeURIComponent(ch.id);
    plate.appendChild(logo);
    const what = document.createElement('span');
    what.className = 'what';
    const name = document.createElement('span');
    name.className = 'name';
    name.textContent = ch.name;
    what.appendChild(name);
    if (ch.now) {
      const on = document.createElement('span');
      on.className = 'on';
      const title = document.createElement('span');
      title.textContent = ch.now;
      on.appendChild(title);
      if (ch.progress != null) {
        const mini = document.createElement('span');
        mini.className = 'mini';
        const fill = document.createElement('b');
        fill.style.width = Math.round(ch.progress * 100) + '%';
        mini.appendChild(fill);
        on.appendChild(mini);
      }
      what.appendChild(on);
    }
    el.append(num, plate, what);
    if (playingHere || ch.playing) el.insertAdjacentHTML('beforeend', '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M11 5 6 9H3v6h3l5 4z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/></svg>');
    el.addEventListener('click', () => { buzz(); send({ t: 'tune', channel: ch.id, list: list }); });
    return el;
  }
  $('search').addEventListener('input', renderRows);

  away();
  connect();
})();
