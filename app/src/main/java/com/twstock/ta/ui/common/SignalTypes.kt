package com.twstock.ta.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.twstock.core.model.SignalIndicator
import com.twstock.core.signal.TradeSignal
import com.twstock.ta.ui.theme.MarketColors

/** 依指標分組列出訊號類型，例如「[布林通道] 跌破布林下軌、布林跌破下軌後收回」。 */
@Composable
fun SignalTypes(signal: TradeSignal) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        signal.groups.forEach { (indicator, events) ->
            TypeLine(indicator, events.joinToString("、") { it.description })
        }
    }
}

@Composable
fun TypeLine(indicator: SignalIndicator, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Text(
            indicator.label,
            style = MaterialTheme.typography.labelSmall,
            color = MarketColors.forIndicator(indicator),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .border(1.dp, MarketColors.forIndicator(indicator), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
