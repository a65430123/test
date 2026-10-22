// 技術指標與進出場訊號（Android App core 模組的 JS 版本，行為與 Kotlin 單元測試一致）
// 日期一律為 'YYYY-MM-DD' 字串，可直接以字串比較先後。
const TA = (() => {
  const DEFAULTS = { bbPeriod: 20, bbMultiplier: 2, bbBreakoutVolumeRatio: 0, volumeMaShort: 5, volumeMaLong: 20, kdPeriod: 9, kdKSmoothing: 3, kdDSmoothing: 3, kdInitial: 50,
    kdOversold: 20, kdOverbought: 80, macdFast: 12, macdSlow: 26, macdSignal: 9,
    bollingerSignals: true, useBollinger: true, useBollingerBreakout: true, useKd: true, kdMidCross: true, useMacd: true, macdCross: true, macdHistogramTurn: true, minConfirmations: 2, confirmWindow: 3 };

  const INDICATORS = [['BOLLINGER', '布林通道'], ['KD', 'KD'], ['MACD', 'MACD']];
  const bbPullback = p => p.bollingerSignals && p.useBollinger;
  const bbBreakout = p => p.bollingerSignals && p.useBollingerBreakout;
  const macdCrossOn = p => p.useMacd && p.macdCross, macdHistOn = p => p.useMacd && p.macdHistogramTurn;
  function selectedIndicators(p) {
    return INDICATORS.filter(([k]) => k === 'BOLLINGER' ? bbPullback(p) || bbBreakout(p) : k === 'KD' ? p.useKd : macdCrossOn(p) || macdHistOn(p)).map(([k]) => k);
  }
  function requiredConfirmations(p) {
    const n = selectedIndicators(p).length;
    return n <= 1 ? 1 : Math.min(Math.max(1, p.minConfirmations), n);
  }
  // 訊號類型所屬指標；同一指標的多種類型在多選確認時只算一個
  const indicatorOf = source => source.startsWith('BOLLINGER') ? 'BOLLINGER' : source.startsWith('MACD') ? 'MACD' : source;
  const labelOf = k => INDICATORS.find(x => x[0] === k)[1];
  function groupEvents(events) {
    return INDICATORS.map(([k]) => [k, events.filter(e => indicatorOf(e.source) === k)]).filter(([, list]) => list.length);
  }
  function describeSignalMode(p) {
    const sel = selectedIndicators(p), names = sel.map(k => INDICATORS.find(x => x[0] === k)[1]).join('＋'), req = requiredConfirmations(p);
    if (!sel.length) return '未選擇訊號來源';
    if (sel.length === 1) return `只看${names}`;
    return req === 1 ? `${names}：任一觸發` : `${names}：${p.confirmWindow} 日內至少 ${req} 個指標同向`;
  }
  function toggleIndicator(p, k, on) {
    if (k === 'BOLLINGER') return on && !p.useBollinger && !p.useBollingerBreakout
      ? { ...p, bollingerSignals: true, useBollinger: true, useBollingerBreakout: true } : { ...p, bollingerSignals: on };
    if (k === 'KD') return { ...p, useKd: on };
    return on && !p.macdCross && !p.macdHistogramTurn ? { ...p, useMacd: true, macdCross: true, macdHistogramTurn: true } : { ...p, useMacd: on };
  }

  function validate(p) {
    const e = [], inR = (v, a, b) => Number.isFinite(v) && v >= a && v <= b, isInt = Number.isInteger;
    if (!isInt(p.bbPeriod) || !inR(p.bbPeriod, 2, 250)) e.push('布林通道週期需介於 2–250');
    if (!(p.bbMultiplier > 0 && p.bbMultiplier <= 10)) e.push('布林通道標準差倍數需介於 0–10');
    if (!inR(p.bbBreakoutVolumeRatio, 0, 20)) e.push('突破量增倍數需介於 0–20');
    if (![p.volumeMaShort, p.volumeMaLong].every(v => isInt(v) && inR(v, 1, 250))) e.push('成交量均線週期需介於 1–250');
    if (!isInt(p.kdPeriod) || !inR(p.kdPeriod, 2, 250)) e.push('KD RSV 週期需介於 2–250');
    if (!isInt(p.kdKSmoothing) || !inR(p.kdKSmoothing, 1, 50)) e.push('K 值平滑係數需介於 1–50');
    if (!isInt(p.kdDSmoothing) || !inR(p.kdDSmoothing, 1, 50)) e.push('D 值平滑係數需介於 1–50');
    if (!inR(p.kdInitial, 0, 100)) e.push('KD 初始值需介於 0–100');
    if (!inR(p.kdOversold, 0, 100) || !inR(p.kdOverbought, 0, 100)) e.push('KD 超買/超賣門檻需介於 0–100');
    if (p.kdOversold >= p.kdOverbought) e.push('KD 超賣門檻需小於超買門檻');
    if (![p.macdFast, p.macdSlow, p.macdSignal].every(v => isInt(v) && inR(v, 1, 250))) e.push('MACD 週期需介於 1–250');
    if (p.macdFast >= p.macdSlow) e.push('MACD 快線週期需小於慢線週期');
    if (!selectedIndicators(p).length) e.push('至少需選擇一個指標作為訊號來源');
    if (!isInt(p.minConfirmations) || !inR(p.minConfirmations, 1, 3)) e.push('確認指標數需介於 1–3');
    if (!isInt(p.confirmWindow) || !inR(p.confirmWindow, 1, 20)) e.push('確認視窗需介於 1–20 日');
    return e;
  }

  function sma(values, period) {
    const out = new Array(values.length).fill(null); let s = 0;
    for (let i = 0; i < values.length; i++) { s += values[i]; if (i >= period) s -= values[i - period]; if (i >= period - 1) out[i] = s / period; }
    return out;
  }

  function ema(values, period) {
    const out = new Array(values.length).fill(null);
    const start = values.findIndex(v => v != null);
    if (start < 0 || values.length - start < period) return out;
    const alpha = 2 / (period + 1), seed = start + period - 1;
    let prev = 0; for (let i = start; i <= seed; i++) prev += values[i]; prev /= period;
    out[seed] = prev;
    for (let i = seed + 1; i < values.length; i++) { const v = values[i]; if (v == null) continue; prev = alpha * v + (1 - alpha) * prev; out[i] = prev; }
    return out;
  }

  function bollinger(closes, period, mult) {
    const n = closes.length, mid = Array(n).fill(null), up = Array(n).fill(null), lo = Array(n).fill(null);
    for (let i = period - 1; i < n; i++) {
      let s = 0; for (let j = i - period + 1; j <= i; j++) s += closes[j];
      const m = s / period; let sq = 0;
      for (let j = i - period + 1; j <= i; j++) sq += (closes[j] - m) ** 2;
      const sd = Math.sqrt(sq / period);
      mid[i] = m; up[i] = m + mult * sd; lo[i] = m - mult * sd;
    }
    return { middle: mid, upper: up, lower: lo };
  }

  function kd(c, period, ks, ds, init) {
    const n = c.length, k = Array(n).fill(null), d = Array(n).fill(null);
    let pk = init, pd = init;
    for (let i = period - 1; i < n; i++) {
      let hh = -Infinity, ll = Infinity;
      for (let j = i - period + 1; j <= i; j++) { hh = Math.max(hh, c[j].high); ll = Math.min(ll, c[j].low); }
      const rsv = hh === ll ? pk : (c[i].close - ll) / (hh - ll) * 100;
      const kv = pk * (ks - 1) / ks + rsv / ks, dv = pd * (ds - 1) / ds + kv / ds;
      k[i] = kv; d[i] = dv; pk = kv; pd = dv;
    }
    return { k, d };
  }

  function macd(closes, f, s, sig) {
    const ef = ema(closes, f), es = ema(closes, s);
    const dif = closes.map((_, i) => ef[i] != null && es[i] != null ? ef[i] - es[i] : null);
    const signal = ema(dif, sig);
    const hist = closes.map((_, i) => dif[i] != null && signal[i] != null ? dif[i] - signal[i] : null);
    return { dif, signal, histogram: hist };
  }

  const LABEL = { BOLLINGER: '布林通道', KD: 'KD', MACD: 'MACD' };

  // hist[at] 是否為所在同號區段的最高（正）或最低（負）值
  function runExtreme(hist, at, positive) {
    const v = hist[at];
    for (let j = at - 1; j >= 0; j--) {
      const x = hist[j]; if (x == null) break;
      if (positive ? x <= 0 : x >= 0) break;
      if (positive ? x > v : x < v) return false;
    }
    return true;
  }

  // 訊號細分類型（對照 SignalType）；KD 依交叉位置分高低檔與中檔
  const TYPES = [['BOLLINGER_PULLBACK', '布林收回'], ['BOLLINGER_BREAKOUT', '布林突破'], ['KD_ZONE_CROSS', 'KD 高低檔交叉'],
    ['KD_MID_CROSS', 'KD 中檔交叉'], ['MACD_CROSS', 'MACD 交叉'], ['MACD_HISTOGRAM_TURN', 'MACD 柱狀轉折']];
  const TYPE_OF = { BOLLINGER: 'BOLLINGER_PULLBACK', BOLLINGER_BREAKOUT: 'BOLLINGER_BREAKOUT', KD: 'KD_ZONE_CROSS', MACD: 'MACD_CROSS', MACD_HISTOGRAM: 'MACD_HISTOGRAM_TURN' };
  function enabledTypes(p) {
    return [bbPullback(p) && 'BOLLINGER_PULLBACK', bbBreakout(p) && 'BOLLINGER_BREAKOUT', p.useKd && 'KD_ZONE_CROSS',
      p.useKd && p.kdMidCross && 'KD_MID_CROSS', macdCrossOn(p) && 'MACD_CROSS', macdHistOn(p) && 'MACD_HISTOGRAM_TURN'].filter(Boolean);
  }

  function detectEvents(c, bb, k, m, p, volMa) {
    const ev = [];
    for (let i = 1; i < c.length; i++) {
      if (bbPullback(p) && bb.lower[i - 1] != null && bb.lower[i] != null) {
        if (c[i - 1].close < bb.lower[i - 1] && c[i].close >= bb.lower[i]) ev.push({ index: i, side: 'BUY', source: 'BOLLINGER', desc: '布林跌破下軌後收回' });
        else if (c[i - 1].close > bb.upper[i - 1] && c[i].close <= bb.upper[i]) ev.push({ index: i, side: 'SELL', source: 'BOLLINGER', desc: '布林突破上軌後跌回' });
      }
      if (bbBreakout(p) && bb.lower[i - 1] != null && bb.lower[i] != null) {
        const pc = c[i - 1].close, cl = c[i].close;
        let hit = null;
        if (pc <= bb.upper[i - 1] && cl > bb.upper[i]) hit = ['SELL', '突破布林上軌'];
        else if (pc >= bb.lower[i - 1] && cl < bb.lower[i]) hit = ['BUY', '跌破布林下軌'];
        if (hit && p.bbBreakoutVolumeRatio > 0) {
          const avg = volMa[i - 1];
          const ratio = avg > 0 ? c[i].volume / avg : 0;
          hit = ratio >= p.bbBreakoutVolumeRatio ? [hit[0], `${hit[1]}（量 ${ratio.toFixed(1)} 倍）`] : null;
        }
        if (hit) ev.push({ index: i, side: hit[0], source: 'BOLLINGER_BREAKOUT', desc: hit[1] });
      }
      if (p.useKd && k.k[i - 1] != null) {
        const pk = k.k[i - 1], pd = k.d[i - 1], kv = k.k[i], dv = k.d[i];
        if (pk <= pd && kv > dv && pk <= p.kdOversold) ev.push({ index: i, side: 'BUY', source: 'KD', desc: `KD 低檔黃金交叉 (K=${kv.toFixed(1)})` });
        else if (pk >= pd && kv < dv && pk >= p.kdOverbought) ev.push({ index: i, side: 'SELL', source: 'KD', desc: `KD 高檔死亡交叉 (K=${kv.toFixed(1)})` });
        else if (p.kdMidCross && pk > p.kdOversold && pk < p.kdOverbought && pk <= pd && kv > dv) ev.push({ index: i, side: 'BUY', source: 'KD', type: 'KD_MID_CROSS', desc: `KD 黃金交叉 (K=${kv.toFixed(1)})` });
        else if (p.kdMidCross && pk > p.kdOversold && pk < p.kdOverbought && pk >= pd && kv < dv) ev.push({ index: i, side: 'SELL', source: 'KD', type: 'KD_MID_CROSS', desc: `KD 死亡交叉 (K=${kv.toFixed(1)})` });
      }
      if (m.histogram[i - 1] != null && m.histogram[i] != null) {
        const ph = m.histogram[i - 1], h = m.histogram[i];
        if (macdCrossOn(p)) {
          if (ph <= 0 && h > 0) ev.push({ index: i, side: 'BUY', source: 'MACD', desc: 'MACD 黃金交叉' });
          else if (ph >= 0 && h < 0) ev.push({ index: i, side: 'SELL', source: 'MACD', desc: 'MACD 死亡交叉' });
        }
        if (macdHistOn(p)) {
          if (ph > 0 && h < ph && runExtreme(m.histogram, i - 1, true)) ev.push({ index: i, side: 'SELL', source: 'MACD_HISTOGRAM', desc: 'MACD 柱狀高點轉折向下' });
          else if (ph < 0 && h > ph && runExtreme(m.histogram, i - 1, false)) ev.push({ index: i, side: 'BUY', source: 'MACD_HISTOGRAM', desc: 'MACD 柱狀低點轉折向上' });
        }
      }
    }
    ev.forEach(e => { e.type = e.type || TYPE_OF[e.source]; });
    return ev;
  }

  function confirmSignals(c, events, p) {
    const required = requiredConfirmations(p);
    const win = Math.max(1, p.confirmWindow);
    const idxs = [...new Set(events.map(e => e.index))].sort((a, b) => a - b);
    const last = {}, out = [];
    for (const i of idxs) for (const side of ['BUY', 'SELL']) {
      if (!events.some(e => e.index === i && e.side === side)) continue;
      if (last[side] != null && i - last[side] < win) continue;
      const inWin = events.filter(e => e.side === side && e.index >= i - win + 1 && e.index <= i);
      const bySrc = {}; for (const e of inWin) if (!bySrc[e.source] || bySrc[e.source].index < e.index) bySrc[e.source] = e;
      const distinct = Object.values(bySrc).sort((a, b) => a.index - b.index);
      if (new Set(distinct.map(e => indicatorOf(e.source))).size >= required) {
        const groups = groupEvents(distinct);
        out.push({ index: i, date: c[i].date, side, price: c[i].close, events: distinct, groups, types: [...new Set(distinct.map(e => e.type))],
          summary: groups.map(([k, list]) => `${labelOf(k)}：${list.map(e => e.desc).join('、')}`).join('｜') });
        last[side] = i;
      }
    }
    return out;
  }

  function analyze(c, p) {
    const closes = c.map(x => x.close);
    const bb = bollinger(closes, p.bbPeriod, p.bbMultiplier);
    const k = kd(c, p.kdPeriod, p.kdKSmoothing, p.kdDSmoothing, p.kdInitial);
    const m = macd(closes, p.macdFast, p.macdSlow, p.macdSignal);
    const vols = c.map(x => x.volume);
    const volumeMaShort = sma(vols, p.volumeMaShort), volumeMaLong = sma(vols, p.volumeMaLong);
    const events = detectEvents(c, bb, k, m, p, volumeMaShort);
    return { candles: c, bollinger: bb, kd: k, macd: m, volumeMaShort, volumeMaLong, events, signals: confirmSignals(c, events, p) };
  }

  // 除權息向前還原
  function adjust(c, events) {
    if (!c.length || !events.length) return c;
    const lastDate = c[c.length - 1].date;
    const valid = events.filter(e => e.closeBefore > 0 && e.referencePrice > 0 && e.exDate <= lastDate)
      .filter(e => { const f = e.referencePrice / e.closeBefore; return f >= 0.2 && f <= 1.5; })
      .sort((a, b) => a.exDate < b.exDate ? 1 : -1);
    if (!valid.length) return c;
    const out = new Array(c.length); let cum = 1, e = 0;
    for (let i = c.length - 1; i >= 0; i--) {
      while (e < valid.length && c[i].date < valid[e].exDate) { cum *= valid[e].referencePrice / valid[e].closeBefore; e++; }
      const x = c[i];
      out[i] = cum === 1 ? x : { ...x, open: x.open * cum, high: x.high * cum, low: x.low * cum, close: x.close * cum };
    }
    return out;
  }

  // 選股（對照 Screener.kt）：最新交易日出現訊號者，依成交量排序
  const MIN_BARS = 35;
  function screen(stocks, p, latestDate, adjustPrices) {
    const hits = [];
    for (const s of stocks) {
      const raw = s.candles, last = raw.at(-1);
      if (!last || last.date !== latestDate || raw.length < MIN_BARS) continue;
      const c = adjustPrices ? adjust(raw, s.exRights || []) : raw;
      const a = analyze(c, p), sig = a.signals.at(-1);
      if (!sig || sig.index !== c.length - 1) continue;
      hits.push({ code: s.code, signal: sig, close: last.close, prevClose: raw.at(-2)?.close ?? null, volume: last.volume });
    }
    return hits.sort((x, y) => y.volume - x.volume);
  }

  return { DEFAULTS, INDICATORS, TYPES, enabledTypes, screen, detectEvents, validate, analyze, adjust, ema, bollinger, kd, confirmSignals, selectedIndicators, groupEvents, labelOf, requiredConfirmations, describeSignalMode, toggleIndicator };
})();
export default TA;
