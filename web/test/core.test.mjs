import { test } from 'node:test';
import TA from '../js/core.js';

// 與 Android App core 模組的 Kotlin 單元測試對照（相同輸入、相同預期）

const near = (a, b, e = 1e-9) => { if (Math.abs(a - b) > e) throw new Error(`${a} != ${b}`); };

test('指標數值與除權息還原（對照 IndicatorsTest、PriceAdjusterTest）', () => {
const e = TA.ema([1,2,3,4,5], 3); near(e[2], 2); near(e[3], 3); near(e[4], 4);
const b = TA.bollinger([...Array(20)].map((_, i) => i + 1), 20, 2); near(b.middle[19], 10.5); near(b.upper[19], 10.5 + 2 * Math.sqrt(399/12));
const c = [...Array(9)].map((_, i) => ({ high: 20, low: 10, close: i === 8 ? 18 : 15 }));
const k = TA.kd(c, 9, 3, 3, 50); near(k.k[8], 50*2/3 + 80/3);
const closes = [...Array(30)].map((_, i) => 100 + (i % 2 ? -1 : 1)).concat([97,93,89,85,82,80,79,86,90,93]);
const cs = closes.map((v, i) => ({ date: `d${String(i).padStart(3,'0')}`, open: v, high: v+1, low: v-1, close: v }));
const r = TA.analyze(cs, TA.DEFAULTS);
if (!r.signals.some(s => s.side === 'BUY' && s.events.some(x => x.source === 'KD'))) throw new Error('no buy');
const adj = TA.adjust(cs.slice(0,4), [{ exDate: 'd002', closeBefore: 100, referencePrice: 97 }]); near(adj[0].close, 101*0.97); near(adj[2].close, 101);
});

test('對照 BollingerBreakoutTest', () => {
const ser = (last, lv = 1000) => [...Array(25)].map((_, i) => 100 + (i % 2 ? -0.5 : 0.5)).concat([last])
  .map((v, i, a) => ({ date: `d${String(i).padStart(3, '0')}`, open: v, high: v + .2, low: v - .2, close: v, volume: i === a.length - 1 ? lv : 1000 }));
const only = { ...TA.DEFAULTS, useBollinger: false, useKd: false, useMacd: false, minConfirmations: 1 };
const bo = (c, p) => TA.analyze(c, p).events.filter(e => e.source === 'BOLLINGER_BREAKOUT');
let ev = bo(ser(104), only); if (ev.length !== 1 || ev[0].side !== 'SELL' || ev[0].index !== 25) throw new Error('breakout sell');
ev = bo(ser(96), only); if (ev[0].side !== 'BUY' || ev[0].desc !== '跌破布林下軌') throw new Error('breakdown buy');
if (bo(ser(100.3), only).length) throw new Error('inside');
if (bo(ser(104, 1200), { ...only, bbBreakoutVolumeRatio: 1.5 }).length) throw new Error('vol filter');
if (bo(ser(104, 2000), { ...only, bbBreakoutVolumeRatio: 1.5 })[0].desc !== '突破布林上軌（量 2.0 倍）') throw new Error('vol desc');
const va = TA.analyze(ser(100, 6000), TA.DEFAULTS); near(va.volumeMaShort[25], 2000); near(va.volumeMaLong[25], 1250);
if (!TA.validate({ ...TA.DEFAULTS, minConfirmations: 4 }).length) throw new Error('4 should be invalid');
});

