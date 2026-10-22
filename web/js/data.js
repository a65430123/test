// 讀取每日資料流程（scripts/fetch-data.mjs）產生的靜態 JSON。
// 清單、除權息在啟動時載入；個股日 K 與選股資料用到時才下載並快取。
const BASE = 'data/';
const MARKET_LABEL = { TWSE: '上市', TPEX: '上櫃' };

async function getJson(path, fresh) {
  const res = await fetch(BASE + path, { cache: fresh ? 'no-cache' : 'default' });
  if (!res.ok) throw new Error(`${path}：HTTP ${res.status}`);
  return res.json();
}

const toCandles = x => x.d.map((date, i) => ({ date, open: x.o[i], high: x.h[i], low: x.l[i], close: x.c[i], volume: x.v[i] }));

export function createData() {
  let meta = null, stocks = new Map(), list = [], exRights = {};
  const candles = new Map(), pending = new Map();
  let screen = null, screenPending = null;

  // 同一份資料只下載一次；資料日期變動時以 ?v= 避開瀏覽器快取
  const version = () => (meta ? `?v=${meta.latestDate}` : '');
  function once(key, load) {
    if (!pending.has(key)) pending.set(key, load().finally(() => pending.delete(key)));
    return pending.get(key);
  }

  return {
    get meta() { return meta; },
    get list() { return list; },
    stock: code => stocks.get(code),

    /** 載入（或重新載入）清單；回傳資料日期是否變更。 */
    async init(fresh = false) {
      const m = await getJson('meta.json', true);
      const changed = !meta || m.latestDate !== meta.latestDate || m.generatedAt !== meta.generatedAt;
      if (!changed && !fresh) return false;
      meta = m;
      const [rows, ex] = await Promise.all([getJson('stocks.json' + version(), fresh), getJson('exrights.json' + version(), fresh)]);
      list = rows.map(([code, name, market, lastDate, close, prevClose]) => ({ code, name, market: MARKET_LABEL[market] || market, lastDate, close, prevClose }));
      stocks = new Map(list.map(s => [s.code, s]));
      exRights = ex;
      if (changed) { candles.clear(); screen = null; }
      return changed;
    },

    exRightsOf: code => (exRights[code] || []).map(([exDate, closeBefore, referencePrice]) => ({ exDate, closeBefore, referencePrice })),

    /** 已載入的日 K；尚未載入回傳 undefined。 */
    candles: code => candles.get(code),
    loadCandles(code) {
      if (candles.has(code)) return Promise.resolve(candles.get(code));
      return once('c:' + code, async () => {
        const c = toCandles(await getJson(`candles/${encodeURIComponent(code)}.json${version()}`));
        candles.set(code, c);
        return c;
      });
    },

    /** 選股用：最近 60 個交易日的全市場資料，轉為 TA.screen 需要的格式。 */
    get screen() { return screen; },
    loadScreen() {
      if (screen) return Promise.resolve(screen);
      return (screenPending ||= getJson('screen.json' + version()).then(raw => {
        const all = [];
        for (const [code, x] of Object.entries(raw.stocks)) {
          const c = [];
          raw.dates.forEach((date, i) => { if (x.c[i] != null) c.push({ date, open: x.o[i], high: x.h[i], low: x.l[i], close: x.c[i], volume: x.v[i] }); });
          all.push({ code, candles: c, exRights: this.exRightsOf(code) });
        }
        screen = { latestDate: raw.dates.at(-1), stocks: all };
        return screen;
      }).finally(() => { screenPending = null; }));
    },
  };
}
