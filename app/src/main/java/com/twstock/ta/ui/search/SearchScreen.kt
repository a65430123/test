package com.twstock.ta.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.twstock.ta.data.local.labelFor
import com.twstock.ta.data.repository.QuoteSummary
import com.twstock.ta.ui.AppViewModelProvider
import com.twstock.ta.ui.theme.MarketColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenStock: (String) -> Unit,
    vm: SearchViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                title = {
                    TextField(
                        value = state.query,
                        onValueChange = vm::onQueryChange,
                        placeholder = { Text("輸入股票代號或名稱") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Clear, "清除") }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            // 鍵盤「搜尋」：有結果開第一筆，否則嘗試直接查詢代號
                            state.results.firstOrNull()?.let { onOpenStock(it.info.code) }
                                ?: state.lookupCode?.let { vm.lookup(it, onOpenStock) }
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loadingList || state.looking) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize()) {
                if (state.query.isBlank()) {
                    item(key = "hint") { Hint("例如：2330、台積電、0050、00878、環球晶") }
                }
                items(state.results, key = { it.info.code }) { r ->
                    ResultRow(r, onClick = { onOpenStock(r.info.code) })
                    HorizontalDivider()
                }
                state.lookupCode?.let { code ->
                    item(key = "lookup") {
                        ListItem(
                            modifier = Modifier.clickable(enabled = !state.looking) { vm.lookup(code, onOpenStock) },
                            headlineContent = { Text("查詢代號 $code") },
                            supportingContent = { Text("清單中沒有這個代號，直接向證交所／櫃買中心查詢") },
                            trailingContent = { if (state.looking) CircularProgressIndicator(Modifier.padding(4.dp)) },
                        )
                    }
                }
                if (state.query.isNotBlank() && state.results.isEmpty() && state.lookupCode == null && !state.loadingList) {
                    item(key = "none") { Hint("找不到符合「${state.query.trim()}」的股票") }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun ResultRow(r: SearchResult, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(r.info.name, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("${r.info.code}・${r.info.market.labelFor(r.info.code)}") },
        trailingContent = if (r.quote != null) {
            { QuoteColumn(r.quote) }
        } else {
            null
        },
    )
}

@Composable
private fun QuoteColumn(q: QuoteSummary) {
    val color = MarketColors.forChange(q.changePercent)
    Column(horizontalAlignment = Alignment.End) {
        Text("%.2f".format(q.close), color = color, fontWeight = FontWeight.Bold)
        Text(q.changePercent?.let { "%+.2f%%".format(it) } ?: "--", color = color, style = MaterialTheme.typography.bodySmall)
    }
}
