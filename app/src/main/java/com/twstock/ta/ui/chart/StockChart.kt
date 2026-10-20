package com.twstock.ta.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twstock.core.AnalysisResult
import com.twstock.core.model.IndicatorParams
import com.twstock.core.signal.SignalSide
import com.twstock.ta.ui.theme.MarketColors
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_BARS = 20f
private const val MAX_BARS = 250f
private val DATE_FMT = DateTimeFormatter.ofPattern("yy/MM/dd")

/** 可視範圍：[start, end) 的 K 棒，靠右對齊。 */
internal data class Viewport(val start: Int, val end: Int, val barWidth: Float, val plotRight: Float) {
    fun x(i: Int): Float = plotRight - (end - i - 0.5f) * barWidth
    fun indexAt(x: Float): Int? {
        val i = end - 1 - floor((plotRight - x) / barWidth).toInt()
        return i.takeIf { it in start until end }
    }
}

@Stable
class ChartState {
    /** 畫面上可容納的 K 棒數（縮放）。 */
    var visibleCount by mutableFloatStateOf(80f)
    /** 自最右端往左捲動的 K 棒數（平移）。 */
    var scrollOffset by mutableFloatStateOf(0f)
    /** 使用者點選的 K 棒；null 時圖例顯示最右側 K 棒。 */
    var selectedIndex by mutableStateOf<Int?>(null)

    internal fun viewport(n: Int, plotWidth: Float): Viewport {
        val count = visibleCount.coerceIn(MIN_BARS, MAX_BARS)
        val maxOffset = max(0f, n - count)
        val offset = scrollOffset.coerceIn(0f, maxOffset).roundToInt()
        val end = n - offset
        val start = max(0, end - count.roundToInt())
        return Viewport(start, end, plotWidth / count, plotWidth)
    }

    /** 選取指定 K 棒並將其捲動至畫面中央。 */
    fun focus(index: Int, n: Int) {
        selectedIndex = index
        val count = visibleCount.coerceIn(MIN_BARS, MAX_BARS)
        scrollOffset = (n - 1 - index - count / 2).coerceIn(0f, max(0f, n - count))
    }

    internal fun onTransform(n: Int, plotWidth: Float, panX: Float, zoom: Float) {
        visibleCount = (visibleCount / zoom).coerceIn(MIN_BARS, MAX_BARS)
        val barWidth = plotWidth / visibleCount
        scrollOffset = (scrollOffset + panX / barWidth).coerceIn(0f, max(0f, n - visibleCount))
    }
}

@Composable
fun rememberChartState(): ChartState = remember { ChartState() }

@Composable
fun StockChart(
    analysis: AnalysisResult,
    params: IndicatorParams,
    state: ChartState,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val n = analysis.candles.size

    Canvas(
        modifier
            .fillMaxWidth()
            .height(470.dp)
            .pointerInput(n) {
                val axis = AXIS_WIDTH.toPx()
                detectTransformGestures { _, pan, zoom, _ ->
                    state.onTransform(n, size.width - axis, pan.x, zoom)
                }
            }
            .pointerInput(n) {
                val axis = AXIS_WIDTH.toPx()
                detectTapGestures { pos ->
                    val idx = state.viewport(n, size.width - axis).indexAt(pos.x)
                    state.selectedIndex = if (idx == state.selectedIndex) null else idx
                }
            },
    ) {
        if (n == 0) return@Canvas
        val plotWidth = size.width - AXIS_WIDTH.toPx()
        val vp = state.viewport(n, plotWidth)
        val selected = state.selectedIndex?.takeIf { it in vp.start until vp.end } ?: (vp.end - 1)
        val ctx = DrawCtx(this, measurer, vp, textColor, gridColor, plotWidth)

        val legendH = LEGEND_HEIGHT.toPx()
        val gap = PANE_GAP.toPx()
        var top = 0f
        val mainRect = Pane(top + legendH, top + MAIN_HEIGHT.toPx())
        top += MAIN_HEIGHT.toPx() + gap
        val kdRect = Pane(top + legendH, top + KD_HEIGHT.toPx())
        top += KD_HEIGHT.toPx() + gap
        val macdRect = Pane(top + legendH, top + MACD_HEIGHT.toPx())

        ctx.drawMainPane(analysis, params, mainRect, selected)
        ctx.drawKdPane(analysis, params, kdRect, selected)
        ctx.drawMacdPane(analysis, params, macdRect, selected)
        ctx.drawDateAxis(analysis, macdRect.bottom + 2.dp.toPx())

        if (state.selectedIndex != null) {
            val x = vp.x(selected)
            drawLine(textColor.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, macdRect.bottom), 1f)
        }
    }
}

