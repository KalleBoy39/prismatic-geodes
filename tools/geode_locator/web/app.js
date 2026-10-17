'use strict';
// Prismatic Geode Locator UI. Coordinates are Minecraft blocks; the map shows the X/Z plane, north up.

const COLOURS = ['white', 'light_gray', 'gray', 'black', 'brown', 'red', 'orange', 'yellow',
  'lime', 'green', 'cyan', 'light_blue', 'blue', 'purple', 'magenta', 'pink'];
// The approved Prismatic Geodes crystal palettes (purple = vanilla amethyst): [fill, dark, light]
const PAL = {
  white: ['#bbc4d8', '#59616e', '#d5def1'], light_gray: ['#848e9c', '#3e464e', '#aab4c0'],
  gray: ['#5b646e', '#1f252c', '#828b95'], black: ['#4b4a5a', '#090912', '#7c7b8b'],
  brown: ['#905700', '#281300', '#c79052'], red: ['#be3541', '#470113', '#f37375'],
  orange: ['#c3680b', '#592805', '#f5964c'], yellow: ['#aea633', '#655a05', '#d0ca68'],
  lime: ['#64b62d', '#2e5500', '#90dc67'], green: ['#417814', '#132400', '#72ab51'],
  cyan: ['#157682', '#002225', '#3baebf'], light_blue: ['#75b3ec', '#34658b', '#a5d2fe'],
  blue: ['#4c63d3', '#021661', '#819bfe'], purple: ['#9a5cc6', '#361c6a', '#c98ff3'],
  magenta: ['#b34c9e', '#4d054a', '#e583ce'], pink: ['#cf7997', '#713851', '#f6a3be'],
};
const NICE = c => c.replace('_', ' ').replace(/^./, m => m.toUpperCase());
const PURPLE = COLOURS.indexOf('purple');
const MAX_PREDICT = 1_500_000;
const TILE = 128, MAX_LEVEL = 5;
// one geode attempt per 24 chunks on average (vanilla rarity), split evenly over 16 colours
const BLOCKS2_PER_GEODE = 24 * 256;

const $ = id => document.getElementById(id);
const canvas = $('map'), ctx = canvas.getContext('2d');
const state = {
  view: { x: 0, z: 0, s: 0.5 },           // centre in blocks, pixels per block
  seed: null, seedText: '',
  attempts: [], loaded: null,             // [{x,y,z,c,cx,cz}], {seed, x0,z0,x1,z1}
  verified: new Map(), unexplained: [],
  filters: new Set(COLOURS.map((_, i) => i)),
  info: null, maxVerify: 64, drawn: [], popup: null, hover: null,
  terrainReady: false,
};
// hooks for optional extensions (the private dev build adds one; the public build has none)
const hooks = { underlay: [], attempt: [], overlay: [], popupNote: [], seedChanged: [] };

// ---------- API ----------
async function api(path, opts) {
  const r = await fetch(path, opts);
  const j = await r.json();
  if (!r.ok || j.error) throw new Error(j.error || r.statusText);
  return j;
}

// ---------- view helpers ----------
function size() { return { w: canvas.clientWidth, h: canvas.clientHeight }; }
function toScreen(x, z) {
  const { w, h } = size(), v = state.view;
  return [(x - v.x) * v.s + w / 2, (z - v.z) * v.s + h / 2];
}
function toWorld(px, py) {
  const { w, h } = size(), v = state.view;
  return [(px - w / 2) / v.s + v.x, (py - h / 2) / v.s + v.z];
}
function visibleChunks() {
  const { w, h } = size();
  const [ax, az] = toWorld(0, 0), [bx, bz] = toWorld(w, h);
  return { x0: Math.floor(ax / 16), z0: Math.floor(az / 16), x1: Math.floor(bx / 16), z1: Math.floor(bz / 16) };
}
const rectCount = r => (r.x1 - r.x0 + 1) * (r.z1 - r.z0 + 1);
const key = (cx, cz) => cx + ',' + cz;

// ---------- crystal icons ----------
// Three shards of an amethyst cluster, in a unit box (base at the bottom): [topX, topY, baseX, baseY, halfWidth]
const SHARDS = [[0.17, 0.36, 0.36, 0.97, 0.13], [0.85, 0.42, 0.64, 0.97, 0.12], [0.5, 0.03, 0.5, 0.98, 0.17]];
function shard(s, ox, oy, size) {
  const [tx, ty, bx, by, hw] = s.map(v => v * size);
  const X = v => ox + v, Y = v => oy + v;
  const dx = bx - tx, dy = by - ty, len = Math.hypot(dx, dy), px = -dy / len, py = dx / len;
  const sx = tx + dx * 0.24, sy = ty + dy * 0.24;
  return {
    T: [X(tx), Y(ty)], S: [X(sx), Y(sy)], B: [X(bx), Y(by)],
    SL: [X(sx + px * hw), Y(sy + py * hw)], SR: [X(sx - px * hw), Y(sy - py * hw)],
    BL: [X(bx + px * hw * 0.8), Y(by + py * hw * 0.8)], BR: [X(bx - px * hw * 0.8), Y(by - py * hw * 0.8)],
  };
}
function poly(c, pts) { c.beginPath(); c.moveTo(...pts[0]); for (let i = 1; i < pts.length; i++) c.lineTo(...pts[i]); c.closePath(); }

