// 證交所／櫃買中心回應解析（Android App MarketDataParser 的 JS 版本，以欄位名稱定位，容忍欄位順序與空白變動）

/** 民國日期 "115/10/01"、"115年10月01日" → 'YYYY-MM-DD'；無法解析回傳 null */
export function parseRocDate(text) {
  const parts = String(text).match(/\d+/g);
  if (!parts || parts.length !== 3) return null;
  const [y, m, d] = parts.map(Number);
  if (m < 1 || m > 12 || d < 1 || d > 31) return null;
  return `${y + 1911}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

/** "1,234.50" → 1234.5；"--"、空字串等 → null */
export function parseNumber(text) {
  const s = String(text ?? '').replace(/,/g, '').trim();
  if (!s || /^-+$/.test(s)) return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

const norm = fields => (fields || []).map(f => String(f).replace(/\s+/g, ''));

/** 普通股：4 碼數字（00 開頭為 ETF） */
export const isStock = code => /^\d{4}$/.test(code) && !code.startsWith('00');
/** ETF：00 開頭 4–6 碼，可帶一個英文字尾（0050、006208、00631L、00679B、00400A） */
export const isEtf = code => /^00\d{2,4}[A-Z]?$/.test(code);

/** 多表格回應中，找出欄位包含所有關鍵字且有資料的表格 */
export function pickTable(tables, ...keys) {
  return (tables || []).find(t => {
    const names = norm(t.fields);
    return keys.every(k => names.some(n => n.includes(k))) && (t.data || []).length > 0;
  }) || null;
}

/**
 * 全市場每日收盤行情（證交所 MI_INDEX、櫃買中心 otc）→ [{ code, name, o, h, l, c, v }]，v 為張。
 * 當日無成交（價格為 "--"）者略過。
 */
export function parseDailyQuotes(fields, rows) {
  const names = norm(fields);
  const col = key => names.findIndex(n => n.includes(key));
  const idx = { code: col('代號'), name: col('名稱'), o: col('開盤'), h: col('最高'), l: col('最低'), c: col('收盤'), v: col('成交股數') };
  if (Object.values(idx).some(i => i < 0)) return [];
  const max = Math.max(...Object.values(idx));
  const out = [];
  for (const row of rows || []) {
    if (row.length <= max) continue;
    const code = String(row[idx.code]).trim();
    const o = parseNumber(row[idx.o]), h = parseNumber(row[idx.h]), l = parseNumber(row[idx.l]), c = parseNumber(row[idx.c]);
    if (!code || o == null || h == null || l == null || c == null) continue;
    out.push({ code, name: String(row[idx.name]).trim(), o, h, l, c, v: Math.trunc((parseNumber(row[idx.v]) ?? 0) / 1000) });
  }
  return out;
}

/**
 * 除權除息計算結果表（證交所 TWT49U、櫃買中心 exDailyQ）→ [{ code, date, closeBefore, ref }]。
 * 以欄位名稱定位，找不到時退回證交所固定欄位順序。
 */
export function parseExRights(fields, rows) {
  const names = norm(fields);
  const col = (fallback, match) => { const i = names.findIndex(match); return i >= 0 ? i : fallback; };
  const dI = col(0, n => n.includes('日期')), cI = col(1, n => n.includes('代號'));
  const bI = col(3, n => n.includes('前收盤')), rI = col(4, n => n.includes('參考價') && !n.includes('減除'));
  const max = Math.max(dI, cI, bI, rI);
  const out = [];
  for (const row of rows || []) {
    if (row.length <= max) continue;
    const date = parseRocDate(row[dI]), code = String(row[cI]).trim();
    const closeBefore = parseNumber(row[bI]), ref = parseNumber(row[rI]);
    if (!date || !code || closeBefore == null || ref == null) continue;
    out.push({ code, date, closeBefore, ref });
  }
  return out;
}