test('對照 SignalModeTest', () => {
const cc = [...Array(20)].map((_, i) => ({ date: `d${String(i).padStart(3, '0')}`, close: 10 }));
const E = (i, source) => ({ index: i, side: 'BUY', source, desc: source });
const evs = [E(2, 'KD'), E(6, 'MACD'), E(10, 'BOLLINGER_BREAKOUT'), E(11, 'BOLLINGER'), E(15, 'KD'), E(16, 'MACD')];
const idx = (e, p) => TA.confirmSignals(cc, e, p).map(s => s.index).join();
const kdOnly = { ...TA.DEFAULTS, bollingerSignals: false, useMacd: false }, bbOnly = { ...TA.DEFAULTS, useKd: false, useMacd: false };
if (idx(evs.filter(e => e.source === 'KD'), kdOnly) !== '2,15') throw new Error('kd only');
if (idx(evs.filter(e => e.source.startsWith('BOLLINGER')), bbOnly) !== '10') throw new Error('bb only');
if (idx(evs, TA.DEFAULTS) !== '16') throw new Error('multi 2 counts indicators');
const same = TA.confirmSignals(cc, [E(10, 'BOLLINGER_BREAKOUT'), E(10, 'BOLLINGER')], bbOnly);
if (same.length !== 1 || same[0].events.length !== 2 || same[0].groups.map(g => g[0]).join() !== 'BOLLINGER') throw new Error('bb same day merge');
const grp = TA.confirmSignals(cc, [E(4, 'MACD_HISTOGRAM'), E(5, 'KD'), E(5, 'BOLLINGER_BREAKOUT')], TA.DEFAULTS)[0];
if (grp.summary !== '布林通道：BOLLINGER_BREAKOUT｜KD：KD｜MACD：MACD_HISTOGRAM') throw new Error('summary ' + grp.summary);
if (idx(evs, { ...TA.DEFAULTS, minConfirmations: 1 }) !== '2,6,10,15') throw new Error('multi any');
if (TA.requiredConfirmations({ ...TA.DEFAULTS, bollingerSignals: false, minConfirmations: 3 }) !== 2) throw new Error('clamp');
if (TA.describeSignalMode(kdOnly) !== '只看KD' || TA.describeSignalMode(TA.DEFAULTS) !== '布林通道＋KD＋MACD：3 日內至少 2 個指標同向'
  || TA.describeSignalMode({ ...TA.DEFAULTS, bollingerSignals: false, minConfirmations: 1 }) !== 'KD＋MACD：任一觸發') throw new Error('describe');
const back = TA.toggleIndicator({ ...TA.DEFAULTS, useBollinger: false, useBollingerBreakout: false }, 'BOLLINGER', true);
if (!back.useBollinger || !back.useBollingerBreakout) throw new Error('toggle');
});

test('對照 KdMacdRulesTest', () => {
{
  const n = len => [...Array(len)].map((_, i) => ({ date: 'd' + i, close: 10 }));
  const nul = len => Array(len).fill(null);
  const bbN = len => ({ middle: nul(len), upper: nul(len), lower: nul(len) });
  const kdE = (k, d, p = TA.DEFAULTS) => TA.detectEvents(n(k.length), bbN(k.length), { k, d }, { histogram: nul(k.length) }, p, nul(k.length)).map(e => e.side + ':' + e.desc).join('|');
  const hE = (h, p = TA.DEFAULTS) => TA.detectEvents(n(h.length), bbN(h.length), { k: nul(h.length), d: nul(h.length) }, { histogram: h }, p, nul(h.length))
    .filter(e => e.source === 'MACD_HISTOGRAM').map(e => e.index + e.side).join();
  const eq = (a, b, m) => { if (a !== b) throw new Error(`${m}: ${a} != ${b}`); };
  eq(kdE([45, 55, 60, 50], [50, 50, 55, 55]), 'BUY:KD 黃金交叉 (K=55.0)|SELL:KD 死亡交叉 (K=50.0)', 'kd mid');
  eq(kdE([15, 25], [20, 20]), 'BUY:KD 低檔黃金交叉 (K=25.0)', 'kd low');
  eq(kdE([90, 82], [85, 86]), 'SELL:KD 高檔死亡交叉 (K=82.0)', 'kd high');
  eq(kdE([85, 90], [88, 88]) + kdE([18, 12], [16, 15]), '', 'kd wrong zone');
  eq(kdE([45, 55], [50, 50], { ...TA.DEFAULTS, kdMidCross: false }), '', 'kd mid off');
  eq(hE([null, 0.5, 1.2, 2.0, 1.6, 1.1, 1.4, 1.0]), '4SELL', 'hist high');
  eq(hE([null, -1.0, 0.5]), '2BUY', 'hist across zero');
  eq(hE([0.5, 2.0, 1.5, 2.5, 2.2]), '2SELL,4SELL', 'hist new high');
  eq(hE([null, -0.4, -1.5, -2.1, -1.7, -1.9, -1.2]), '4BUY', 'hist low');
  eq(hE([1, 2, 1], { ...TA.DEFAULTS, macdHistogramTurn: false }), '', 'hist off');
  const back = TA.toggleIndicator({ ...TA.DEFAULTS, macdCross: false, macdHistogramTurn: false }, 'MACD', true);
  eq(back.macdCross && back.macdHistogramTurn, true, 'toggle macd');
  eq(TA.validate({ ...TA.DEFAULTS, minConfirmations: 5 }).length + ':' + (TA.validate({ ...TA.DEFAULTS, minConfirmations: 4 }).length > 0), '1:true', 'validate 4');
}
});