/** Draws a crystal cluster centred on (x, y). kind: predicted | confirmed | aborted. */
function drawCrystal(c, x, y, size, colour, kind) {
  const [fill, dark, light] = PAL[COLOURS[colour]];
  const ox = x - size / 2, oy = y - size / 2;
  const shards = SHARDS.map(s => shard(s, ox, oy, size));
  c.save();
  if (kind === 'aborted') c.globalAlpha *= 0.5;
  c.lineJoin = 'round';
  // soft outer outline so the icon reads on light land, dark land and water alike
  for (const p of shards) {
    poly(c, [p.T, p.SL, p.BL, p.BR, p.SR]);
    c.lineWidth = Math.max(2.4, size * 0.16); c.strokeStyle = 'rgba(10,10,14,.75)'; c.stroke();
  }
  for (const p of shards) {
    poly(c, [p.T, p.SL, p.BL, p.B, p.S]); c.fillStyle = light; c.fill();
    poly(c, [p.T, p.S, p.B, p.BR, p.SR]); c.fillStyle = fill; c.fill();
    poly(c, [p.T, p.SL, p.BL, p.BR, p.SR]);
    c.lineWidth = Math.max(1, size * 0.05); c.strokeStyle = dark; c.stroke();
  }
  c.restore();
  if (kind === 'confirmed' || kind === 'aborted') {
    const r = Math.max(3.2, size * 0.2), bx = x + size * 0.36, by = y + size * 0.34;
    c.save();
    c.beginPath(); c.arc(bx, by, r + 1, 0, Math.PI * 2); c.fillStyle = 'rgba(10,10,14,.8)'; c.fill();
    c.beginPath(); c.arc(bx, by, r, 0, Math.PI * 2); c.fillStyle = kind === 'confirmed' ? '#3fbf6a' : '#6b7079'; c.fill();
    c.strokeStyle = '#fff'; c.lineWidth = Math.max(1.2, r * 0.32); c.lineCap = 'round'; c.beginPath();
    if (kind === 'confirmed') { c.moveTo(bx - r * .5, by); c.lineTo(bx - r * .1, by + r * .4); c.lineTo(bx + r * .5, by - r * .4); }
    else { const k = r * .42; c.moveTo(bx - k, by - k); c.lineTo(bx + k, by + k); c.moveTo(bx + k, by - k); c.lineTo(bx - k, by + k); }
    c.stroke(); c.restore();
  }
}

// icons are pre-rendered per colour/kind/size so a screen full of markers stays fast
const sprites = new Map();
function sprite(colour, kind, size) {
  const dpr = window.devicePixelRatio || 1, k = colour + kind + size + '@' + dpr;
  let s = sprites.get(k);
  if (!s) {
    const pad = Math.ceil(size * 0.6), n = Math.ceil((size + pad * 2) * dpr);
    s = document.createElement('canvas'); s.width = n; s.height = n;
    const c = s.getContext('2d'); c.scale(dpr, dpr);
    drawCrystal(c, size / 2 + pad, size / 2 + pad, size, colour, kind);
    s.css = size + pad * 2;
    sprites.set(k, s);
  }
  return s;
}
function drawDot(c, x, y, r, colour, kind) {
  const [fill, dark] = PAL[COLOURS[colour]];
  c.save();
  if (kind === 'aborted') c.globalAlpha = 0.45;
  c.beginPath(); c.moveTo(x, y - r - 1.5); c.lineTo(x + r + 1.5, y); c.lineTo(x, y + r + 1.5); c.lineTo(x - r - 1.5, y); c.closePath();
  c.fillStyle = 'rgba(10,10,14,.8)'; c.fill();
  c.beginPath(); c.moveTo(x, y - r); c.lineTo(x + r, y); c.lineTo(x, y + r); c.lineTo(x - r, y); c.closePath();
  c.fillStyle = fill; c.fill(); c.lineWidth = 1; c.strokeStyle = kind === 'confirmed' ? '#ffffff' : dark; c.stroke();
  c.restore();
}