private val AXIS_WIDTH = 52.dp
private val LEGEND_HEIGHT = 16.dp
private val PANE_GAP = 6.dp
private val MAIN_HEIGHT = 250.dp
private val KD_HEIGHT = 90.dp
private val MACD_HEIGHT = 100.dp

private data class Pane(val top: Float, val bottom: Float) {
    fun y(v: Double, lo: Double, hi: Double): Float {
        val range = if (hi - lo == 0.0) 1.0 else hi - lo
        return (bottom - (v - lo) / range * (bottom - top)).toFloat()
    }
}

private class DrawCtx(
    val scope: DrawScope,
    val measurer: TextMeasurer,
    val vp: Viewport,
    val textColor: Color,
    val gridColor: Color,
    val plotWidth: Float,
) {
    private val labelStyle = TextStyle(fontSize = 10.sp, color = textColor)
    private val stroke = with(scope) { 1.2.dp.toPx() }

    fun drawMainPane(a: AnalysisResult, p: IndicatorParams, pane: Pane, sel: Int) = with(scope) {
        val c = a.candles
        val bb = a.bollinger
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        for (i in vp.start until vp.end) {
            lo = min(lo, min(c[i].low, bb.lower[i] ?: c[i].low))
            hi = max(hi, max(c[i].high, bb.upper[i] ?: c[i].high))
        }
        val pad = (hi - lo) * 0.06
        lo -= pad
        hi += pad
        drawGrid(pane, listOf(lo + (hi - lo) * 0.1, lo + (hi - lo) * 0.5, lo + (hi - lo) * 0.9), lo, hi) { "%.2f".format(it) }

        drawSeries(bb.upper, pane, lo, hi, MarketColors.BollingerBand)
        drawSeries(bb.lower, pane, lo, hi, MarketColors.BollingerBand)
        drawSeries(bb.middle, pane, lo, hi, MarketColors.BollingerMid)

        val bodyW = max(1f, vp.barWidth * 0.7f)
        for (i in vp.start until vp.end) {
            val k = c[i]
            val color = when {
                k.close > k.open -> MarketColors.Up
                k.close < k.open -> MarketColors.Down
                else -> MarketColors.Flat
            }
            val x = vp.x(i)
            drawLine(color, Offset(x, pane.y(k.high, lo, hi)), Offset(x, pane.y(k.low, lo, hi)), max(1f, stroke * 0.8f))
            val yTop = pane.y(max(k.open, k.close), lo, hi)
            val yBot = pane.y(min(k.open, k.close), lo, hi)
            drawRect(color, Offset(x - bodyW / 2, yTop), Size(bodyW, max(1f, yBot - yTop)))
        }

        // 進出場訊號標記
        val markerSize = max(6f, min(vp.barWidth, 14.dp.toPx()))
        for (s in a.signals) {
            if (s.index !in vp.start until vp.end) continue
            val x = vp.x(s.index)
            val k = c[s.index]
            if (s.side == SignalSide.BUY) {
                val y = pane.y(k.low, lo, hi) + 4.dp.toPx()
                drawTriangle(x, y, markerSize, up = true, color = MarketColors.Up)
            } else {
                val y = pane.y(k.high, lo, hi) - 4.dp.toPx()
                drawTriangle(x, y, markerSize, up = false, color = MarketColors.Down)
            }
        }

        val k = c[sel]
        drawLegend(pane.top, buildAnnotatedString {
            withStyle(SpanStyle(color = textColor)) {
                append("${k.date.format(DATE_FMT)} 開%.2f 高%.2f 低%.2f ".format(k.open, k.high, k.low))
            }
            withStyle(SpanStyle(color = MarketColors.forChange(k.close - k.open))) { append("收%.2f  ".format(k.close)) }
            withStyle(SpanStyle(color = MarketColors.BollingerMid)) {
                append("MA${p.bbPeriod} ${fmt(bb.middle[sel])} ")
            }
            withStyle(SpanStyle(color = MarketColors.BollingerBand)) {
                append("上${fmt(bb.upper[sel])} 下${fmt(bb.lower[sel])}")
            }
        })
    }

    fun drawKdPane(a: AnalysisResult, p: IndicatorParams, pane: Pane, sel: Int) = with(scope) {
        drawGrid(pane, listOf(p.kdOversold, 50.0, p.kdOverbought), 0.0, 100.0) { "%.0f".format(it) }
        drawSeries(a.kd.k, pane, 0.0, 100.0, MarketColors.K)
        drawSeries(a.kd.d, pane, 0.0, 100.0, MarketColors.D)
        drawLegend(pane.top, buildAnnotatedString {
            withStyle(SpanStyle(color = textColor)) { append("KD(${p.kdPeriod},${p.kdKSmoothing},${p.kdDSmoothing})  ") }
            withStyle(SpanStyle(color = MarketColors.K)) { append("K ${fmt(a.kd.k[sel])}  ") }
            withStyle(SpanStyle(color = MarketColors.D)) { append("D ${fmt(a.kd.d[sel])}") }
        })
    }

    fun drawMacdPane(a: AnalysisResult, p: IndicatorParams, pane: Pane, sel: Int) = with(scope) {
        val m = a.macd
        var extent = 0.0
        for (i in vp.start until vp.end) {
            listOf(m.dif[i], m.signal[i], m.histogram[i]).forEach { v -> if (v != null) extent = max(extent, abs(v)) }
        }
        if (extent == 0.0) extent = 1.0
        extent *= 1.1
        drawGrid(pane, listOf(-extent / 1.1, 0.0, extent / 1.1), -extent, extent) { "%.2f".format(it) }

        val zeroY = pane.y(0.0, -extent, extent)
        val barW = max(1f, vp.barWidth * 0.6f)
        for (i in vp.start until vp.end) {
            val h = m.histogram[i] ?: continue
            val y = pane.y(h, -extent, extent)
            val color = if (h >= 0) MarketColors.Up else MarketColors.Down
            drawRect(color.copy(alpha = 0.7f), Offset(vp.x(i) - barW / 2, min(y, zeroY)), Size(barW, abs(zeroY - y)))
        }
        drawSeries(m.dif, pane, -extent, extent, MarketColors.Dif)
        drawSeries(m.signal, pane, -extent, extent, MarketColors.Signal)
        drawLegend(pane.top, buildAnnotatedString {
            withStyle(SpanStyle(color = textColor)) { append("MACD(${p.macdFast},${p.macdSlow},${p.macdSignal})  ") }
            withStyle(SpanStyle(color = MarketColors.Dif)) { append("DIF ${fmt(m.dif[sel])}  ") }
            withStyle(SpanStyle(color = MarketColors.Signal)) { append("MACD ${fmt(m.signal[sel])}  ") }
            withStyle(SpanStyle(color = MarketColors.forChange(m.histogram[sel]))) { append("OSC ${fmt(m.histogram[sel])}") }
        })
    }

    fun drawDateAxis(a: AnalysisResult, y: Float) = with(scope) {
        if (vp.end <= vp.start) return@with
        val idx = listOf(vp.start, (vp.start + vp.end - 1) / 2, vp.end - 1).distinct()
        idx.forEachIndexed { n, i ->
            val text = a.candles[i].date.format(DATE_FMT)
            val layout = measurer.measure(text, labelStyle)
            val x = when (n) {
                0 -> max(0f, vp.x(i) - layout.size.width / 2f)
                idx.lastIndex -> min(plotWidth - layout.size.width, vp.x(i) - layout.size.width / 2f)
                else -> vp.x(i) - layout.size.width / 2f
            }
            drawText(layout, topLeft = Offset(x, y))
        }
    }

    private fun DrawScope.drawGrid(pane: Pane, levels: List<Double>, lo: Double, hi: Double, label: (Double) -> String) {
        drawRect(gridColor, Offset(0f, pane.top), Size(plotWidth, pane.bottom - pane.top), style = Stroke(1f))
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        for (v in levels) {
            val y = pane.y(v, lo, hi)
            drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), 1f, pathEffect = dash)
            val layout = measurer.measure(label(v), labelStyle)
            drawText(layout, topLeft = Offset(plotWidth + 4.dp.toPx(), y - layout.size.height / 2f))
        }
    }

    private fun DrawScope.drawSeries(values: List<Double?>, pane: Pane, lo: Double, hi: Double, color: Color) {
        val path = Path()
        var started = false
        for (i in vp.start until vp.end) {
            val v = values[i]
            if (v == null) {
                started = false
                continue
            }
            val pt = Offset(vp.x(i), pane.y(v, lo, hi))
            if (started) path.lineTo(pt.x, pt.y) else path.moveTo(pt.x, pt.y)
            started = true
        }
        drawPath(path, color, style = Stroke(stroke))
    }

    private fun DrawScope.drawTriangle(x: Float, tipY: Float, size: Float, up: Boolean, color: Color) {
        val h = size * 0.9f
        val path = Path().apply {
            if (up) {
                moveTo(x, tipY)
                lineTo(x - size / 2, tipY + h)
                lineTo(x + size / 2, tipY + h)
            } else {
                moveTo(x, tipY)
                lineTo(x - size / 2, tipY - h)
                lineTo(x + size / 2, tipY - h)
            }
            close()
        }
        drawPath(path, color)
    }

    private fun DrawScope.drawLegend(paneTop: Float, text: AnnotatedString) {
        val layout = measurer.measure(text, labelStyle, softWrap = false, maxLines = 1)
        drawText(layout, topLeft = Offset(2f, paneTop - LEGEND_HEIGHT.toPx() + 1f))
    }

    private fun fmt(v: Double?): String = v?.let { "%.2f".format(it) } ?: "--"
}
