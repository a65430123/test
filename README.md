# 台股技術分析 App（Android）

台股（上市＋上櫃）日 K 技術分析：自選股、K 線疊加布林通道、副圖成交量 / KD / MACD、自動判斷進出場訊號並推播。

- Kotlin + Jetpack Compose（Material 3）
- MVVM：`ViewModel` + `StateFlow`，手動 DI（`AppContainer`）
- Room 本地資料庫、Retrofit + OkHttp 抓資料、DataStore 儲存設定、WorkManager 每日檢查訊號

## 網頁版

同樣的功能也有網頁版（`web/`），部署在 GitHub Pages：<https://a65430123.github.io/test/>

- 畫面與流程對照 App：選股、自選股、搜尋、個股圖表（K 線＋布林、成交量、KD、MACD）、設定、深色介面。
- 指標與訊號：`web/js/core.js`，與 `core` 模組相同的演算法與預設值（`web/test/core.test.mjs`）。
- 自選股、設定只存在瀏覽器（localStorage）。開啟網頁時，自選股最新交易日若有新訊號，會在網頁上方提示；在設定頁允許瀏覽器通知後也會送出系統通知。
- 網址：`#/screener`、`#/watchlist`、`#/search`、`#/settings`、`#/stock/2330`（可加入書籤或分享）。

證交所以外的資料來源（櫃買中心）不允許瀏覽器跨網域讀取，所以網頁版不直接連交易所，而是由 GitHub Actions（`.github/workflows/pages.yml`）每個交易日 16:30、19:30（台北時間）執行 `scripts/fetch-data.mjs`：

1. 下載上市（MI_INDEX）＋上櫃（otc）全市場日 K，保留最近 250 個交易日；除權息（TWT49U、exDailyQ）保留約 13 個月。已下載的日子存在 `data` 分支，每次只補缺少的日子（第一次約 15 分鐘）。
2. 產生 `data/meta.json`、`stocks.json`（清單與最新收盤）、`candles/<代號>.json`（個股日 K）、`screen.json`（選股用最近 60 個交易日）、`exrights.json`。
3. 與 `web/` 一起部署到 GitHub Pages。

首次使用：Settings → Pages → Build and deployment → Source 選「GitHub Actions」，再到 Actions 手動執行「Web (daily data + Pages)」。

本機試跑：

```bash
node scripts/fetch-data.mjs --store store --out site/data --days 40   # 下載 40 個交易日
cp -r web/index.html web/css web/js site/ && python3 -m http.server -d site 8000
node --test web/test/*.test.mjs scripts/test/*.test.mjs
```

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

## 搜尋個股

選股與自選股頁右上角的搜尋按鈕：輸入**代號或名稱**（代號比對開頭、名稱比對任意位置，全形字與空白會自動轉換），列出上市、上櫃股票與 ETF，並顯示最新收盤與漲跌幅（選股資料已下載時）。點選開啟個股頁，可再加入自選股；鍵盤「搜尋」直接開啟第一筆。清單中沒有的完整代號（例如剛上市）可「查詢代號」直接向證交所／櫃買中心查詢。

## 選股

底部導覽（選股｜自選股）的「選股」頁：掃描上市＋上櫃全部股票，列出**最新交易日**出現進出場訊號的股票。

- 判斷方式與個股頁相同：依目前的訊號來源、確認方式、細項開關與除權息還原設定。
- 資料：證交所 MI_INDEX、櫃買中心上櫃股票行情的「全市場每日收盤行情」，每個交易日每個市場一個請求，保留最近 60 個交易日。首次下載約 2 × 65 個請求、約 3 分鐘（有進度條）；之後每天只需 2 個請求，背景排程（每日 14:30）也會順便更新。查無資料的過去日期記為休市不再查詢。
- 掃描普通股（4 碼）與 **ETF**（00 開頭，含上市與上櫃 ETF，例如 0050、00878、006208、槓桿／反向 00631L、00632R、債券 00679B）；資料不足 35 根 K 棒的股票略過。
- 篩選：方向（全部／買進／賣出）、市場（全部／上市／上櫃／ETF；上市、上櫃只含股票，全部含 ETF）、最低成交量（預設 ≥500 張）。
- 類型篩選（多選）：布林收回、布林突破、KD 高低檔交叉、KD 中檔交叉、MACD 交叉、MACD 柱狀轉折；只列出目前訊號設定會產生的類型，並顯示各類型的檔數。選了多個類型時，訊號包含其中任一類型即顯示；「全部」為不限。結果依成交量排序，顯示收盤、漲跌幅、成交量與訊號類型。
- 點選股票開啟個股頁（下載完整日 K）；不在自選股時右上角可「加入自選股」。
- ETF 也可加入自選股、看個股頁與推播，市場別顯示為「上市ETF／上櫃ETF」；ETF 配息同樣適用除權息還原。

## 外觀

設定頁「外觀」可選 跟隨系統／淺色／深色，**預設深色**，點選即套用。深色採近黑底與略帶藍的中性灰，K 線紅漲綠跌與各指標線維持原色；狀態列與導覽列圖示顏色跟著 App 外觀切換，啟動畫面也是深色，避免閃白。

## 圖表操作