// ---------- terrain tiles ----------
const tiles = new Map();   // key -> {img, ok, at}
let tileQueue = [], tileBusy = 0;
function tileLevel() { return Math.max(0, Math.min(MAX_LEVEL, Math.floor(Math.log2(1 / state.view.s) + 0.35))); }
function tileKey(L, tx, tz) { return state.seed + '/' + L + '/' + tx + '/' + tz; }
function requestTile(L, tx, tz) {
  const k = tileKey(L, tx, tz);
  if (tiles.has(k)) return tiles.get(k);
  const t = { L, tx, tz, k, ok: false, img: null, queued: true };
  tiles.set(k, t);
  tileQueue.push(t);
  return t;
}
function pumpTiles() {
  const L = tileLevel(), span = TILE << L, { w, h } = size();
  const [cx, cz] = toWorld(w / 2, h / 2);
  // drop queued tiles that left the view; nearest to the centre first
  const vis = visibleTileRange(L);
  tileQueue = tileQueue.filter(t => {
    const keep = t.L === L && t.tx >= vis.x0 && t.tx <= vis.x1 && t.tz >= vis.z0 && t.tz <= vis.z1 && t.k.startsWith(state.seed + '/');
    if (!keep) tiles.delete(t.k);
    return keep;
  });
  tileQueue.sort((a, b) => Math.hypot((a.tx + .5) * span - cx, (a.tz + .5) * span - cz) - Math.hypot((b.tx + .5) * span - cx, (b.tz + .5) * span - cz));
  while (tileBusy < 6 && tileQueue.length) {
    const t = tileQueue.shift(); t.queued = false; tileBusy++;
    const img = new Image();
    img.onload = () => { t.img = img; t.ok = true; tileBusy--; redraw(); pumpTiles(); };
    img.onerror = () => { tiles.delete(t.k); tileBusy--; setTimeout(() => { redraw(); }, 1500); pumpTiles(); };
    img.src = `/api/tile?seed=${state.seed}&l=${t.L}&x=${t.tx}&z=${t.tz}`;
  }
  if (tiles.size > 900) { // forget the oldest finished tiles
    for (const [k, t] of tiles) { if (tiles.size <= 700) break; if (t.ok) tiles.delete(k); }
  }
}
function visibleTileRange(L) {
  const span = TILE << L, { w, h } = size();
  const [ax, az] = toWorld(0, 0), [bx, bz] = toWorld(w, h);
  return { x0: Math.floor(ax / span), z0: Math.floor(az / span), x1: Math.floor(bx / span), z1: Math.floor(bz / span) };
}
function drawTerrain() {
  const L = tileLevel(), span = TILE << L, vis = visibleTileRange(L), s = state.view.s;
  ctx.imageSmoothingEnabled = s < 1 / (1 << L) * 0.99;
  for (let tx = vis.x0; tx <= vis.x1; tx++) {
    for (let tz = vis.z0; tz <= vis.z1; tz++) {
      const t = requestTile(L, tx, tz);
      const [sx, sy] = toScreen(tx * span, tz * span);
      const px = span * s;
      if (t.ok) { ctx.drawImage(t.img, Math.floor(sx), Math.floor(sy), Math.ceil(px + 1), Math.ceil(px + 1)); continue; }
      // until it arrives, stretch a coarser tile that is already loaded
      for (let up = 1; L + up <= MAX_LEVEL; up++) {
        const pL = L + up, pt = tiles.get(tileKey(pL, Math.floor(tx / (1 << up)), Math.floor(tz / (1 << up))));
        if (!pt || !pt.ok) continue;
        const sub = TILE >> up, ix = ((tx % (1 << up)) + (1 << up)) % (1 << up), iz = ((tz % (1 << up)) + (1 << up)) % (1 << up);
        ctx.drawImage(pt.img, ix * sub, iz * sub, sub, sub, Math.floor(sx), Math.floor(sy), Math.ceil(px + 1), Math.ceil(px + 1));
        break;
      }
    }
  }
  pumpTiles();
}

// ---------- drawing ----------
let frame = 0;
function redraw() {
  if (frame) return;
  // animation frame normally; a timer as fallback where frames are throttled (hidden tabs, headless capture)
  const run = () => { if (!frame) return; frame = 0; clearTimeout(fallback); draw(); };
  frame = requestAnimationFrame(run);
  const fallback = setTimeout(run, 250);
}

function iconSize() { return Math.round(Math.max(14, Math.min(28, 18 + 5 * Math.log2(state.view.s * 2)))); }
/** How markers can be shown at this zoom: 'icons', 'dots' or 'none' (too dense to be useful). */
function markerMode() {
  const k = Math.max(1, state.filters.size);
  const spacing = Math.sqrt(BLOCKS2_PER_GEODE * 16 / k) * state.view.s;
  if (spacing >= iconSize() * 2.6) return 'icons';
  if (spacing >= 9) return 'dots';
  return 'none';
}

