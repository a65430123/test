import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { parseRocDate, parseNumber, parseDailyQuotes, parseExRights, pickTable, isStock, isEtf } from '../lib/parse.mjs';

// 樣本裁切自 2026-10 證交所／櫃買中心的真實回應
const fixture = name => JSON.parse(readFileSync(new URL(`./fixtures/${name}`, import.meta.url)));

test('日期與數字', () => {
  assert.equal(parseRocDate('115/10/01'), '2026-10-01');
  assert.equal(parseRocDate('113年06月13日'), '2024-06-13');
  assert.equal(parseRocDate('115/10/01＊'), '2026-10-01');
  assert.equal(parseRocDate('abc'), null);
  assert.equal(parseNumber('1,234.50'), 1234.5);
  assert.equal(parseNumber('--'), null);
  assert.equal(parseNumber(''), null);
});

test('代號分類', () => {
  for (const c of ['2330', '6488']) assert.ok(isStock(c));
  for (const c of ['0050', '00878', '006208', '00631L', '00679B', '00400A']) assert.ok(isEtf(c), c);
  for (const c of ['01001T', '020000', '0050AB']) assert.ok(!isStock(c) && !isEtf(c), c);
});

test('證交所 MI_INDEX：挑出每日收盤行情表並解析', () => {
  const r = fixture('twse_mi_index.json');
  const t = pickTable(r.tables, '代號', '收盤');
  assert.match(t.title, /每日收盤行情/);
  const q = parseDailyQuotes(t.fields, t.data);
  const tsmc = q.find(x => x.code === '2330');
  assert.deepEqual(tsmc, { code: '2330', name: '台積電', o: 2400, h: 2415, l: 2395, c: 2405, v: 22402 });
  assert.ok(q.some(x => x.code === '00631L'));
});

test('櫃買 otc：欄位名稱含空白也能解析', () => {
  const r = fixture('tpex_otc.json');
  const t = pickTable(r.tables, '代號', '收盤');
  const q = parseDailyQuotes(t.fields, t.data);
  assert.deepEqual(q.find(x => x.code === '6488'), { code: '6488', name: '環球晶', o: 865, h: 890, l: 839, c: 844, v: 10599 });
});

test('除權息：證交所 TWT49U、櫃買 exDailyQ', () => {
  const a = fixture('twse_twt49u.json');
  const ex1 = parseExRights(a.fields, a.data);
  assert.equal(ex1.length, 3);
  assert.deepEqual(ex1[0], { code: '2612', date: '2026-06-01', closeBefore: 57.7, ref: 55.5 });
  const b = fixture('tpex_exdailyq.json').tables[0];
  const ex2 = parseExRights(b.fields, b.data);
  assert.deepEqual(ex2[0], { code: '3709', date: '2026-06-01', closeBefore: 88.8, ref: 86.35 });
});
