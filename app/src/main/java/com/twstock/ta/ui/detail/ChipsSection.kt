package com.twstock.ta.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.twstock.core.model.BrokerFlow
import com.twstock.core.model.ChipDay
import com.twstock.core.model.IndicatorParams
import com.twstock.ta.ui.theme.MarketColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_FMT = DateTimeFormatter.ofPattern("MM/dd")
private const val TOP_BROKERS = 10

/** 籌碼分頁：法人與融資融券近 N 日、目前連續買賣超天數、分點券商進出。 */
fun LazyListScope.chipItems(
    chips: List<ChipDay>,
    params: IndicatorParams,
    broker: BrokerState,
    onSelectDate: (LocalDate) -> Unit,
) {
    if (chips.isEmpty()) {
        item(key = "chips-empty") {
            Hint("尚無籌碼資料。按右上角更新即可下載最近 20 個交易日的三大法人與融資融券（首次約需 1 分鐘）。")
        }
    } else {
        item(key = "chips-summary") { ChipSummary(chips, params) }
        item(key = "chips-header") {
            Hint("單位：張。點選日期可查看當日分點券商進出。")
        }
        items(chips.asReversed(), key = { "chip-${it.date}" }) { day ->
            ChipRow(day, selected = (broker as? BrokerState.Loaded)?.date == day.date, onClick = { onSelectDate(day.date) })
        }
    }
    item(key = "brokers") { BrokerSection(broker) }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

private fun signed(v: Long?): String = v?.let { "%+,d".format(it) } ?: "--"

private fun colorOf(v: Long?): Color = MarketColors.forChange(v?.toDouble())

/** 截至最後一日，所選法人連續買超（正）或賣超（負）的天數。 */
private fun currentStreak(chips: List<ChipDay>, params: IndicatorParams): Int {
    var streak = 0
    for (day in chips.asReversed()) {
        val net = day.net(params.chipParty) ?: break
        when {
            net > params.chipMinNet && streak >= 0 -> streak++
            net < -params.chipMinNet && streak <= 0 -> streak--
            else -> break
        }
    }
    return streak
}

@Composable
private fun ChipSummary(chips: List<ChipDay>, params: IndicatorParams) {
    val last = chips.last()
    val streak = currentStreak(chips, params)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${last.date}　三大法人 ${signed(last.totalNet)} 張",
            style = MaterialTheme.typography.titleSmall,
            color = colorOf(last.totalNet),
        )
        Text(
            buildAnnotatedString {
                append("外資 "); withStyle(SpanStyle(color = colorOf(last.foreignNet))) { append(signed(last.foreignNet)) }
                append("　投信 "); withStyle(SpanStyle(color = colorOf(last.trustNet))) { append(signed(last.trustNet)) }
                append("　自營商 "); withStyle(SpanStyle(color = colorOf(last.dealerNet))) { append(signed(last.dealerNet)) }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "融資餘額 ${last.marginBalance?.let { "%,d".format(it) } ?: "--"}（${signed(last.marginChange)}）　" +
                "融券餘額 ${last.shortBalance?.let { "%,d".format(it) } ?: "--"}（${signed(last.shortChange)}）",
            style = MaterialTheme.typography.bodySmall,
        )
        val who = params.chipParty.label
        Text(
            when {
                streak > 0 -> "$who 已連續買超 $streak 日（訊號門檻 ${params.chipStreakDays} 日）"
                streak < 0 -> "$who 已連續賣超 ${-streak} 日（訊號門檻 ${params.chipStreakDays} 日）"
                else -> "$who 目前無連續買賣超"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (streak > 0) MarketColors.Up else if (streak < 0) MarketColors.Down else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
private fun ChipRow(day: ChipDay, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                day.date.format(DAY_FMT),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("合計 ${signed(day.totalNet)}", color = colorOf(day.totalNet), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            buildAnnotatedString {
                append("外 "); withStyle(SpanStyle(color = colorOf(day.foreignNet))) { append(signed(day.foreignNet)) }
                append("  投 "); withStyle(SpanStyle(color = colorOf(day.trustNet))) { append(signed(day.trustNet)) }
                append("  自 "); withStyle(SpanStyle(color = colorOf(day.dealerNet))) { append(signed(day.dealerNet)) }
                append("｜資 "); withStyle(SpanStyle(color = colorOf(day.marginChange))) { append(signed(day.marginChange)) }
                append("  券 "); withStyle(SpanStyle(color = colorOf(day.shortChange))) { append(signed(day.shortChange)) }
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BrokerSection(state: BrokerState) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        HorizontalDivider()
        val title = (state as? BrokerState.Loaded)?.let { "分點券商進出（${it.date}）" } ?: "分點券商進出"
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        when (state) {
            BrokerState.NoToken -> Hint("到設定頁「籌碼」輸入 FinMind API token 後即可查看分點券商買賣超。")
            BrokerState.Idle -> Hint("點選上方日期載入。")
            BrokerState.Loading -> CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp), strokeWidth = 2.dp)
            is BrokerState.Error -> Text(
                "載入失敗：${state.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            is BrokerState.Loaded -> {
                val buyers = state.flows.filter { it.net > 0 }.sortedByDescending { it.net }.take(TOP_BROKERS)
                val sellers = state.flows.filter { it.net < 0 }.sortedBy { it.net }.take(TOP_BROKERS)
                if (buyers.isEmpty() && sellers.isEmpty()) {
                    Hint("該日沒有分點資料（可能尚未公布或非交易日）。")
                } else {
                    BrokerList("買超前 $TOP_BROKERS 名", buyers)
                    BrokerList("賣超前 $TOP_BROKERS 名", sellers)
                }
            }
        }
    }
}

@Composable
private fun BrokerList(title: String, flows: List<BrokerFlow>) {
    if (flows.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
    )
    flows.forEach { f ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(f.brokerName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("買 %,d　賣 %,d　".format(f.buy, f.sell), style = MaterialTheme.typography.bodySmall)
            Text(signed(f.net), color = colorOf(f.net), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}