function draw() {
  const dpr = window.devicePixelRatio || 1;
  const { w, h } = size();
  if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
    canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
  }
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.fillStyle = '#20232a'; ctx.fillRect(0, 0, w, h);
  const v = state.view, vis = visibleChunks(), cpx = 16 * v.s;
  if (state.seed && state.terrainReady && $('lTerrain').checked) drawTerrain();
  ctx.imageSmoothingEnabled = true;
  for (const f of hooks.underlay) f(ctx);

  // verified area: a light veil
  if (state.verified.size && cpx >= 1.5) {
    ctx.fillStyle = 'rgba(255,255,255,.16)';
    for (const [k] of state.verified) {
      const [cx, cz] = k.split(',').map(Number);
      if (cx < vis.x0 || cx > vis.x1 || cz < vis.z0 || cz > vis.z1) continue;
      // whole-pixel edges so neighbouring chunks neither overlap nor leave gaps
      const [ax, ay] = toScreen(cx * 16, cz * 16), [bx, by] = toScreen(cx * 16 + 16, cz * 16 + 16);
      ctx.fillRect(Math.round(ax), Math.round(ay), Math.round(bx) - Math.round(ax), Math.round(by) - Math.round(ay));
    }
  }
  // grid
  if ($('lGrid').checked) {
    const lines = (step, style, minPx) => {
      if (step * v.s < minPx) return;
      ctx.strokeStyle = style; ctx.lineWidth = 1; ctx.beginPath();
      const [ax, az] = toWorld(0, 0), [bx, bz] = toWorld(w, h);
      for (let x = Math.floor(ax / step) * step; x <= bx; x += step) { const sx = Math.round(toScreen(x, 0)[0]) + .5; ctx.moveTo(sx, 0); ctx.lineTo(sx, h); }
      for (let z = Math.floor(az / step) * step; z <= bz; z += step) { const sy = Math.round(toScreen(0, z)[1]) + .5; ctx.moveTo(0, sy); ctx.lineTo(w, sy); }
      ctx.stroke();
    };
    lines(16, 'rgba(0,0,0,.22)', 6);
    lines(512, 'rgba(0,0,0,.45)', 10);
  }

  // markers
  const mode = markerMode(), size2 = iconSize(), drawn = [];
  const showAborted = $('lAborted').checked;
  if (state.seed && mode !== 'none') {
    const list = [];
    for (const a of state.attempts) {
      if (!state.filters.has(a.c)) continue;
      const [sx, sy] = toScreen(a.x + .5, a.z + .5);
      if (sx < -30 || sy < -30 || sx > w + 30 || sy > h + 30) continue;
      const ver = state.verified.get(key(a.cx, a.cz));
      const kind = ver && ver[0] === 1 ? 'confirmed' : ver && ver[0] === 2 ? 'aborted' : 'predicted';
      if (kind === 'aborted' && !showAborted) continue;
      let skip = false, faded = false;
      for (const f of hooks.attempt) { const r = f(a, kind); if (r) { skip ||= r.skip; faded ||= r.faded; } }
      if (skip) continue;
      list.push({ sx, sy, a, kind, faded });
    }
    // aborted underneath, confirmed on top
    const order = { aborted: 0, predicted: 1, confirmed: 2 };
    list.sort((p, q) => order[p.kind] - order[q.kind] || p.sy - q.sy);
    for (const m of list) {
      ctx.save(); if (m.faded) ctx.globalAlpha = 0.4;
      if (mode === 'icons') {
        const sp = sprite(m.a.c, m.kind, size2);
        ctx.drawImage(sp, m.sx - sp.css / 2, m.sy - sp.css / 2, sp.css, sp.css);
      } else drawDot(ctx, m.sx, m.sy, 3, m.a.c, m.kind);
      ctx.restore();
      drawn.push({ sx: m.sx, sy: m.sy, r: mode === 'icons' ? size2 * 0.55 : 6, type: 'attempt', a: m.a, kind: m.kind, x: m.a.x, z: m.a.z });
    }
  }
  for (const f of hooks.overlay) f(ctx, drawn, mode, size2);
  state.drawn = drawn;
  if (state.popup) placePopup();
  updateNotice(mode);
  updateStats(vis);
}

function updateNotice(mode) {
  const n = $('notice');
  let t = '';
  if (!state.seed) t = '';
  else if (rectCount(visibleChunks()) > MAX_PREDICT || mode === 'none') t = 'Zoom in to see geodes';
  else if (!state.terrainReady && $('lTerrain').checked) t = state.terrainStatus || 'Preparing the terrain map…';
  n.textContent = t; n.style.display = t ? 'block' : 'none';
  $('welcome').hidden = !!state.seed;
}

// ---------- data loading ----------
let loadTimer = 0, loadSeq = 0;
function scheduleLoad() { clearTimeout(loadTimer); loadTimer = setTimeout(load, 120); saveHash(); }

