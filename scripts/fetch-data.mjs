#!/usr/bin/env node
// 每日資料流程：下載上市＋上櫃全市場日 K 與除權息，產生網頁版讀取的靜態 JSON。
//
//   node scripts/fetch-data.mjs --store <原始資料目錄> --out <網站 data 目錄> [--days 250]
//
// - 原始資料（store）：每個市場每個交易日一個檔案 days/<TWSE|TPEX>-YYYYMMDD.json，以及休市日、除權息；
//   於 GitHub Actions 中存放在 data 分支，每次只補下載缺少的日子。
// - 輸出（out）：meta.json、stocks.json（代號／名稱／市場／最新收盤）、exrights.json、screen.json（選股用最近 60 日）、candles/<代號>.json。
import { mkdirSync, readFileSync, writeFileSync, readdirSync, existsSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { parseDailyQuotes, parseExRights, pickTable, isStock, isEtf } from './lib/parse.mjs';

const args = Object.fromEntries(process.argv.slice(2).reduce((a, v, i, arr) => (v.startsWith('--') ? [...a, [v.slice(2), arr[i + 1]]] : a), []));
const STORE = args.store || 'store';
const OUT = args.out || 'site/data';
const TRADING_DAYS = Number(args.days || 250); // 個股圖表約 12 個月
const SCREEN_DAYS = 60; // 選股掃描用
const EX_RIGHT_DAYS = 400;
const UA = 'Mozilla/5.0 (compatible; TwStockTA-web/1.0; +https://github.com/)';

const MARKETS = {
  // 證交所約每 5 秒 3 個請求即可能暫時封鎖 IP
  TWSE: {
    interval: 2000,
    quotes: d => `https://www.twse.com.tw/rwd/zh/afterTrading/MI_INDEX?date=${ymd(d)}&type=ALLBUT0999&response=json`,
    exRights: (a, b) => `https://www.twse.com.tw/rwd/zh/exRight/TWT49U?startDate=${ymd(a)}&endDate=${ymd(b)}&response=json`,
    quoteTable: r => pickTable(r.tables, '代號', '收盤') || { fields: r.fields, data: r.data },
    exTable: r => (String(r.stat).toUpperCase() === 'OK' ? r : null),
  },
  TPEX: {
    interval: 800,
    quotes: d => `https://www.tpex.org.tw/www/zh-tw/afterTrading/otc?date=${slash(d)}&type=EW&response=json`,
    exRights: (a, b) => `https://www.tpex.org.tw/www/zh-tw/bulletin/exDailyQ?startDate=${slash(a)}&endDate=${slash(b)}&response=json`,
    quoteTable: r => pickTable(r.tables, '代號', '收盤'),
    exTable: r => r.tables?.[0] || null,
  },
};

// ---- 日期工具（台北時間） ----
const taipeiToday = () => new Date(Date.now() + 8 * 3600e3).toISOString().slice(0, 10);
const addDays = (iso, n) => new Date(Date.parse(iso + 'T00:00:00Z') + n * 86400e3).toISOString().slice(0, 10);
const weekday = iso => new Date(iso + 'T00:00:00Z').getUTCDay();
const ymd = iso => iso.replace(/-/g, '');
const slash = iso => iso.replace(/-/g, '/');
const sleep = ms => new Promise(r => setTimeout(r, ms));

// ---- 節流＋重試的 JSON 下載 ----
const lastRequest = {};
async function getJson(market, url) {
  for (let attempt = 1; ; attempt++) {
    const wait = (lastRequest[market] || 0) + MARKETS[market].interval - Date.now();
    if (wait > 0) await sleep(wait);
    lastRequest[market] = Date.now();
    try {
      const res = await fetch(url, { headers: { 'User-Agent': UA, Accept: 'application/json' }, signal: AbortSignal.timeout(60_000) });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return JSON.parse(await res.text());
    } catch (e) {
      if (attempt >= 4) throw new Error(`${url} → ${e.message}`);
      console.warn(`  retry ${attempt} ${market}: ${e.message}`);
      await sleep(5000 * attempt);
    }
  }
}

const readJson = (p, fallback) => (existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')) : fallback);
const writeJson = (p, v) => writeFileSync(p, JSON.stringify(v));

// ---- 1. 全市場日 K：由今天往回補齊 TRADING_DAYS 個交易日 ----
async function syncDays(market, holidays) {
  const dir = join(STORE, 'days');
  const today = taipeiToday();
  let trading = 0, fetched = 0, date = today;
  const oldest = [];
  for (let i = 0; trading < TRADING_DAYS && i < TRADING_DAYS * 2; i++, date = addDays(date, -1)) {
    if (weekday(date) === 0 || weekday(date) === 6) continue;
    const file = join(dir, `${market}-${ymd(date)}.json`);
    if (existsSync(file)) { trading++; oldest[0] = date; continue; }
    if (holidays.has(date)) continue;
    let quotes = [];
    try {
      const r = await getJson(market, MARKETS[market].quotes(date));
      const t = MARKETS[market].quoteTable(r);
      quotes = t ? parseDailyQuotes(t.fields, t.data).filter(q => isStock(q.code) || isEtf(q.code)) : [];
    } catch (e) {
      console.warn(`  ${market} ${date} 失敗，下次再試：${e.message}`);
      continue;
    }
    fetched++;
    if (quotes.length) {
      writeJson(file, quotes.map(q => [q.code, q.name, q.o, q.h, q.l, q.c, q.v]));
      trading++; oldest[0] = date;
    } else if (date < today) {
      holidays.add(date); // 過去日期查無資料 → 休市
    }
    if (fetched % 20 === 0) console.log(`  ${market} 已下載 ${fetched} 日（目前 ${date}）`);
  }
  // 刪除超出保留範圍的舊檔
  for (const f of readdirSync(dir)) {
    const m = f.match(new RegExp(`^${market}-(\\d{8})\\.json$`));
    if (m && oldest[0] && m[1] < ymd(oldest[0])) rmSync(join(dir, f));
  }
  console.log(`${market}: 新下載 ${fetched} 日，共 ${trading} 個交易日`);
}

// ---- 2. 除權息：從上次同步日補到今天（3 個月一個請求） ----
async function syncExRights(store) {
  const today = taipeiToday();
  for (const market of Object.keys(MARKETS)) {
    let from = store.syncedThrough?.[market] ? addDays(store.syncedThrough[market], -7) : addDays(today, -EX_RIGHT_DAYS);
    while (from <= today) {
      let to = addDays(from, 91);
      if (to > today) to = today;
      try {
        const t = MARKETS[market].exTable(await getJson(market, MARKETS[market].exRights(from, to)));
        for (const e of t ? parseExRights(t.fields, t.data) : []) store.events[`${e.code}|${e.date}`] = [e.code, e.date, e.closeBefore, e.ref];
      } catch (e) {
        console.warn(`  除權息 ${market} ${from}~${to} 失敗：${e.message}`);
        break;
      }
      store.syncedThrough = { ...store.syncedThrough, [market]: to };
      from = addDays(to, 1);
    }
  }
  const cutoff = addDays(today, -EX_RIGHT_DAYS);
  for (const [k, v] of Object.entries(store.events)) if (v[1] < cutoff) delete store.events[k];
  console.log(`除權息：共 ${Object.keys(store.events).length} 筆`);
}

// ---- 3. 產生網站資料 ----
function build(exStore) {
  const dir = join(STORE, 'days');
  const files = readdirSync(dir).filter(f => /^(TWSE|TPEX)-\d{8}\.json$/.test(f)).sort((a, b) => a.slice(5).localeCompare(b.slice(5)));
  const series = new Map(); // code → { market, name, rows: [[date,o,h,l,c,v]] }
  const dates = new Set();
  for (const f of files) {
    const market = f.slice(0, 4), d = f.slice(5, 13), date = `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}`;
    dates.add(date);
    for (const [code, name, o, h, l, c, v] of readJson(join(dir, f), [])) {
      let s = series.get(code);
      if (!s) series.set(code, (s = { market, name, rows: [] }));
      s.name = name; s.market = market; // 以最新一日為準
      s.rows.push([date, o, h, l, c, v]);
    }
  }
  const allDates = [...dates].sort();
  const latestDate = allDates.at(-1);
  if (!latestDate) throw new Error('沒有任何行情資料');

  mkdirSync(join(OUT, 'candles'), { recursive: true });
  for (const f of readdirSync(join(OUT, 'candles'))) rmSync(join(OUT, 'candles', f));

  const stocks = [];
  const screenDates = allDates.slice(-SCREEN_DAYS);
  const screenIndex = new Map(screenDates.map((d, i) => [d, i]));
  const screen = { dates: screenDates, stocks: {} };
  for (const [code, s] of [...series.entries()].sort()) {
    const last = s.rows.at(-1), prev = s.rows.at(-2);
    stocks.push([code, s.name, s.market, last[0], last[4], prev ? prev[4] : null]); // 代號、名稱、市場、最後交易日、收盤、前一日收盤
    const col = i => s.rows.map(r => r[i]);
    writeJson(join(OUT, 'candles', `${code}.json`), { d: col(0), o: col(1), h: col(2), l: col(3), c: col(4), v: col(5) });
    const recent = s.rows.filter(r => screenIndex.has(r[0]));
    if (recent.length) {
      const arr = () => Array(screenDates.length).fill(null);
      const x = { o: arr(), h: arr(), l: arr(), c: arr(), v: arr() };
      for (const [d, o, h, l, c, v] of recent) {
        const i = screenIndex.get(d);
        x.o[i] = o; x.h[i] = h; x.l[i] = l; x.c[i] = c; x.v[i] = v;
      }
      screen.stocks[code] = x;
    }
  }
  const ex = {};
  for (const [code, date, closeBefore, ref] of Object.values(exStore.events).sort((a, b) => a[1].localeCompare(b[1]))) {
    (ex[code] ||= []).push([date, closeBefore, ref]);
  }
  writeJson(join(OUT, 'stocks.json'), stocks);
  writeJson(join(OUT, 'screen.json'), screen);
  writeJson(join(OUT, 'exrights.json'), ex);
  writeJson(join(OUT, 'meta.json'), {
    generatedAt: new Date().toISOString(), latestDate, tradingDays: allDates.length, stocks: stocks.length,
  });
  console.log(`輸出：${stocks.length} 檔，${allDates.length} 個交易日，最新 ${latestDate}`);
}

async function main() {
  mkdirSync(join(STORE, 'days'), { recursive: true });
  mkdirSync(OUT, { recursive: true });
  const holidaysFile = join(STORE, 'holidays.json');
  const holidayStore = readJson(holidaysFile, {});
  const exFile = join(STORE, 'exrights.json');
  const exStore = readJson(exFile, { syncedThrough: {}, events: {} });

  if (!args['build-only']) {
    // 上市、上櫃為不同主機、各自節流，同時下載
    await Promise.all(Object.keys(MARKETS).map(async m => {
      const set = new Set(holidayStore[m] || []);
      await syncDays(m, set);
      holidayStore[m] = [...set].sort().slice(-200);
    }));
    writeJson(holidaysFile, holidayStore);
    await syncExRights(exStore);
    writeJson(exFile, exStore);
  }
  build(exStore);
}

main().catch(e => { console.error(e); process.exit(1); });
