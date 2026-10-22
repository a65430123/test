// 台股技術分析網頁版：畫面與流程對照 Android App，指標與訊號使用 core.js（與 App core 模組相同演算法）。
import TA from './core.js';
import { createData } from './data.js';

const data = createData();
const ICON = {
  add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  refresh: 'M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
  settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  star: 'M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z',
  search: 'M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  trend: 'M3.5 18.49l6-6.01 4 4L22 6.92l-1.41-1.41-7.09 7.97-4-4L2 16.99z',
};
const svg = n => `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${ICON[n]}"/></svg>`;
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const f2 = v => v == null ? '--' : v.toFixed(2);
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const mmdd = iso => iso.slice(5).replace('-', '/');
const yymmdd = iso => iso.slice(2).replace(/-/g, '/');

const store = {
  get(k) { try { return JSON.parse(localStorage.getItem(k)); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};
let settings = Object.assign({ ...TA.DEFAULTS, notificationsEnabled: true, adjustPrices: true, themeMode: 'DARK' }, store.get('ta-settings') || {});
// 外觀（對照 App 的 ThemeMode）：預設深色；跟隨系統時交給 prefers-color-scheme
const THEME_MODES = [['SYSTEM', '跟隨系統'], ['LIGHT', '淺色'], ['DARK', '深色']];
function applyTheme() {
  const root = document.documentElement;
  if (settings.themeMode === 'SYSTEM') delete root.dataset.theme;
  else root.dataset.theme = settings.themeMode === 'LIGHT' ? 'light' : 'dark';
}
applyTheme();
// 舊版以規則計可存到 5，現改以指標計（1–3）
settings.minConfirmations = Math.min(3, Math.max(1, settings.minConfirmations));
// 市場別顯示：ETF 加註（對照 App 的 Market.labelFor）
const isEtf = code => /^00\d{2,4}[A-Z]?$/.test(code);
const mlabel = s => s.market + (isEtf(s.code) ? 'ETF' : '');
const typeLines = groups => groups.map(([k, list]) =>
  `<div class="type-line"><span class="tag tag-${k}">${TA.labelOf(k)}</span><span>${esc(list.map(e => e.desc).join('、'))}</span></div>`).join('');
let watchlist = store.get('ta-watchlist') || ['2330', '2317', '2454', '0050'];
const notified = store.get('ta-notified') || {};
const stockOf = code => data.stock(code);
const state = { loading: true, loadError: null, screen: 'watchlist', code: null, from: 'watchlist', detailFrom: 'watchlist', query: '', filters: { side: 'ALL', market: 'ALL', minVolume: 500, types: [] }, refreshing: false, chart: { visible: 80, offset: 0, sel: null }, draft: null, errors: [] };

// ---- 計算（依設定快取）----
const cache = new Map();
/** 日 K 尚未下載時回傳 null，並在下載完成後重繪。 */
function analysisFor(code) {
  const raw = data.candles(code);
  if (!raw) { need([code]); return null; }
  const key = code + JSON.stringify(settings);
  if (!cache.has(key)) {
    if (cache.size > 200) cache.clear();
    const ex = data.exRightsOf(code);
    const candles = settings.adjustPrices ? TA.adjust(raw, ex) : raw;
    cache.set(key, { ...TA.analyze(candles, settings), exDates: new Set(ex.map(e => e.exDate)), adjusted: settings.adjustPrices });
  }
  return cache.get(key);
}
// 需要的資料在背景下載，完成後重繪；失敗的代號記下來避免重複請求
const failed = new Set();
let redraw = 0;
function need(codes) {
  const todo = codes.filter(c => !data.candles(c) && !failed.has(c) && stockOf(c));
  if (!todo.length) return;
  Promise.all(todo.map(c => data.loadCandles(c).catch(() => failed.add(c)))).then(() => {
    cancelAnimationFrame(redraw); redraw = requestAnimationFrame(() => render());
  });
}

// ---- UI 共用 ----
const app = document.getElementById('app');
const overlay = document.getElementById('overlay');
let snackTimer, notifTimer;
function snack(msg) {
  clearTimeout(snackTimer);
  document.querySelector('.snackbar')?.remove();
  const el = document.createElement('div');
  el.className = 'snackbar'; el.setAttribute('role', 'status'); el.textContent = msg;
  document.getElementById('screen').appendChild(el);
  snackTimer = setTimeout(() => el.remove(), 2600);
}
function dialog(html, bind) {
  overlay.innerHTML = `<div class="scrim"><div class="dialog" role="dialog" aria-modal="true">${html}</div></div>`;
  const scrim = overlay.firstElementChild;
  scrim.addEventListener('click', e => { if (e.target === scrim) closeDialog(); });
  bind(scrim.firstElementChild);
}
const closeDialog = () => { overlay.innerHTML = ''; };
// ---- 網址（#/watchlist、#/screener、#/search、#/settings、#/stock/代號）----
const hashOf = (screen, code) => screen === 'detail' ? `#/stock/${code}` : `#/${screen}`;
function parseHash() {
  const [screen, code] = location.hash.replace(/^#\/?/, '').split('/');
  if (screen === 'stock' && code) return ['detail', decodeURIComponent(code).toUpperCase()];
  return [['watchlist', 'screener', 'search', 'settings'].includes(screen) ? screen : 'watchlist', null];
}
function go(screen, code, push = true) {
  // 記住離開時所在的主分頁，個股頁與設定頁返回時回到該分頁
  if (state.screen === 'watchlist' || state.screen === 'screener') state.from = state.screen;
  if (screen === 'detail' && state.screen !== 'detail') state.detailFrom = state.screen;
  state.screen = screen;
  if (code && code !== state.code) { state.code = code; state.chart = { visible: 80, offset: 0, sel: null }; }
  if (screen === 'settings') { state.draft = toDraft(settings); state.errors = []; }
  const hash = hashOf(screen, state.code);
  if (push && location.hash !== hash) history.pushState(null, '', hash);
  render();
  app.querySelector('.body')?.scrollTo(0, 0);
}
window.addEventListener('popstate', () => go(...parseHash(), false));

// 重新讀取資料（GitHub Actions 每個交易日收盤後更新）
async function refresh() {
  if (state.refreshing) return;
  state.refreshing = true; render();
  try {
    const changed = await data.init(true);
    cache.clear(); screenCache.clear(); failed.clear();
    snack(changed ? `已更新至 ${data.meta.latestDate}` : `已是最新資料（${data.meta.latestDate}）`);
  } catch (e) {
    snack('更新失敗，請稍後再試');
  }
  state.refreshing = false; render();
  if (state.screen === 'watchlist') loadWatchlist().then(() => runCheck(false));
}
const loadWatchlist = () => Promise.all(watchlist.filter(stockOf).map(c => data.loadCandles(c).catch(() => failed.add(c))));
const latestDate = () => data.meta?.latestDate || '';
const priceOf = s => {
  const chg = s.prevClose == null ? 0 : s.close - s.prevClose, pct = s.prevClose ? chg / s.prevClose * 100 : 0;
  return { chg, pct, cls: chg > 0 ? 'up' : chg < 0 ? 'down' : 'flat' };
};

// ---- 自選股 ----
function watchlistView() {
  const rows = watchlist.map(code => {
    const s = stockOf(code);
    if (!s) return `<li class="row" tabindex="0" data-code="${esc(code)}"><div class="row-main"><b>${esc(code)}</b><span class="meta">資料中沒有這個代號（可能已下市），長按可移除</span></div></li>`;
    const { chg, pct, cls } = priceOf(s);
    const a = analysisFor(code), sig = a?.signals.at(-1);
    const chip = sig ? `<span class="chip ${sig.side === 'BUY' ? 'buy' : 'sell'}">${mmdd(sig.date)} ${sig.side === 'BUY' ? '買進訊號' : '賣出訊號'}</span>`
      : a || failed.has(code) ? '' : '<span class="meta">計算訊號中…</span>';
    return `<li class="row" tabindex="0" data-code="${code}">
      <div class="row-main"><div class="row-title"><b>${esc(s.name)}</b><span class="meta">${code}・${mlabel(s)}</span></div>${chip}</div>
      <div class="row-price"><span class="px ${cls}">${f2(s.close)}</span><span class="chg ${cls}">${chg >= 0 ? '+' : ''}${chg.toFixed(2)} (${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%)</span></div>
    </li>`;
  }).join('');
  return `
    <header class="appbar"><h1 class="title">自選股</h1>
      <button class="icon-btn" id="open-search" aria-label="搜尋個股">${svg('search')}</button>
      <button class="icon-btn" id="refresh" aria-label="重新讀取資料">${svg('refresh')}</button>
      <button class="icon-btn" id="open-settings" aria-label="設定">${svg('settings')}</button></header>
    ${state.refreshing ? '<div class="progress"></div>' : ''}
    <div class="data-strip">資料日期 ${latestDate()}・每個交易日收盤後自動更新<br>訊號：${esc(TA.describeSignalMode(settings))}</div>
    <div class="body">${rows ? `<ul class="list">${rows}</ul>` : '<p class="empty">尚無自選股，點右下角 + 加入</p>'}</div>
    <button class="fab" id="add" aria-label="加入自選股">${svg('add')}</button>
    ${bottomNav('watchlist')}`;
}
const bottomNav = cur => `<nav class="bottom-nav">
  <button data-nav="screener" aria-current="${cur === 'screener'}">${svg('search')}<span>選股</span></button>
  <button data-nav="watchlist" aria-current="${cur === 'watchlist'}">${svg('star')}<span>自選股</span></button></nav>`;
const bindNav = () => app.querySelectorAll('[data-nav]').forEach(b => b.onclick = () => go(b.dataset.nav));

// ---- 搜尋個股（對照 SearchScreen）----
// 正規化：去空白、全形轉半形、英文大寫（對照 StockQuery.normalize）
const normalize = raw => [...raw].map(ch => { const c = ch.charCodeAt(0); return c >= 0xFF01 && c <= 0xFF5E ? String.fromCharCode(c - 0xFEE0) : ch; })
  .join('').replace(/[\s\u3000]/g, '').toUpperCase();
const looksLikeCode = q => /^\d{4,6}[A-Z]?$/.test(q);
function searchResults(raw) {
  const q = normalize(raw);
  if (!q) return [];
  const rank = s => s.code === q ? 0 : s.code.startsWith(q) ? 1 : s.name.startsWith(q) ? 2 : 3;
  return data.list.filter(s => s.code.startsWith(q) || s.name.toUpperCase().includes(q))
    .sort((a, b) => rank(a) - rank(b) || a.code.length - b.code.length || a.code.localeCompare(b.code)).slice(0, 50);
}
function searchView() {
  return `
    <header class="appbar has-back"><button class="icon-btn" id="back" aria-label="返回">${svg('back')}</button>
      <label class="search-box">${svg('search')}<input id="sq" type="search" enterkeyhint="search" autocomplete="off"
        placeholder="輸入股票代號或名稱" value="${esc(state.query)}" aria-label="輸入股票代號或名稱"></label></header>
    <div class="body"><ul class="list" id="search-results"></ul></div>`;
}
function drawSearchResults() {
  const list = app.querySelector('#search-results'), raw = state.query, q = normalize(raw), res = searchResults(raw);
  const rows = res.map(s => {
    const { pct, cls } = priceOf(s);
    return `<li class="row" tabindex="0" data-code="${s.code}"><div class="row-main"><div class="row-title"><b>${esc(s.name)}</b></div>
      <span class="meta">${s.code}・${mlabel(s)}</span></div>
      <div class="row-price"><span class="px ${cls}">${f2(s.close)}</span><span class="chg ${cls}">${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%</span></div></li>`;
  }).join('');
  const hint = !q ? '<li class="empty">例如：2330、台積電、聯發、0050、00878</li>'
    : !res.length ? `<li class="empty">${looksLikeCode(q) ? `查無股票代號 ${esc(q)}（上市、上櫃股票與 ETF）` : `找不到符合「${esc(raw.trim())}」的股票`}</li>` : '';
  list.innerHTML = rows + hint;
  list.querySelectorAll('.row[data-code]').forEach(li => {
    li.onclick = () => go('detail', li.dataset.code);
    li.onkeydown = e => { if (e.key === 'Enter') go('detail', li.dataset.code); };
  });
}
function bindSearch() {
  app.querySelector('#back').onclick = () => go(state.from);
  const input = app.querySelector('#sq');
  input.oninput = () => { state.query = input.value; drawSearchResults(); };
  input.onkeydown = e => {
    if (e.key !== 'Enter') return;
    const first = searchResults(state.query)[0];
    if (first) go('detail', first.code); else if (looksLikeCode(normalize(state.query))) snack(`查無股票代號 ${normalize(state.query)}`);
  };
  drawSearchResults();
  input.focus();
}

// ---- 選股（對照 ScreenerScreen）----
const screenCache = new Map();
function screenHits() {
  const sc = data.screen;
  if (!sc) return null;
  const key = JSON.stringify(settings) + sc.latestDate;
  if (!screenCache.has(key)) { screenCache.clear(); screenCache.set(key, TA.screen(sc.stocks, settings, sc.latestDate, settings.adjustPrices)); }
  return screenCache.get(key);
}
let screenFailed = false;
function screenerView() {
  const all = screenHits(), f = state.filters;
  if (!all) {
    if (!screenFailed) data.loadScreen().then(() => render(), () => { screenFailed = true; render(); });
    return `<header class="appbar"><h1 class="title">選股</h1>
        <button class="icon-btn" id="open-search" aria-label="搜尋個股">${svg('search')}</button>
        <button class="icon-btn" id="open-settings" aria-label="設定">${svg('settings')}</button></header>
      <div class="body">${screenFailed ? '<div class="error-box">選股資料下載失敗，請檢查網路後重新整理頁面。</div>'
        : `<div class="progress"></div><p class="loading">下載上市上櫃最近 60 個交易日行情並掃描訊號…</p>`}</div>
      ${bottomNav('screener')}`;
  }
  const enabled = TA.enabledTypes(settings);
  f.types = f.types.filter(t => enabled.includes(t)); // 已停用的類型不再參與篩選
  const base = all.filter(h => {
    const s = stockOf(h.code) || { market: '' }, etf = isEtf(h.code);
    if (f.minVolume && h.volume < f.minVolume) return false;
    if (f.side !== 'ALL' && h.signal.side !== f.side) return false;
    // 上市、上櫃只含股票；ETF 含上市與上櫃 ETF；全部含股票與 ETF
    if (f.market === 'ETF') return etf;
    return f.market === 'ALL' || (!etf && s.market === (f.market === 'TWSE' ? '上市' : '上櫃'));
  });
  // 類型（多選）：訊號包含任一選取類型即顯示
  const items = f.types.length ? base.filter(h => h.signal.types.some(t => f.types.includes(t))) : base;
  const typeLabel = t => TA.TYPES.find(x => x[0] === t)[1];
  const typeChips = () => enabled.length ? `<div class="chip-row"><span class="lbl">類型</span>${chip('types', '', '全部', !f.types.length)}${
    enabled.map(t => chip('types', t, `${typeLabel(t)}（${base.filter(h => h.signal.types.includes(t)).length}）`, f.types.includes(t))).join('')}</div>` : '';
  const scanned = data.screen.stocks.filter(s => s.candles.length >= 35 && s.candles.at(-1).date === data.screen.latestDate).length;
  const chip = (group, value, label, on) => `<button class="filter-chip" data-f="${group}" data-v="${value}" aria-pressed="${on}">${label}</button>`;
  const rows = items.map(h => {
    const s = stockOf(h.code) || { name: h.code, market: '' }, chg = h.prevClose ? h.close - h.prevClose : 0, pct = h.prevClose ? chg / h.prevClose * 100 : 0;
    const cls = chg > 0 ? 'up' : chg < 0 ? 'down' : 'flat', buy = h.signal.side === 'BUY';
    return `<li class="screen-row" tabindex="0" data-code="${h.code}">
      <div class="sr-top"><b class="${buy ? 'up' : 'down'}">${buy ? '▲ 買進' : '▼ 賣出'}</b><b>${esc(s.name)}</b><span class="meta">${h.code}・${mlabel(s)}</span>
        <span class="sr-px"><b class="${cls}">${f2(h.close)}</b><span class="${cls}">${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%</span></span></div>
      <div class="meta">成交量 ${h.volume.toLocaleString('en-US')} 張</div>
      <div class="types">${typeLines(h.signal.groups)}</div></li>`;
  }).join('');
  return `
    <header class="appbar"><h1 class="title">選股</h1>
      <button class="icon-btn" id="open-search" aria-label="搜尋個股">${svg('search')}</button>
      <button class="icon-btn" id="refresh" aria-label="重新讀取資料">${svg('refresh')}</button>
      <button class="icon-btn" id="open-settings" aria-label="設定">${svg('settings')}</button></header>
    ${state.refreshing ? '<div class="progress"></div>' : ''}
    <div class="body">
      <div class="screen-status">資料日期 ${data.screen.latestDate}・掃描 ${scanned} 檔・出現訊號 ${all.length} 檔・顯示 ${items.length} 檔
        <div class="meta">訊號：${esc(TA.describeSignalMode(settings))}（於個股頁或設定頁調整）</div>
        <div class="meta">上市、上櫃股票與 ETF，資料來源：證交所、櫃買中心，每個交易日收盤後自動更新</div></div>
      <div class="mode">
        <div class="chip-row"><span class="lbl">方向</span>${chip('side', 'ALL', '全部', f.side === 'ALL')}${chip('side', 'BUY', '買進', f.side === 'BUY')}${chip('side', 'SELL', '賣出', f.side === 'SELL')}</div>
        <div class="chip-row"><span class="lbl">市場</span>${chip('market', 'ALL', '全部', f.market === 'ALL')}${chip('market', 'TWSE', '上市', f.market === 'TWSE')}${chip('market', 'TPEX', '上櫃', f.market === 'TPEX')}${chip('market', 'ETF', 'ETF', f.market === 'ETF')}</div>
        <div class="chip-row"><span class="lbl">成交量</span>${[0, 500, 1000, 5000].map(v => chip('minVolume', v, v ? '≥' + v.toLocaleString('en-US') + ' 張' : '不限', f.minVolume === v)).join('')}</div>
        ${typeChips()}
      </div>
      <ul class="screen-list">${rows || `<li class="empty">${all.length ? `共 ${all.length} 檔出現訊號，但都不符合目前的篩選條件` : '最新交易日沒有出現進出場訊號的股票'}</li>`}</ul>
    </div>
    ${bottomNav('screener')}`;
}
function bindScreener() {
  bindNav();
  app.querySelector('#open-search').onclick = () => go('search');
  app.querySelector('#refresh')?.addEventListener('click', refresh);
  app.querySelector('#open-settings').onclick = () => go('settings');
  app.querySelectorAll('[data-f]').forEach(b => b.onclick = () => {
    const g = b.dataset.f, f = state.filters;
    const v = b.dataset.v;
    if (g === 'minVolume') f.minVolume = +v;
    else if (g === 'types') f.types = !v ? [] : f.types.includes(v) ? f.types.filter(t => t !== v) : [...f.types, v];
    else f[g] = v;
    render();
  });
  app.querySelectorAll('.screen-row').forEach(li => {
    li.onclick = () => go('detail', li.dataset.code);
    li.onkeydown = e => { if (e.key === 'Enter') go('detail', li.dataset.code); };
  });
}
function bindWatchlist() {
  bindNav();
  app.querySelector('#open-search').onclick = () => go('search');
  app.querySelector('#refresh').onclick = refresh;
  app.querySelector('#open-settings').onclick = () => go('settings');
  app.querySelector('#add').onclick = openAddDialog;
  app.querySelectorAll('.row').forEach(row => {
    const code = row.dataset.code;
    let timer, long = false, sx = 0, sy = 0;
    row.addEventListener('pointerdown', e => { long = false; sx = e.clientX; sy = e.clientY; timer = setTimeout(() => { long = true; confirmRemove(code); }, 550); });
    row.addEventListener('pointermove', e => { if (Math.hypot(e.clientX - sx, e.clientY - sy) > 8) clearTimeout(timer); });
    ['pointerup', 'pointerleave', 'pointercancel'].forEach(t => row.addEventListener(t, () => clearTimeout(timer)));
    row.addEventListener('click', () => { if (!long) go('detail', code); });
    row.addEventListener('contextmenu', e => { e.preventDefault(); clearTimeout(timer); confirmRemove(code); });
    row.addEventListener('keydown', e => { if (e.key === 'Enter') go('detail', code); if (e.key === 'Delete') confirmRemove(code); });
  });
}
function confirmRemove(code) {
  const s = stockOf(code) || { name: '' };
  dialog(`<h3>移除自選股</h3><p>確定要移除 ${esc(code)} ${esc(s.name)}？</p>
    <div class="dialog-actions"><button class="text-btn" data-a="no">取消</button><button class="text-btn" data-a="yes">移除</button></div>`, d => {
    d.querySelector('[data-a=no]').onclick = closeDialog;
    d.querySelector('[data-a=yes]').onclick = () => {
      watchlist = watchlist.filter(c => c !== code); store.set('ta-watchlist', watchlist);
      closeDialog(); render(); snack(`已移除 ${code} ${s.name}`);
    };
  });
}
function openAddDialog() {
  dialog(`<h3>加入自選股</h3>
    <div class="field"><label for="q">代號或名稱</label><input id="q" autocomplete="off" placeholder="例如 2330 或 台積電"></div>
    <ul class="results" id="results"></ul>
    <div class="dialog-actions"><button class="text-btn" data-a="cancel">取消</button><button class="text-btn" data-a="code" disabled>以代號加入</button></div>`, d => {
    const q = d.querySelector('#q'), list = d.querySelector('#results'), byCode = d.querySelector('[data-a=code]');
    const add = code => {
      const s = stockOf(code);
      if (!s) { snack(`查無股票代號 ${code}`); return; }
      if (!watchlist.includes(code)) { watchlist.push(code); store.set('ta-watchlist', watchlist); }
      closeDialog(); render(); snack(`已加入 ${code} ${s.name}`);
    };
    const draw = () => {
      const t = normalize(q.value);
      byCode.disabled = !t;
      list.innerHTML = (t ? searchResults(t) : data.list).slice(0, 30).map(s => {
        const added = watchlist.includes(s.code);
        return `<li data-code="${s.code}" aria-disabled="${added}"><span>${s.code} ${esc(s.name)}</span><span class="meta">${added ? '已在清單' : mlabel(s)}</span></li>`;
      }).join('');
      list.querySelectorAll('li[aria-disabled=false]').forEach(li => li.onclick = () => add(li.dataset.code));
    };
    q.oninput = draw; draw(); q.focus();
    d.querySelector('[data-a=cancel]').onclick = closeDialog;
    byCode.onclick = () => add(normalize(q.value));
  });
}

// ---- 個股 ----
function detailView() {
  const s = stockOf(state.code), a = s && analysisFor(state.code);
  const head = `<header class="appbar has-back"><button class="icon-btn" id="back" aria-label="返回">${svg('back')}</button>
      <h1 class="title">${esc(state.code)} ${esc(s?.name || '')}${s ? `<small>${mlabel(s)}</small>` : ''}</h1></header>`;
  if (!s) return `${head}<div class="body"><div class="error-box">查無股票代號 ${esc(state.code)}（上市、上櫃股票與 ETF）</div></div>`;
  if (!a) return `${head}<div class="body">${failed.has(state.code) ? '<div class="error-box">日 K 資料下載失敗，請檢查網路後重新整理頁面。</div>'
    : '<div class="progress"></div><p class="loading">載入日 K…</p>'}</div>`;
  const sigs = a.signals.slice().reverse().map(g => `
    <li data-index="${g.index}"><span class="side ${g.side === 'BUY' ? 'up' : 'down'}">${g.side === 'BUY' ? '▲ 買' : '▼ 賣'}</span>
      <div><div class="when">${g.date}　${a.adjusted ? '還原收盤' : '收盤'} ${f2(g.price)}</div><div class="types">${typeLines(g.groups)}</div></div></li>`).join('');
  return `
    <header class="appbar has-back"><button class="icon-btn" id="back" aria-label="返回">${svg('back')}</button>
      <h1 class="title">${state.code} ${esc(s.name)}<small>${mlabel(s)}</small></h1>
      ${watchlist.includes(state.code) ? '' : `<button class="icon-btn" id="add-watch" aria-label="加入自選股">${svg('add')}</button>`}
      <button class="icon-btn" id="refresh" aria-label="重新讀取資料">${svg('refresh')}</button></header>
    ${state.refreshing ? '<div class="progress"></div>' : ''}
    <div class="body">
      <div class="toggle-row"><button class="filter-chip" id="adjust" aria-pressed="${a.adjusted}">除權息還原</button>
        <span>${a.adjusted ? '價格與指標以還原價計算' : '原始價（除權息日可能產生缺口）'}</span></div>
      ${modeSelector()}
      <div class="readout" id="readout"></div>
      <div class="chart-wrap"><canvas class="chart" id="chart" aria-label="K 線、布林通道、KD 與 MACD 圖表"></canvas></div>
      <div class="bar-signal" id="bar-signal" hidden></div>
      <div class="hint">雙指或滾輪縮放、左右拖曳平移、點選 K 棒查看數值與當日訊號</div>
      <h2 class="section-title">進出場訊號（共 ${a.signals.length} 筆）</h2>
      <ul class="signals">${sigs || '<li class="why">目前參數下沒有訊號</li>'}</ul>
    </div>`;
}
function modeSelector() {
  const sel = TA.selectedIndicators(settings), req = TA.requiredConfirmations(settings);
  const chips = TA.INDICATORS.map(([k, label]) =>
    `<button class="filter-chip" data-ind="${k}" aria-pressed="${sel.includes(k)}">${label}</button>`).join('');
  const confirm = sel.length > 1 ? `<div class="chip-row"><span class="lbl">確認方式</span>${
    Array.from({ length: sel.length }, (_, i) => i + 1).map(n =>
      `<button class="filter-chip" data-confirm="${n}" aria-pressed="${req === n}">${n === 1 ? '任一' : n + ' 個'}</button>`).join('')}</div>` : '';
  return `<div class="mode"><div class="chip-row"><span class="lbl">訊號來源</span>${chips}</div>${confirm}
    <div class="mode-desc">${esc(TA.describeSignalMode(settings))}</div></div>`;
}
function saveSignalParams(next) {
  if (TA.validate(next).length) return;
  settings = next; store.set('ta-settings', settings); render();
}

function bindDetail() {
  app.querySelector('#back').onclick = () => go(state.detailFrom === 'search' ? 'search' : state.from);
  if (!app.querySelector('#chart')) return; // 查無或載入中
  app.querySelectorAll('[data-ind]').forEach(b => b.onclick = () => {
    const k = b.dataset.ind, on = b.getAttribute('aria-pressed') !== 'true';
    if (!on && TA.selectedIndicators(settings).length === 1) { snack('至少需保留一個指標'); return; }
    saveSignalParams(TA.toggleIndicator(settings, k, on));
  });
  app.querySelectorAll('[data-confirm]').forEach(b => b.onclick = () => saveSignalParams({ ...settings, minConfirmations: +b.dataset.confirm }));
  app.querySelector('#back').onclick = () => go(state.detailFrom === 'search' ? 'search' : state.from);
  app.querySelector('#add-watch')?.addEventListener('click', () => {
    watchlist.push(state.code); store.set('ta-watchlist', watchlist); render(); snack(`已加入 ${state.code} ${stockOf(state.code).name}`);
  });
  app.querySelector('#refresh').onclick = refresh;
  app.querySelector('#adjust').onclick = () => {
    settings.adjustPrices = !settings.adjustPrices; store.set('ta-settings', settings); render();
  };
  app.querySelectorAll('.signals li[data-index]').forEach(li => li.onclick = () => focusBar(+li.dataset.index));
  bindChart(app.querySelector('#chart'));
  drawChart();
}
function focusBar(i) {
  const n = analysisFor(state.code)?.candles.length || 0, count = clamp(state.chart.visible, 20, 250);
  state.chart.sel = i;
  state.chart.offset = clamp(n - 1 - i - count / 2, 0, Math.max(0, n - count));
  drawChart();
  app.querySelector('.body').scrollTo({ top: 0, behavior: 'smooth' });
}

// ---- 圖表（對照 StockChart.kt）----
const AX = 52, LEG = 16, GAP = 6, MH = 230, VH = 74, KH = 90, CH = 100, CHART_H = 530;
function viewport(n, plotW) {
  const count = clamp(state.chart.visible, 20, 250);
  const off = Math.round(clamp(state.chart.offset, 0, Math.max(0, n - count)));
  const end = n - off, start = Math.max(0, end - Math.round(count)), bw = plotW / count;
  return { start, end, bw, x: i => plotW - (end - i - 0.5) * bw,
    indexAt: x => { const i = end - 1 - Math.floor((plotW - x) / bw); return i >= start && i < end ? i : null; } };
}
function bindChart(cv) {
  const pts = new Map(); let tap = null, lastDist = null;
  const n = () => analysisFor(state.code)?.candles.length || 0;
  const zoom = f => { state.chart.visible = clamp(state.chart.visible * f, 20, 250); state.chart.offset = clamp(state.chart.offset, 0, Math.max(0, n() - state.chart.visible)); drawChart(); };
  cv.addEventListener('pointerdown', e => {
    cv.setPointerCapture(e.pointerId); pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    tap = pts.size === 1 ? { x: e.clientX, y: e.clientY, t: Date.now(), moved: false, mode: null } : null; lastDist = null;
  });
  cv.addEventListener('pointermove', e => {
    if (!pts.has(e.pointerId)) return;
    const prev = pts.get(e.pointerId), cur = { x: e.clientX, y: e.clientY }; pts.set(e.pointerId, cur);
    if (pts.size === 1) {
      // 先判斷方向：水平才平移圖表，垂直交給頁面捲動（對照 App 的 detectChartGestures）
      if (tap && !tap.mode && Math.hypot(cur.x - tap.x, cur.y - tap.y) > 6) {
        tap.moved = true;
        tap.mode = Math.abs(cur.x - tap.x) > Math.abs(cur.y - tap.y) ? 'pan' : 'vertical';
      }
      if (tap && tap.mode === 'pan') {
        const bw = (cv.clientWidth - AX) / clamp(state.chart.visible, 20, 250);
        state.chart.offset = clamp(state.chart.offset + (cur.x - prev.x) / bw, 0, Math.max(0, n() - state.chart.visible));
        drawChart();
      }
    } else if (pts.size === 2) {
      if (tap) tap.mode = 'zoom';
      const [p, q] = [...pts.values()], d = Math.hypot(p.x - q.x, p.y - q.y);
      if (lastDist) zoom(lastDist / d);
      lastDist = d;
    }
  });
  const end = e => {
    if (!pts.delete(e.pointerId)) return;
    if (pts.size === 0 && tap && !tap.moved && Date.now() - tap.t < 500) {
      const r = cv.getBoundingClientRect();
      const idx = viewport(n(), cv.clientWidth - AX).indexAt(e.clientX - r.left);
      state.chart.sel = idx === state.chart.sel ? null : idx; drawChart();
    }
    if (pts.size === 0) tap = null; lastDist = null;
  };
  cv.addEventListener('pointerup', end); cv.addEventListener('pointercancel', end);
  cv.addEventListener('wheel', e => { e.preventDefault(); zoom(e.deltaY > 0 ? 1.12 : 0.89); }, { passive: false });
}
let rafId = 0;
function drawChart() { cancelAnimationFrame(rafId); rafId = requestAnimationFrame(paint); }
function paint() {
  const cv = document.getElementById('chart'); if (!cv) return;
  const a = analysisFor(state.code); if (!a || !a.candles.length) return;
  const c = a.candles, n = c.length, p = settings;
  const css = getComputedStyle(document.documentElement), col = k => css.getPropertyValue('--' + k).trim();
  const C = { up: col('up'), down: col('down'), flat: col('flat'), bb: col('bb'), ma: col('ma'), k: col('kline'), d: col('dline'), ex: col('ex'), grid: col('grid'), line: col('line'), text: col('muted'), fg: col('fg') };
  const W = cv.clientWidth, H = CHART_H, dpr = window.devicePixelRatio || 1;
  cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr);
  const g = cv.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0); g.clearRect(0, 0, W, H);
  const plotW = W - AX, vp = viewport(n, plotW);
  const sel = state.chart.sel != null && state.chart.sel >= vp.start && state.chart.sel < vp.end ? state.chart.sel : vp.end - 1;
  const numFont = '10px "IBM Plex Mono", ui-monospace, monospace';
  const pane = (top, h) => ({ top: top + LEG, bottom: top + h, y(v, lo, hi) { return this.bottom - (v - lo) / ((hi - lo) || 1) * (this.bottom - this.top); } });
  const mainP = pane(0, MH), volP = pane(MH + GAP, VH), kdP = pane(MH + VH + 2 * GAP, KH), macdP = pane(MH + VH + KH + 3 * GAP, CH);
  const fmtVol = v => v >= 10000 ? (v / 10000).toFixed(1) + '萬' : v.toFixed(0);

  const grid = (P, levels, lo, hi, fmt) => {
    g.strokeStyle = C.line; g.lineWidth = 1; g.setLineDash([]); g.strokeRect(0.5, P.top + 0.5, plotW - 1, P.bottom - P.top - 1);
    g.font = numFont; g.textBaseline = 'middle'; g.fillStyle = C.text;
    for (const v of levels) {
      const y = Math.round(P.y(v, lo, hi)) + 0.5;
      g.strokeStyle = C.grid; g.setLineDash([4, 4]); g.beginPath(); g.moveTo(0, y); g.lineTo(plotW, y); g.stroke();
      g.fillText(fmt(v), plotW + 4, y);
    }
    g.setLineDash([]);
  };
  const series = (vals, P, lo, hi, color) => {
    g.strokeStyle = color; g.lineWidth = 1.3; g.beginPath(); let on = false;
    for (let i = vp.start; i < vp.end; i++) {
      const v = vals[i]; if (v == null) { on = false; continue; }
      const x = vp.x(i), y = P.y(v, lo, hi); on ? g.lineTo(x, y) : g.moveTo(x, y); on = true;
    }
    g.stroke();
  };
  const legend = (P, parts) => {
    g.font = numFont; g.textBaseline = 'top'; let x = 2;
    for (const [t, color] of parts) { g.fillStyle = color; g.fillText(t, x, P.top - LEG + 2); x += g.measureText(t).width; }
  };

  // 主圖
  let lo = Infinity, hi = -Infinity;
  for (let i = vp.start; i < vp.end; i++) {
    lo = Math.min(lo, c[i].low, a.bollinger.lower[i] ?? c[i].low); hi = Math.max(hi, c[i].high, a.bollinger.upper[i] ?? c[i].high);
  }
  const pad = (hi - lo) * 0.06; lo -= pad; hi += pad;
  grid(mainP, [lo + (hi - lo) * 0.1, lo + (hi - lo) * 0.5, lo + (hi - lo) * 0.9], lo, hi, v => v.toFixed(2));
  g.font = '10px "Noto Sans TC", sans-serif';
  for (let i = vp.start; i < vp.end; i++) {
    if (!a.exDates.has(c[i].date)) continue;
    const x = Math.round(vp.x(i)) + 0.5;
    g.strokeStyle = C.ex; g.globalAlpha = 0.55; g.setLineDash([3, 3]); g.beginPath(); g.moveTo(x, mainP.top); g.lineTo(x, mainP.bottom); g.stroke();
    g.globalAlpha = 1; g.setLineDash([]); g.fillStyle = C.ex; g.textBaseline = 'bottom'; g.textAlign = 'center'; g.fillText('除', x, mainP.bottom - 1); g.textAlign = 'left';
  }
  series(a.bollinger.upper, mainP, lo, hi, C.bb); series(a.bollinger.lower, mainP, lo, hi, C.bb); series(a.bollinger.middle, mainP, lo, hi, C.ma);
  const bodyW = Math.max(1, vp.bw * 0.7);
  for (let i = vp.start; i < vp.end; i++) {
    const k = c[i], color = k.close > k.open ? C.up : k.close < k.open ? C.down : C.flat, x = vp.x(i);
    g.strokeStyle = color; g.lineWidth = 1; g.beginPath(); g.moveTo(x, mainP.y(k.high, lo, hi)); g.lineTo(x, mainP.y(k.low, lo, hi)); g.stroke();
    const yT = mainP.y(Math.max(k.open, k.close), lo, hi), yB = mainP.y(Math.min(k.open, k.close), lo, hi);
    g.fillStyle = color; g.fillRect(x - bodyW / 2, yT, bodyW, Math.max(1, yB - yT));
  }
  const ms = Math.max(6, Math.min(vp.bw, 14));
  for (const s of a.signals) {
    if (s.index < vp.start || s.index >= vp.end) continue;
    const x = vp.x(s.index), k = c[s.index], buy = s.side === 'BUY';
    const tip = buy ? mainP.y(k.low, lo, hi) + 4 : mainP.y(k.high, lo, hi) - 4, h = ms * 0.9 * (buy ? 1 : -1);
    g.fillStyle = buy ? C.up : C.down; g.beginPath(); g.moveTo(x, tip); g.lineTo(x - ms / 2, tip + h); g.lineTo(x + ms / 2, tip + h); g.closePath(); g.fill();
  }
  legend(mainP, [[`MA${p.bbPeriod} ${f2(a.bollinger.middle[sel])}  `, C.ma], [`上 ${f2(a.bollinger.upper[sel])}  下 ${f2(a.bollinger.lower[sel])}`, C.bb]]);

  // 成交量
  let vmax = 0;
  for (let i = vp.start; i < vp.end; i++) vmax = Math.max(vmax, c[i].volume, a.volumeMaShort[i] ?? 0, a.volumeMaLong[i] ?? 0);
  if (!vmax) vmax = 1;
  grid(volP, [vmax / 2, vmax], 0, vmax * 1.1, fmtVol);
  const vbw = Math.max(1, vp.bw * 0.7);
  g.globalAlpha = 0.75;
  for (let i = vp.start; i < vp.end; i++) {
    const k = c[i], y = volP.y(k.volume, 0, vmax * 1.1);
    g.fillStyle = k.close > k.open ? C.up : k.close < k.open ? C.down : C.flat;
    g.fillRect(vp.x(i) - vbw / 2, y, vbw, volP.bottom - y);
  }
  g.globalAlpha = 1;
  series(a.volumeMaShort, volP, 0, vmax * 1.1, C.ma); series(a.volumeMaLong, volP, 0, vmax * 1.1, C.d);
  const vs = a.volumeMaShort[sel], vl = a.volumeMaLong[sel];
  legend(volP, [[`量 ${fmtVol(c[sel].volume)}張  `, C.text], [`MA${p.volumeMaShort} ${vs == null ? '--' : fmtVol(vs)}  `, C.ma], [`MA${p.volumeMaLong} ${vl == null ? '--' : fmtVol(vl)}`, C.d]]);

  // KD
  grid(kdP, [p.kdOversold, 50, p.kdOverbought], 0, 100, v => v.toFixed(0));
  series(a.kd.k, kdP, 0, 100, C.k); series(a.kd.d, kdP, 0, 100, C.d);
  legend(kdP, [[`KD(${p.kdPeriod},${p.kdKSmoothing},${p.kdDSmoothing})  `, C.text], [`K ${f2(a.kd.k[sel])}  `, C.k], [`D ${f2(a.kd.d[sel])}`, C.d]]);

  // MACD
  const m = a.macd; let ext = 0;
  for (let i = vp.start; i < vp.end; i++) for (const v of [m.dif[i], m.signal[i], m.histogram[i]]) if (v != null) ext = Math.max(ext, Math.abs(v));
  if (!ext) ext = 1; ext *= 1.1;
  grid(macdP, [-ext / 1.1, 0, ext / 1.1], -ext, ext, v => v.toFixed(2));
  const zy = macdP.y(0, -ext, ext), bw2 = Math.max(1, vp.bw * 0.6);
  for (let i = vp.start; i < vp.end; i++) {
    const h = m.histogram[i]; if (h == null) continue;
    const y = macdP.y(h, -ext, ext); g.globalAlpha = 0.7; g.fillStyle = h >= 0 ? C.up : C.down;
    g.fillRect(vp.x(i) - bw2 / 2, Math.min(y, zy), bw2, Math.abs(zy - y)); g.globalAlpha = 1;
  }
  series(m.dif, macdP, -ext, ext, C.k); series(m.signal, macdP, -ext, ext, C.d);
  const hs = m.histogram[sel];
  legend(macdP, [[`MACD(${p.macdFast},${p.macdSlow},${p.macdSignal}) `, C.text], [`DIF ${f2(m.dif[sel])} `, C.k], [`MACD ${f2(m.signal[sel])} `, C.d], [`OSC ${f2(hs)}`, hs == null || hs === 0 ? C.flat : hs > 0 ? C.up : C.down]]);

  // 日期軸
  g.font = numFont; g.fillStyle = C.text; g.textBaseline = 'top';
  const idx = [...new Set([vp.start, Math.floor((vp.start + vp.end - 1) / 2), vp.end - 1])];
  idx.forEach((i, j) => {
    const t = yymmdd(c[i].date), w = g.measureText(t).width;
    let x = vp.x(i) - w / 2; if (j === 0) x = Math.max(0, x); if (j === idx.length - 1) x = Math.min(plotW - w, x);
    g.fillText(t, x, macdP.bottom + 3);
  });

  if (state.chart.sel != null && state.chart.sel === sel) {
    const x = Math.round(vp.x(sel)) + 0.5;
    g.strokeStyle = C.text; g.globalAlpha = 0.6; g.beginPath(); g.moveTo(x, 0); g.lineTo(x, macdP.bottom); g.stroke(); g.globalAlpha = 1;
  }

  // 讀數列（DOM）
  const k = c[sel], prev = c[sel - 1], chg = prev ? k.close - prev.close : 0, cls = chg > 0 ? 'up' : chg < 0 ? 'down' : 'flat';
  const ro = document.getElementById('readout');
  if (ro) ro.innerHTML = `${a.adjusted ? '[還原] ' : ''}${yymmdd(k.date)} 開${f2(k.open)} 高${f2(k.high)} 低${f2(k.low)} <b class="${cls}">收${f2(k.close)}</b>`;

  // 選取 K 棒的當日訊號（對照 App 的 SelectedBarSignals）
  const box = document.getElementById('bar-signal');
  if (box) {
    const picked = state.chart.sel != null && state.chart.sel === sel;
    box.hidden = !picked;
    if (picked) {
      const sigs = a.signals.filter(s => s.index === sel), evs = a.events.filter(e => e.index === sel);
      const body = sigs.length
        ? sigs.map(s => `<div class="bs-line"><b class="${s.side === 'BUY' ? 'up' : 'down'}">${s.side === 'BUY' ? '▲ 買進訊號' : '▼ 賣出訊號'}</b>
            <span>${a.adjusted ? '還原收盤' : '收盤'} ${f2(s.price)}</span></div><div class="types">${typeLines(s.groups)}</div>`).join('')
        : evs.length
          ? `<div>沒有進出場訊號（未達確認條件）</div><div class="types">${typeLines(TA.groupEvents(evs))}</div>`
          : '<div class="why">這一天沒有進出場訊號</div>';
      box.innerHTML = `<b class="bs-title">${k.date} 的進出場訊號</b>${body}`;
    }
  }
}

// ---- 設定 ----
const SECTIONS = [
  { title: '價格資料', switches: [['adjustPrices', '除權息還原價']], hint: '開啟後，除權息日之前的價格依「參考價 ÷ 除權息前收盤價」向前還原，避免除權息缺口造成假訊號。自選股清單的現價不受影響。' },
  { title: '布林通道', switches: [['bollingerSignals', '納入訊號判斷']], rows: [[['bbPeriod', '均線週期', 'int'], ['bbMultiplier', '標準差倍數', 'dec']]],
    switchesMid: [['useBollinger', '收回訊號（跌破下軌後收回買、突破上軌後跌回賣）'], ['useBollingerBreakout', '突破訊號（突破上軌賣、跌破下軌買）']],
    rows2: [[['bbBreakoutVolumeRatio', '突破需量增倍數', 'dec']]], hint2: '當日成交量需達前一日短期均量的幾倍才算有效突破／跌破；0 表示不檢查' },
  { title: '成交量', rows: [[['volumeMaShort', '短期均量', 'int'], ['volumeMaLong', '長期均量', 'int']]] },
  { title: 'KD 隨機指標', switches: [['useKd', '納入訊號判斷']], note: '固定包含：超賣區黃金交叉買、超買區死亡交叉賣', switchesMid: [['kdMidCross', '中檔交叉（高低檔以外的黃金交叉買、死亡交叉賣）']], rows: [[['kdPeriod', 'RSV 週期', 'int'], ['kdInitial', '初始值', 'dec']], [['kdKSmoothing', 'K 平滑係數', 'int'], ['kdDSmoothing', 'D 平滑係數', 'int']]],
    hint: 'K = 前日K × (n-1)/n + RSV × 1/n；n = 3 即 2/3、1/3', rows2: [[['kdOversold', '超賣門檻', 'dec'], ['kdOverbought', '超買門檻', 'dec']]] },
  { title: 'MACD', switches: [['useMacd', '納入訊號判斷'], ['macdCross', '交叉訊號（DIF 上穿訊號線買、下穿賣）'], ['macdHistogramTurn', '柱狀轉折（最低點後第一根往上買、最高點後第一根往下賣）']], rows: [[['macdFast', '快線 EMA', 'int'], ['macdSlow', '慢線 EMA', 'int'], ['macdSignal', '訊號線', 'int']]] },
  { title: '訊號確認', summary: true, rows: [[['minConfirmations', '確認數（多選時）', 'int'], ['confirmWindow', '確認視窗（日）', 'int']]],
    hint: '只選一個指標時，該指標任一類型的訊號都會發出。多選時，需在視窗天數內有指定數量的不同指標（布林通道、KD、MACD）給出同方向訊號；同一指標的多種類型只算一個，確認數上限為已選指標數', switchesAfter: [['notificationsEnabled', '開啟網頁時提示自選股新訊號']] },
];
const NUM_KEYS = SECTIONS.flatMap(s => [...(s.rows || []), ...(s.rows2 || [])].flat());
const toDraft = s => Object.fromEntries(Object.entries(s).map(([k, v]) => [k, typeof v === 'number' ? String(v) : v]));
function settingsView() {
  const d = state.draft;
  const sw = ([k, label]) => `<div class="switch-row"><span id="lbl-${k}">${label}</span><button class="switch" role="switch" aria-labelledby="lbl-${k}" aria-checked="${!!d[k]}" data-k="${k}"></button></div>`;
  const rows = rs => (rs || []).map(r => `<div class="fields">${r.map(([k, label, t]) =>
    `<div class="field"><label for="f-${k}">${label}</label><input id="f-${k}" data-k="${k}" inputmode="${t === 'int' ? 'numeric' : 'decimal'}" value="${esc(d[k])}"></div>`).join('')}</div>`).join('');
  const themeCard = `<div class="card"><h3>外觀</h3><div class="chip-row">${THEME_MODES.map(([k, label]) =>
    `<button class="filter-chip" data-theme-mode="${k}" aria-pressed="${settings.themeMode === k}">${label}</button>`).join('')}</div>
    <p class="small-hint">立即套用，不需按儲存</p></div>`;
  const cards = themeCard + SECTIONS.map(s => `<div class="card"><h3>${s.title}</h3>${s.summary ? '<p class="mode-now" id="mode-now"></p>' : ''}${(s.switches || []).map(sw).join('')}${s.note ? `<p class="small-hint">${s.note}</p>` : ''}${rows(s.rows)}
    ${(s.switchesMid || []).map(sw).join('')}${s.hint ? `<p class="small-hint">${s.hint}</p>` : ''}${rows(s.rows2)}
    ${s.hint2 ? `<p class="small-hint">${s.hint2}</p>` : ''}${(s.switchesAfter || []).map(sw).join('')}</div>`).join('');
  return `
    <header class="appbar has-back"><button class="icon-btn" id="back" aria-label="返回">${svg('back')}</button><h1 class="title">指標設定</h1></header>
    <div class="body"><div class="settings">${cards}
      ${state.errors.length ? `<ul class="errors">${state.errors.map(e => `<li>${esc(e)}</li>`).join('')}</ul>` : ''}
      <div class="btn-row"><button class="btn" id="reset">恢復預設</button><button class="btn primary" id="save">儲存</button></div>
      <div class="card"><h3>瀏覽器通知</h3><p class="small-hint" id="perm">${permText()}</p>
        ${'Notification' in window && Notification.permission === 'default' ? '<button class="btn" id="perm-btn">允許瀏覽器通知</button>' : ''}</div>
      <button class="btn" id="check">立即檢查自選股訊號</button>
      <p class="small-hint">設定與自選股只存在這個瀏覽器。訊號僅為技術指標之機械式判斷，不構成任何投資建議。資料來源：臺灣證券交易所、證券櫃檯買賣中心。</p>
    </div></div>`;
}
function draftParams() {
  const next = { ...state.draft };
  for (const [k, , t] of NUM_KEYS) {
    const raw = String(next[k]).trim(), v = raw === '' ? NaN : Number(raw);
    next[k] = t === 'int' && !Number.isInteger(v) ? NaN : v;
  }
  return next;
}
function updateModeNow() {
  const el = app.querySelector('#mode-now'), p = draftParams();
  if (el) el.textContent = Number.isNaN(p.minConfirmations) || Number.isNaN(p.confirmWindow) ? '目前：—' : `目前：${TA.describeSignalMode(p)}`;
}
function bindSettings() {
  app.querySelectorAll('[data-theme-mode]').forEach(b => b.onclick = () => {
    settings.themeMode = b.dataset.themeMode; store.set('ta-settings', settings); applyTheme();
    app.querySelectorAll('[data-theme-mode]').forEach(x => x.setAttribute('aria-pressed', x === b));
  });
  app.querySelector('#back').onclick = () => go(state.from);
  app.querySelectorAll('input[data-k]').forEach(i => i.oninput = () => { state.draft[i.dataset.k] = i.value; updateModeNow(); });
  app.querySelectorAll('.switch').forEach(b => b.onclick = () => {
    const k = b.dataset.k; state.draft[k] = !state.draft[k]; b.setAttribute('aria-checked', state.draft[k]); updateModeNow();
  });
  updateModeNow();
  app.querySelector('#reset').onclick = () => {
    state.draft = toDraft({ ...TA.DEFAULTS, notificationsEnabled: state.draft.notificationsEnabled, adjustPrices: state.draft.adjustPrices });
    state.errors = []; render();
  };
  app.querySelector('#save').onclick = () => {
    const next = draftParams();
    const errs = NUM_KEYS.some(([k]) => Number.isNaN(next[k])) ? ['請確認所有欄位皆為有效數字（週期需為整數）'] : TA.validate(next);
    state.errors = errs;
    if (errs.length) { render(); return; }
    settings = { ...next, themeMode: settings.themeMode }; store.set('ta-settings', settings); state.draft = toDraft(settings);
    render(); snack('已儲存設定');
  };
  app.querySelector('#check').onclick = () => loadWatchlist().then(() => runCheck(true));
  app.querySelector('#perm-btn')?.addEventListener('click', () => Notification.requestPermission().then(() => render()));
}
function permText() {
  if (!('Notification' in window)) return '這個瀏覽器不支援系統通知，新訊號只會在網頁上方提示。';
  return { granted: '已允許：開啟網頁時若有新訊號，也會送出系統通知。', denied: '已封鎖：新訊號只會在網頁上方提示（可在瀏覽器網站設定中改為允許）。' }[Notification.permission]
    || '開啟網頁時若自選股出現新訊號，會在網頁上方提示；允許後也會送出系統通知。';
}

// ---- 開啟網頁時提示（對照 App 的 SignalCheckWorker）：自選股最新交易日出現、尚未提示過的訊號 ----
function runCheck(manual) {
  if (!settings.notificationsEnabled) { if (manual) snack('提示已在設定中關閉'); return; }
  const fresh = [];
  for (const code of watchlist) {
    const a = data.candles(code) && analysisFor(code), s = a?.signals.at(-1);
    if (s && s.date === latestDate() && s.index === a.candles.length - 1 && (manual || notified[code] !== s.date)) { fresh.push({ code, s }); notified[code] = s.date; }
  }
  for (const k of Object.keys(notified)) if (!watchlist.includes(k)) delete notified[k];
  store.set('ta-notified', notified);
  if (!fresh.length) { if (manual) snack('自選股最新交易日沒有進出場訊號'); return; }
  systemNotify(fresh);
  const { code, s } = fresh[0], st = stockOf(code);
  document.querySelector('.notif')?.remove(); clearTimeout(notifTimer);
  const el = document.createElement('div');
  el.className = 'notif'; el.setAttribute('role', 'alert');
  el.innerHTML = `<div class="app">${svg('trend')}台股技術分析・${s.date}${fresh.length > 1 ? `・另有 ${fresh.length - 1} 則` : ''}</div>
    <b>${code} ${esc(st.name)}｜${s.side === 'BUY' ? '進場（買進）' : '出場（賣出）'} 訊號</b>
    <span>收盤 ${f2(s.price)}：${esc(s.summary)}</span>`;
  el.onclick = () => { el.remove(); closeDialog(); go('detail', code); focusBar(s.index); };

  document.getElementById('screen').appendChild(el);
  notifTimer = setTimeout(() => el.remove(), 9000);
}
function systemNotify(fresh) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return;
  for (const { code, s } of fresh.slice(0, 5)) {
    try {
      const n = new Notification(`${code} ${stockOf(code).name}｜${s.side === 'BUY' ? '進場（買進）' : '出場（賣出）'} 訊號`,
        { body: `${s.date} 收盤 ${f2(s.price)}：${s.summary}`, tag: `ta-${code}-${s.date}` });
      n.onclick = () => { window.focus(); go('detail', code); focusBar(s.index); n.close(); };
    } catch { /* 部分行動瀏覽器只允許由 Service Worker 送出通知 */ }
  }
}