async function load() {
  if (!state.seed) { redraw(); return; }
  const vis = visibleChunks();
  if (rectCount(vis) > MAX_PREDICT || markerMode() === 'none') { redraw(); return; }
  const L = state.loaded;
  if (L && L.seed === state.seed && vis.x0 >= L.x0 && vis.z0 >= L.z0 && vis.x1 <= L.x1 && vis.z1 <= L.z1) { redraw(); return; }
  // load the visible area plus a margin, within the backend limit
  let pw = Math.ceil((vis.x1 - vis.x0 + 1) / 2), ph = Math.ceil((vis.z1 - vis.z0 + 1) / 2);
  while ((vis.x1 - vis.x0 + 1 + 2 * pw) * (vis.z1 - vis.z0 + 1 + 2 * ph) > MAX_PREDICT && (pw > 0 || ph > 0)) { pw >>= 1; ph >>= 1; }
  const R = { seed: state.seed, x0: vis.x0 - pw, z0: vis.z0 - ph, x1: vis.x1 + pw, z1: vis.z1 + ph };
  const seq = ++loadSeq;
  const qs = `seed=${R.seed}&x0=${R.x0}&z0=${R.z0}&x1=${R.x1}&z1=${R.z1}`;
  try {
    const [p, ver] = await Promise.all([api('/api/predict?' + qs), api('/api/verified?' + qs)]);
    if (seq !== loadSeq) return;
    state.attempts = p.attempts.map(([x, y, z, c]) => ({ x, y, z, c, cx: x >> 4, cz: z >> 4 }));
    setVerified(ver);
    state.loaded = R;
  } catch (e) { $('notice').textContent = e.message; $('notice').style.display = 'block'; }
  redraw();
}

function setVerified(ver) {
  for (const [cx, cz, s, n] of ver.chunks) state.verified.set(key(cx, cz), [s, n]);
  state.unexplained = ver.unexplained;
}

async function refreshVerified() {
  if (!state.loaded) return;
  const R = state.loaded;
  setVerified(await api(`/api/verified?seed=${R.seed}&x0=${R.x0}&z0=${R.z0}&x1=${R.x1}&z1=${R.z1}`));
  redraw();
}

async function setSeed(text, keepView) {
  try {
    const r = await api('/api/seed?text=' + encodeURIComponent(text));
    if (state.seed !== r.seed) { state.verified = new Map(); state.loaded = null; state.attempts = []; tileQueue = []; }
    state.seed = r.seed; state.seedText = r.text;
    $('seed').value = r.text;
    $('seedHint').textContent = r.text === r.seed ? 'Seed loaded.' : `Text seed → numeric seed ${r.seed}`;
    try { localStorage.setItem('geodeSeed', r.text); } catch (e) { /* storage unavailable */ }
    closePopup(); scheduleLoad(); updateVerifyButton();
    for (const f of hooks.seedChanged) f(state.seed);
  } catch (e) { $('seedHint').textContent = e.message; }
}

// ---------- verify ----------
function updateVerifyButton() {
  const vis = visibleChunks(), b = $('verify');
  const wC = vis.x1 - vis.x0 + 1, hC = vis.z1 - vis.z0 + 1;
  if (verifying) return;
  if (!state.seed) { b.disabled = true; b.textContent = 'Verify visible area'; return; }
  const tooBig = wC > state.maxVerify || hC > state.maxVerify;
  b.disabled = tooBig;
  b.textContent = tooBig ? 'Zoom in to verify' : `Verify visible area (${wC} × ${hC} chunks)`;
  const m = $('verifyMsg');
  if (!m.dataset.busy && !m.dataset.keep) m.textContent = tooBig ? `Verify works on up to ${state.maxVerify} × ${state.maxVerify} chunks at a time.` : '';
}

let verifying = false;
async function verify() {
  const vis = visibleChunks(), m = $('verifyMsg'), bar = $('verifyBar');
  verifying = true; $('verify').disabled = true; m.dataset.busy = '1'; delete m.dataset.keep; m.classList.remove('err');
  m.textContent = 'Starting the test world. The first time takes a few minutes.';
  bar.hidden = false; bar.firstChild.style.width = '2%';
  try {
    await api(`/api/verify?seed=${state.seed}&x0=${vis.x0}&z0=${vis.z0}&x1=${vis.x1}&z1=${vis.z1}`, { method: 'POST' });
    for (;;) {
      await new Promise(r => setTimeout(r, 1000));
      const s = await api('/api/verify/status');
      m.textContent = friendly(s.message);
      if (s.total) bar.firstChild.style.width = Math.max(4, Math.round(100 * s.done / s.total)) + '%';
      if (!s.running) { if (s.error) throw new Error(s.error); break; }
    }
    state.loaded = null; // predictions reload in case the probe updated the feature index
    await load(); await refreshVerified();
    m.textContent = 'Done. Confirmed geodes have a green tick.';
  } catch (e) { m.textContent = 'Verify failed: ' + e.message; m.classList.add('err'); }
  m.dataset.keep = '1';
  bar.hidden = true; verifying = false; delete m.dataset.busy; updateVerifyButton();
}
function friendly(msg) {
  if (!msg) return '';
  if (msg.startsWith('starting shadow server')) return 'Starting the test world…';
  if (msg === 'shadow server ready') return 'Test world ready, generating terrain…';
  if (msg.startsWith('generated')) return 'Generating: ' + msg.replace('generated ', '');
  if (msg.startsWith('scanning')) return 'Reading the generated chunks…';
  if (msg.startsWith('area already verified')) return 'This area is already verified.';
  return msg;
}