- 雙指縮放、左右拖曳平移。手指在圖表上時先判斷方向：水平拖曳平移圖表，垂直拖曳則捲動頁面。
- 點選某一根 K 棒：圖例顯示當日數值，圖表下方顯示該日的進出場訊號（方向、收盤價、觸發的指標）；若當日有指標事件但未達確認條件，也會列出。再點一次取消選取。
- 點訊號列表的項目，圖表會跳到該日並選取該 K 棒。

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
| KD 高低檔 | K 上穿 D（黃金交叉）且前日 K ≤ 超賣門檻（20） | K 下穿 D（死亡交叉）且前日 K ≥ 超買門檻（80） |
| KD 中檔（可關閉） | 前日 K 介於 20–80 的黃金交叉 | 前日 K 介於 20–80 的死亡交叉 |
| MACD 交叉 | DIF 上穿訊號線（OSC 翻正） | DIF 下穿訊號線（OSC 翻負） |
| MACD 柱狀轉折 | 負柱狀：前一根是本段最低點，今日第一根往上 | 正柱狀：前一根是本段最高點，今日第一根往下 |

布林「突破」與「收回」都是逆勢（超買／超賣）訊號：突破／跌破當天先發出，收回當天再發出一次，兩者分開計算、可各自開關。突破訊號可設定「量增倍數」：當日量 ≥ 倍數 × 前一日 5 日均量才成立（預設 0 = 不檢查）。

**訊號來源（可單選或多選）**：個股頁上方的「訊號來源」可選布林通道、KD、MACD（設定頁各指標的「納入訊號判斷」相同）。此設定為全域，自選股清單與推播一併套用。

- **只選一個指標**：只看該指標，**它底下任一類型**出現就是進出場訊號。例如只選布林通道，「突破布林上軌、布林突破上軌後跌回、跌破布林下軌、布林跌破下軌後收回」任一符合即為訊號；KD、MACD 以此類推。同一天出現多種類型會合併成一筆。
- **多選**：在最近 `確認視窗`（預設 3）個交易日內，需有至少 `確認數`（預設 2）個**不同指標**給出同方向訊號；同一指標的多種類型只算一個，「任一」＝確認數 1。確認數上限為已選指標數（最多 3）。
- 各指標包含哪些類型由設定頁的細項開關決定（預設全開），例如可關掉布林「突破訊號」只保留「收回訊號」。
- 訊號列表、點選 K 棒的卡片與推播，都會依指標分組顯示類型，例如「布林通道：跌破布林下軌、布林跌破下軌後收回｜KD：KD 低檔黃金交叉」。
- 同方向訊號產生後，於確認視窗內不重複發出。

**推播**：WorkManager 每日台北時間 14:30 後執行，更新所有自選股；若「最新一根 K 棒」產生訊號且該日尚未推播過，就發通知（點通知直接開該股圖表）。設定頁可「立即檢查」。

## 資料來源

| 用途 | 端點 |
|---|---|
| 上市日 K（每次一個月） | `https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY?date=yyyyMMdd&stockNo=XXXX&response=json` |
| 上櫃日 K（每次一個月） | `https://www.tpex.org.tw/www/zh-tw/afterTrading/tradingStock?code=XXXX&date=yyyy/MM/dd&response=json` |
| 上市除權息 | `https://www.twse.com.tw/rwd/zh/exRight/TWT49U?startDate=yyyyMMdd&endDate=yyyyMMdd&response=json` |
| 上櫃除權息 | `https://www.tpex.org.tw/www/zh-tw/bulletin/exDailyQ?startDate=yyyy/MM/dd&endDate=yyyy/MM/dd&response=json` |
| 上市全市場行情（選股） | `https://www.twse.com.tw/rwd/zh/afterTrading/MI_INDEX?date=yyyyMMdd&type=ALLBUT0999&response=json` |
| 上櫃全市場行情（選股） | `https://www.tpex.org.tw/www/zh-tw/afterTrading/otc?date=yyyy/MM/dd&type=EW&response=json` |
| 上櫃股票清單 | 取自最近一個交易日的上櫃全市場行情（`otc`，約 150 KB；openapi 的 mainboard 清單含上萬檔權證、超過 4 MB） |
| 上市股票清單 | `https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL` |

- 開啟個股時：若選股的全市場資料已下載，先帶入該股最近 60 個交易日，圖表**立即顯示**；接著由本月**往回**下載 12 個月日 K（第一個月約 0.5 秒即可顯示最新 K 線），再補齊更早的月份；下載中斷時下次自動補齊。之後只補最後一筆所在月份到本月。
- 節流依主機分開：證交所約每 5 秒 3 個請求即可能被暫時封鎖，間隔 1.7 秒；櫃買中心 0.6 秒。兩者可同時下載。
- 使用者正在看的個股優先：選股、除權息等背景同步會在前景下載期間暫停讓路。
- 成交量統一以「張」儲存（證交所回傳股數，已除以 1000）。
- 顏色採台股慣例：紅漲綠跌。

## 建置

需求：Android Studio（Ladybug 以上）或 Android SDK 35 + JDK 17+。

```bash
./gradlew :core:test          # 指標與訊號單元測試（純 JVM）
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## 已用真實資料驗證（2026-10）

個股日 K（上市／上櫃）、除權除息計算結果表（上市 889 筆、上櫃 673 筆）、全市場收盤行情（上市 1,085 檔股票＋239 檔 ETF、上櫃 869 檔股票＋118 檔 ETF）、上市股票清單，皆以實際 API 回應跑過 App 的解析程式。REIT（01001T 等）與 ETN（020000 等）不納入選股。

## 已知限制 / 後續可做

- 還原價未處理減資、分割等非除權息的股本變動。
- 股票清單只在本地為空時下載一次。
- 端點格式為政府網站公開資料，若官方改版需調整 `MarketDataParser` / DTO。
- 部分舊 Android 裝置可能缺少 TWCA 根憑證，連線證交所會出現 SSL 錯誤，必要時可加入 `network_security_config` 信任該 CA。

> 本 App 訊號僅為技術指標之機械式判斷，不構成任何投資建議。