// ---- 主繪製 ----
let lastScreen = null;
function render() {
  if (state.loading || state.loadError) {
    app.innerHTML = state.loadError
      ? `<div class="error-box">資料讀取失敗：${esc(state.loadError)}<br><button class="btn" id="retry">重試</button></div>`
      : '<div class="progress"></div><p class="loading">讀取上市上櫃股票清單…</p>';
    app.querySelector('#retry')?.addEventListener('click', boot);
    return;
  }
  const keep = lastScreen === state.screen + state.code ? app.querySelector('.body')?.scrollTop : 0;
  lastScreen = state.screen + state.code;
  app.innerHTML = state.screen === 'detail' ? detailView() : state.screen === 'settings' ? settingsView()
    : state.screen === 'screener' ? screenerView() : state.screen === 'search' ? searchView() : watchlistView();
  if (state.screen === 'detail') bindDetail(); else if (state.screen === 'settings') bindSettings();
  else if (state.screen === 'screener') bindScreener(); else if (state.screen === 'search') bindSearch(); else bindWatchlist();
  if (keep) app.querySelector('.body')?.scrollTo(0, keep);
}
window.addEventListener('resize', () => { if (state.screen === 'detail') drawChart(); });
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', drawChart);
new MutationObserver(drawChart).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
document.fonts?.ready.then(() => { if (state.screen === 'detail') drawChart(); });
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeDialog(); });

async function boot() {
  state.loading = true; state.loadError = null; render();
  try {
    await data.init();
  } catch (e) {
    state.loading = false; state.loadError = e.message; render();
    return;
  }
  state.loading = false;
  go(...parseHash(), false);
  await loadWatchlist();
  render();
  setTimeout(() => runCheck(false), 600);
}
boot();