// ---------- stats + filters ----------
function updateStats(vis) {
  const per = new Array(16).fill(0); let pred = 0, conf = 0, ab = 0;
  for (const a of state.attempts) {
    if (a.cx < vis.x0 || a.cx > vis.x1 || a.cz < vis.z0 || a.cz > vis.z1) continue;
    const s = state.verified.get(key(a.cx, a.cz));
    if (s && s[0] === 2) { ab++; continue; }
    per[a.c]++;
    if (s && s[0] === 1) conf++; else pred++;
  }
  document.querySelectorAll('.chip').forEach(ch => { ch.querySelector('.n').textContent = per[ch.dataset.c] || ''; });
  let t = state.seed && state.loaded ? `In view: ${pred + conf} geodes (${conf} confirmed, ${pred} not checked yet)` + (ab ? `, ${ab} spots stopped by terrain.` : '.') : '';
  if (state.unexplained.length) t += ` <span style="color:var(--warn)">${state.unexplained.length} verified geodes do not match a prediction. Please report this.</span>`;
  $('stats').innerHTML = t;
  updateVerifyButton();
}

function iconCanvas(cv, colour, kind, px) {
  const dpr = window.devicePixelRatio || 1; cv.width = px * dpr; cv.height = px * dpr;
  const c = cv.getContext('2d'); c.scale(dpr, dpr);
  drawCrystal(c, px / 2, px / 2 - px * 0.04, px * 0.78, colour, kind);
}
function buildFilters() {
  const f = $('filters');
  COLOURS.forEach((c, i) => {
    const el = document.createElement('div');
    el.className = 'chip'; el.dataset.c = i; el.title = NICE(c) + (i === PURPLE ? ' (vanilla amethyst)' : '');
    const cv = document.createElement('canvas'); iconCanvas(cv, i, 'predicted', 16);
    el.append(cv, document.createTextNode(NICE(c)));
    const n = document.createElement('span'); n.className = 'n'; el.append(n);
    el.onclick = () => { state.filters.has(i) ? state.filters.delete(i) : state.filters.add(i); syncFilters(); };
    f.appendChild(el);
  });
  $('allOn').onclick = () => { COLOURS.forEach((_, i) => state.filters.add(i)); syncFilters(); };
  $('allOff').onclick = () => { state.filters.clear(); syncFilters(); };
}
function syncFilters() {
  document.querySelectorAll('.chip').forEach(ch => ch.classList.toggle('off', !state.filters.has(+ch.dataset.c)));
  try { localStorage.setItem('geodeFilters', JSON.stringify([...state.filters])); } catch (e) { /* storage unavailable */ }
  redraw(); scheduleLoad();
}

function drawLegend() {
  document.querySelectorAll('#legend canvas').forEach(cv => {
    const k = cv.dataset.k, red = COLOURS.indexOf('red');
    if (k === 'verChunk') {
      const dpr = window.devicePixelRatio || 1; cv.width = 24 * dpr; cv.height = 24 * dpr;
      const c = cv.getContext('2d'); c.scale(dpr, dpr);
      c.fillStyle = '#8db360'; c.fillRect(3, 3, 18, 18); c.fillStyle = 'rgba(255,255,255,.3)'; c.fillRect(3, 3, 18, 18);
      return;
    }
    iconCanvas(cv, red, k, 24);
  });
  const lg = $('logo'), dpr = window.devicePixelRatio || 1; lg.width = 34 * dpr; lg.height = 34 * dpr;
  const c = lg.getContext('2d'); c.scale(dpr, dpr); drawCrystal(c, 17, 17, 28, PURPLE, 'predicted');
}

