# 台股技術分析 App（Android）

台股（上市＋上櫃）日 K 技術分析：自選股、K 線疊加布林通道、副圖成交量 / KD / MACD、自動判斷進出場訊號並推播。

- Kotlin + Jetpack Compose（Material 3）
- MVVM：`ViewModel` + `StateFlow`，手動 DI（`AppContainer`）
- Room 本地資料庫、Retrofit + OkHttp 抓資料、DataStore 儲存設定、WorkManager 每日檢查訊號

## 模組結構

```
core/   純 Kotlin（JVM）模組：指標計算與訊號引擎，可獨立單元測試
  model/Candle.kt, IndicatorParams.kt
  model/ExRightEvent.kt       除權息事件
  indicator/Indicators.kt     SMA / EMA / 布林通道 / KD / MACD
  PriceAdjuster.kt            除權息向前還原
  signal/SignalEngine.kt      進出場規則與多指標確認
  TechnicalAnalyzer.kt        一次算完所有指標與訊號

app/    Android App
  data/remote    TwseApi（上市）、TpexApi（上櫃）、TwseOpenApi（股票清單）、MarketDataParser
  data/local     Room：stock_info、watchlist、candles、ex_rights
  data/repository/StockRepository   增量下載日 K、節流、加入/移除自選股
  data/settings/SettingsRepository  DataStore 儲存指標參數
  worker/SignalCheckWorker          每日 14:30（台北）更新並推播
  notification/SignalNotifier
  ui/watchlist   自選股清單（下拉更新、長按刪除、搜尋加入）
  ui/detail      個股 K 線圖＋訊號列表
  ui/chart       Compose Canvas 自繪圖表（縮放 / 平移 / 點選）
  ui/settings    指標參數設定頁
```

## 指標算法（預設值，皆可在設定頁調整）

| 指標 | 算法 |
|---|---|
| 布林通道 | 中軌 = 20 日 SMA；上下軌 = 中軌 ± 2 × 20 日母體標準差 |
| 成交量 | 柱狀（紅漲綠跌，單位：張）＋ 5 日、20 日均量 |
| KD | RSV = (C − 9日最低) / (9日最高 − 9日最低) × 100；K = 前日K × 2/3 + RSV × 1/3；D = 前日D × 2/3 + K × 1/3；初始 K、D = 50 |
| MACD | DIF = EMA12 − EMA26；訊號線 = DIF 的 EMA9；OSC = DIF − 訊號線。EMA 以前 N 筆 SMA 為起始值，alpha = 2/(N+1) |

KD 的平滑係數 n 可調（K = 前日K × (n−1)/n + RSV / n，n = 3 即上式）。若 9 日內最高 = 最低，RSV 以前日 K 代入（K 維持不變）。

## 除權息還原價（可開關）

設定頁「價格資料 → 除權息還原價」或個股頁上方的「除權息還原」chip 皆可切換，預設開啟。

- 資料：證交所 TWT49U、櫃買中心 exDailyQ「除權除息計算結果表」（全市場，一次下載一段日期區間）。
- 還原因子 = 除權息參考價 ÷ 除權息前收盤價（現金股利與股票股利皆適用）。
- 向前還原：最新價不變，除權息日**之前**的開高低收乘上其後所有因子的連乘積；成交量不調整。
- 開啟時布林、KD、MACD、訊號與推播都以還原價計算；圖上以「除」虛線標記除權息日（關閉時仍標記，方便辨識缺口）。
- 自選股清單的現價、漲跌永遠顯示原始價。
- 同步策略：首次下載 13 個月，之後每天最多一次，從最後一筆事件日期補到今天（每 3 個月一個請求，與日 K 共用節流）。
- 因子不在 0.2–1.5 之間的資料視為異常而忽略；尚未發生（晚於最新 K 棒）的事件不套用。

## 進出場訊號規則

| 指標 | 買進事件 | 賣出事件 |
|---|---|---|
| 布林收回 | 前日收盤跌破下軌、今日收回下軌之上 | 前日收盤突破上軌、今日跌回上軌之下 |
| 布林突破 | 收盤由下軌之上**跌破下軌**（超賣） | 收盤由上軌之下**突破上軌**（超買） |
| KD | K 上穿 D（黃金交叉）且前日 K ≤ 超賣門檻（20） | K 下穿 D（死亡交叉）且前日 K ≥ 超買門檻（80） |
| MACD | DIF 上穿訊號線（OSC 翻正） | DIF 下穿訊號線（OSC 翻負） |

布林「突破」與「收回」都是逆勢（超買／超賣）訊號：突破／跌破當天先發出，收回當天再發出一次，兩者分開計算、可各自開關。突破訊號可設定「量增倍數」：當日量 ≥ 倍數 × 前一日 5 日均量才成立（預設 0 = 不檢查）。

**確認機制**：某日有事件觸發，且在最近 `確認視窗`（預設 3）個交易日內有至少 `確認指標數`（預設 2）個**不同**指標出現同方向事件，即產生訊號。指標共三個：布林通道（收回與突破合計為一個）、KD、MACD，所以布林的兩種規則不能互相確認；同方向訊號在視窗期內不重複發出。確認指標數設為 1 即「任一指標觸發就發訊號」。

**推播**：WorkManager 每日台北時間 14:30 後執行，更新所有自選股；若「最新一根 K 棒」產生訊號且該日尚未推播過，就發通知（點通知直接開該股圖表）。設定頁可「立即檢查」。

## 資料來源

| 用途 | 端點 |
|---|---|
| 上市日 K（每次一個月） | `https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY?date=yyyyMMdd&stockNo=XXXX&response=json` |
| 上櫃日 K（每次一個月） | `https://www.tpex.org.tw/www/zh-tw/afterTrading/tradingStock?code=XXXX&date=yyyy/MM/dd&response=json` |
| 上市除權息 | `https://www.twse.com.tw/rwd/zh/exRight/TWT49U?startDate=yyyyMMdd&endDate=yyyyMMdd&response=json` |
| 上櫃除權息 | `https://www.tpex.org.tw/www/zh-tw/bulletin/exDailyQ?startDate=yyyy/MM/dd&endDate=yyyy/MM/dd&response=json` |
| 上市股票清單 | `https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL` |
| 上櫃股票清單 | `https://www.tpex.org.tw/openapi/v1/tpex_mainboard_daily_close_quotes` |

- 首次加入自選股會下載 12 個月資料，之後只補最後一筆所在月份到本月。
- 證交所會封鎖短時間大量請求，所有月資料請求以 1.5 秒間隔節流（首次下載一檔約 18 秒）。
- 成交量統一以「張」儲存（證交所回傳股數，已除以 1000）。
- 顏色採台股慣例：紅漲綠跌。

## 建置

需求：Android Studio（Ladybug 以上）或 Android SDK 35 + JDK 17+。

```bash
./gradlew :core:test          # 指標與訊號單元測試（純 JVM）
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## 已知限制 / 後續可做

- 還原價未處理減資、分割等非除權息的股本變動。
- 股票清單只在本地為空時下載一次。
- 端點格式為政府網站公開資料，若官方改版需調整 `MarketDataParser` / DTO。
- 部分舊 Android 裝置可能缺少 TWCA 根憑證，連線證交所會出現 SSL 錯誤，必要時可加入 `network_security_config` 信任該 CA。

> 本 App 訊號僅為技術指標之機械式判斷，不構成任何投資建議。