test('對照 ScreenerTest', () => {
{
  const eq = (a, b, m) => { if (a !== b) throw new Error(`${m}: ${a} != ${b}`); };
  const ser = (closes, vol = 1000, off = 0) => closes.map((v, i) => ({ date: 'q' + String(i + off).padStart(3, '0'), open: v, high: v + 1, low: v - 1, close: v, volume: vol }));
  const rebound = [...Array(30)].map((_, i) => 100 + (i % 2 ? -1 : 1)).concat([97, 93, 89, 85, 82, 80, 79, 86]);
  const latest = 'q' + String(rebound.length - 1).padStart(3, '0');
  const hits = TA.screen([{ code: 'A', candles: ser(rebound, 500) }, { code: 'B', candles: ser(rebound.map(() => 100)) }, { code: 'C', candles: ser(rebound, 2000) }], TA.DEFAULTS, latest, false);
  eq(hits.map(h => h.code).join(), 'C,A', 'order'); eq(hits[0].close, 86, 'close'); eq(hits[0].prevClose, 79, 'prev');
  eq(TA.screen([{ code: 'S', candles: ser(rebound, 1000, -1) }, { code: 'T', candles: ser(rebound.slice(-20), 1000, rebound.length - 20) }], TA.DEFAULTS, latest, false).length, 0, 'stale/short');
  const closes = [...Array(40)].map((_, i) => 100 + (i % 2 ? -0.5 : 0.5)).concat([92]), c = ser(closes), last = c.at(-1).date;
  const p = { ...TA.DEFAULTS, useKd: false, useMacd: false };
  eq(TA.screen([{ code: 'X', candles: c }], p, last, false).length, 1, 'raw');
  eq(TA.screen([{ code: 'X', candles: c, exRights: [{ exDate: last, closeBefore: 99.5, referencePrice: 92 }] }], p, last, true).length, 0, 'adjusted');
}
});

test('對照 SignalType 測試', () => {
{
  const eq = (a, b, m) => { if (a !== b) throw new Error(`${m}: ${a} != ${b}`); };
  const n = len => [...Array(len)].map((_, i) => ({ date: 't' + i, close: 10 })), nul = len => Array(len).fill(null);
  const bbN = len => ({ middle: nul(len), upper: nul(len), lower: nul(len) });
  const kdT = (k, d) => TA.detectEvents(n(k.length), bbN(k.length), { k, d }, { histogram: nul(k.length) }, TA.DEFAULTS, nul(k.length)).map(e => e.type).join();
  eq(kdT([45, 55, 60, 50], [50, 50, 55, 55]), 'KD_MID_CROSS,KD_MID_CROSS', 'kd mid type');
  eq(kdT([15, 25], [20, 20]), 'KD_ZONE_CROSS', 'kd zone type');
  eq(TA.enabledTypes(TA.DEFAULTS).length, 6, 'all types');
  eq(TA.enabledTypes({ ...TA.DEFAULTS, useBollingerBreakout: false, kdMidCross: false, macdHistogramTurn: false }).join(), 'BOLLINGER_PULLBACK,KD_ZONE_CROSS,MACD_CROSS', 'enabled');
  eq(TA.enabledTypes({ ...TA.DEFAULTS, bollingerSignals: false, useMacd: false }).join(), 'KD_ZONE_CROSS,KD_MID_CROSS', 'kd only');
}
});