// ---------- popup + hover ----------
function closePopup() { state.popup = null; $('popup').hidden = true; }
function placePopup() {
  const p = $('popup'), m = state.popup;
  const [sx, sy] = toScreen(m.x + .5, m.z + .5), { w, h } = size();
  let left = sx + 18, top = sy - 24;
  if (left + p.offsetWidth > w - 8) left = sx - 18 - p.offsetWidth;
  if (top + p.offsetHeight > h - 8) top = h - 8 - p.offsetHeight;
  p.style.left = Math.max(8, left) + 'px'; p.style.top = Math.max(8, top) + 'px';
}
const STATUS = {
  predicted: ['', 'Predicted from the seed. Not checked against the terrain yet; use Verify to be sure.'],
  confirmed: ['ok', '<b>Verified in newly generated terrain</b><br>Confirms that the predicted geode forms when this area is generated with Prismatic Geodes installed. Chunks generated before the mod was installed keep their original vanilla purple geodes.'],
  aborted: ['ab', 'Stopped by terrain: Verify found no geode here (a cave, water or open air was in the way).'],
};
function copyButton(label, text) {
  const b = document.createElement('button'); b.textContent = label;
  b.onclick = async () => {
    try { await navigator.clipboard.writeText(text); b.textContent = 'Copied'; } catch (e) { b.textContent = text; }
    setTimeout(() => { b.textContent = label; }, 1500);
  };
  return b;
}
function openPopup(hit) {
  const p = $('popup');
  if (hit.type !== 'attempt') { if (hit.popup) { hit.popup(p); state.popup = { x: hit.x, z: hit.z }; p.hidden = false; placePopup(); } return; }
  const a = hit.a, c = COLOURS[a.c], [cls, text] = STATUS[hit.kind];
  const row = (k, v) => `<tr><td>${k}</td><td>${v}</td></tr>`;
  const cxp = a.x + 5, cyp = a.y + 5, czp = a.z + 5;
  p.innerHTML = `<button class="close" title="Close">×</button>
    <h3><canvas></canvas>${NICE(c)} geode</h3>
    <div class="status ${cls}">${text}</div>
    <table>
      ${row('Origin', `<span class="big mono">${a.x} ${a.y} ${a.z}</span>`)}
      ${row('Centre ≈', `<span class="mono">${cxp} ${cyp} ${czp}</span>`)}
      ${row('Chunk', `<span class="mono">${a.cx}, ${a.cz}</span>`)}
      ${row('Colour', NICE(c) + (a.c === PURPLE ? ' (vanilla amethyst)' : ''))}
    </table>
    <div class="actions"></div>
    <div class="note">The origin is the exact point Minecraft uses for the geode. The hollow centre is usually about 5 blocks further in X, Y and Z.</div>`;
  for (const f of hooks.popupNote) { const n = f(a, hit.kind); if (n) p.insertAdjacentHTML('beforeend', `<div class="note warn">${n}</div>`); }
  iconCanvas(p.querySelector('h3 canvas'), a.c, hit.kind, 24);
  const act = p.querySelector('.actions');
  act.append(copyButton('Copy coordinates', `${a.x} ${a.y} ${a.z}`), copyButton('Copy /tp command', `/tp @s ${cxp} ${cyp} ${czp}`));
  p.querySelector('.close').onclick = closePopup;
  state.popup = { x: a.x, z: a.z };
  p.hidden = false;
  placePopup();
}
function hitAt(px, py) {
  let best = null, bd = Infinity;
  for (const d of state.drawn) {
    const dist = Math.hypot(d.sx - px, d.sy - py);
    if (dist <= d.r + 3 && dist < bd) { bd = dist; best = d; }
  }
  return best;
}
function showTip(hit, px, py) {
  const t = $('tip');
  if (!hit) { t.hidden = true; canvas.classList.remove('hover'); return; }
  canvas.classList.add('hover');
  t.textContent = hit.tip || (hit.type === 'attempt'
    ? `${NICE(COLOURS[hit.a.c])} · ${hit.a.x} ${hit.a.y} ${hit.a.z} · ${hit.kind === 'predicted' ? 'predicted' : hit.kind === 'confirmed' ? 'Confirmed in fresh generation — existing old chunks may differ.' : 'stopped by terrain'}`
    : '');
  t.hidden = !t.textContent;
  t.style.left = (px + 14) + 'px'; t.style.top = (py + 14) + 'px';
}

// cursor read-out: coordinates now, biome + height once the cursor rests
let probeTimer = 0, probeKey = '', probeText = '';
function updateCoords(wx, wz) {
  const bx = Math.floor(wx), bz = Math.floor(wz);
  const base = `X ${bx}  Z ${bz}   chunk ${bx >> 4}, ${bz >> 4}`;
  const k = state.seed + ':' + (bx >> 2) + ',' + (bz >> 2);
  $('coords').textContent = base + (k === probeKey && probeText ? '   ' + probeText : '');
  clearTimeout(probeTimer);
  if (!state.seed || !state.terrainReady || k === probeKey) return;
  probeTimer = setTimeout(async () => {
    try {
      const r = await api(`/api/probe?seed=${state.seed}&x=${bx}&z=${bz}`);
      if (!r.biome) return;
      probeKey = k;
      probeText = NICE(r.biome.replace(/^minecraft:/, '').replace(/_/g, ' ')) + (r.height < 63 ? ` · water, floor ≈ Y ${r.height}` : ` · ground ≈ Y ${r.height}`);
      $('coords').textContent = base + '   ' + probeText;
    } catch (e) { /* ignore */ }
  }, 160);
}

// ---------- input ----------
let drag = null;
canvas.addEventListener('pointerdown', e => {
  drag = { x: e.clientX, y: e.clientY, vx: state.view.x, vz: state.view.z, moved: false };
  canvas.setPointerCapture(e.pointerId);
});
canvas.addEventListener('pointermove', e => {
  const rect = canvas.getBoundingClientRect(), px = e.clientX - rect.left, py = e.clientY - rect.top;
  const [wx, wz] = toWorld(px, py);
  updateCoords(wx, wz);
  if (!drag) { showTip(hitAt(px, py), px, py); return; }
  const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
  if (Math.abs(dx) + Math.abs(dy) > 3) { drag.moved = true; canvas.classList.add('drag'); showTip(null); }
  state.view.x = drag.vx - dx / state.view.s; state.view.z = drag.vz - dy / state.view.s;
  redraw(); scheduleLoad();
});
canvas.addEventListener('pointerleave', () => showTip(null));
canvas.addEventListener('pointerup', e => {
  canvas.classList.remove('drag');
  const wasDrag = drag && drag.moved; drag = null;
  if (wasDrag) return;
  const rect = canvas.getBoundingClientRect();
  const best = hitAt(e.clientX - rect.left, e.clientY - rect.top);
  if (best) openPopup(best); else closePopup();
});
function zoomAt(px, py, f) {
  const [wx, wz] = toWorld(px, py);
  state.view.s = Math.min(8, Math.max(1 / 32, state.view.s * f));
  const [nx, nz] = toWorld(px, py);
  state.view.x += wx - nx; state.view.z += wz - nz;
  redraw(); scheduleLoad();
}
canvas.addEventListener('wheel', e => {
  e.preventDefault();
  const rect = canvas.getBoundingClientRect();
  zoomAt(e.clientX - rect.left, e.clientY - rect.top, Math.exp(-e.deltaY * 0.0015));
}, { passive: false });
$('zin').onclick = () => { const { w, h } = size(); zoomAt(w / 2, h / 2, 2); };
$('zout').onclick = () => { const { w, h } = size(); zoomAt(w / 2, h / 2, 0.5); };
window.addEventListener('resize', () => { redraw(); scheduleLoad(); });

function goTo() {
  const x = parseFloat($('gx').value), z = parseFloat($('gz').value);
  if (!isFinite(x) || !isFinite(z)) return;
  state.view.x = x; state.view.z = z; state.view.s = Math.max(state.view.s, 1);
  redraw(); scheduleLoad();
}

function saveHash() {
  const v = state.view;
  const h = `#seed=${encodeURIComponent(state.seedText || '')}&x=${Math.round(v.x)}&z=${Math.round(v.z)}&zoom=${v.s.toFixed(4)}`;
  history.replaceState(null, '', h);
}

async function pollTerrain() {
  try {
    const t = await api('/api/terrain');
    state.terrainStatus = t.ready ? '' : 'Preparing the terrain map… (' + t.status + ')';
    if (t.ready) { state.terrainReady = true; redraw(); return; }
    if (t.status.startsWith('terrain map unavailable')) { state.terrainStatus = 'The terrain map could not start. Geodes still work.'; redraw(); return; }
  } catch (e) { /* backend restarting */ }
  redraw();
  setTimeout(pollTerrain, 1500);
}

// ---------- init ----------
async function init() {
  // read the link first: syncFilters below rewrites the hash
  const h = new URLSearchParams(location.hash.slice(1));
  if (h.has('x')) { state.view.x = +h.get('x'); state.view.z = +h.get('z'); state.view.s = +h.get('zoom') || 0.5; }
  let seed = h.get('seed');
  buildFilters();
  try { const f = JSON.parse(localStorage.getItem('geodeFilters')); if (Array.isArray(f)) state.filters = new Set(f); } catch (e) { /* none saved */ }
  syncFilters(); drawLegend();
  $('seedGo').onclick = () => setSeed($('seed').value);
  $('seed').onkeydown = e => { if (e.key === 'Enter') setSeed($('seed').value); };
  $('go').onclick = goTo;
  [$('gx'), $('gz')].forEach(i => i.onkeydown = e => { if (e.key === 'Enter') goTo(); });
  ['lTerrain', 'lGrid', 'lAborted'].forEach(id => $(id).onchange = redraw);
  $('verify').onclick = verify;
  $('coords').textContent = 'Move the cursor over the map';
  try {
    state.info = await api('/api/info');
    state.maxVerify = state.info.max_verify_side;
    const i = state.info;
    $('aboutTech').textContent = `Locator ${i.version} · ${i.mod_jar.replace('.jar', '')} · geode feature step ${i.step}, index ${i.index}, rarity 1/${i.rarity_chance}, Y ${i.min_y} to ${i.max_y}. Data folder: ${i.data_dir}`;
    if (i.warnings.length) $('stats').innerHTML = i.warnings.join('<br>');
    for (const ext of i.extensions) {
      const s = document.createElement('script'); s.src = '/ext/' + ext + '.js'; document.body.appendChild(s);
    }
  } catch (e) { $('notice').textContent = 'The locator is not running: ' + e.message; $('notice').style.display = 'block'; }
  pollTerrain();
  if (!seed) { try { seed = localStorage.getItem('geodeSeed'); } catch (e) { /* none */ } }
  if (seed) await setSeed(seed); else { redraw(); scheduleLoad(); }
}

window.Locator = { state, hooks, api, redraw, setSeed, toScreen, toWorld, key, COLOURS, PAL, NICE, drawCrystal, iconCanvas, scheduleLoad,
  slot: $('extSlot') };
init();
