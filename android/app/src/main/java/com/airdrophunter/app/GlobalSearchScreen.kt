package com.airdrophunter.app

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airdrophunter.app.ui.theme.AppColors
import com.airdrophunter.app.data.OkxMarketRepository
import com.airdrophunter.app.data.OkxTicker
import java.text.NumberFormat
import java.util.Locale
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.globalSearchDataStore by preferencesDataStore(name = "global_search_history")
private val SEARCH_HISTORY_KEY = stringPreferencesKey("recent_queries")

private data class HotSearchItem(
    val symbol: String,
    val name: String,
    val iconText: String
)

private val hotSearchItems = listOf(
    HotSearchItem("BTC", "Bitcoin", "₿"),
    HotSearchItem("ETH", "Ethereum", "Ξ"),
    HotSearchItem("SOL", "Solana", "◎")
)

@Composable
fun GlobalSearchScreen(
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onSearch: (String) -> Unit,
    onCoinSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as AirdropHunterApplication
    val marketRepository = remember(application) { application.appContainer.okxMarketRepository }
    var marketTickers by remember { mutableStateOf<List<OkxTicker>>(emptyList()) }
    var marketLoadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(marketRepository) {
        runCatching { marketRepository.fetchSpotTickers() }
            .onSuccess { marketTickers = it; marketLoadFailed = false }
            .onFailure { marketLoadFailed = true }
    }
    val tickerBySymbol = remember(marketTickers) { marketTickers.associateBy { it.symbol } }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    val history by context.globalSearchDataStore.data
        .map { preferences ->
            preferences[SEARCH_HISTORY_KEY].orEmpty()
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(10)
        }
        .collectAsState(initial = emptyList())

    fun submitSearch(raw: String) {
        val value = raw.trim().replace("\n", " ")
        if (value.isBlank()) return
        scope.launch {
            context.globalSearchDataStore.edit { preferences ->
                val old = preferences[SEARCH_HISTORY_KEY].orEmpty()
                    .split("\n").map { it.trim() }.filter { it.isNotBlank() }
                val updated = (listOf(value) + old.filterNot { it.equals(value, ignoreCase = true) })
                    .take(10)
                preferences[SEARCH_HISTORY_KEY] = updated.joinToString("\n")
            }
            keyboard?.hide()
            val target = Uri.parse("https://www.oklink.com/cn/search")
                .buildUpon()
                .appendQueryParameter("q", value)
                .build()
                .toString()
            onSearch(target)
        }
    }

    Column(
        modifier.fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFF0F2F5))
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.TextHelper)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text("币种、股票、地址、DApp") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch(query) })
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空输入", tint = AppColors.TextHelper)
                    }
                }
            }
            TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 2.dp)) {
                Text("取消", color = AppColors.TextPrimary, fontSize = 15.sp)
            }
        }

        if (history.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("历史记录", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { scope.launch { context.globalSearchDataStore.edit { it.remove(SEARCH_HISTORY_KEY) } } },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("清空历史", color = AppColors.TextHelper, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.Surface)
                                .clickable { query = item; submitSearch(item) }
                                .padding(horizontal = 13.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, null, tint = AppColors.TextHelper, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(item, color = AppColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                            Icon(Icons.Default.ArrowBack, null, tint = AppColors.TextHelper, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("热门搜索", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (marketTickers.isEmpty()) "实时行情" else "实时行情 · OKX", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
            }
            hotSearchItems.forEach { item ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(AppColors.Surface)
                        .clickable { onCoinSelected(item.symbol) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF0F2F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.iconText, color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.symbol, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(item.name, color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val ticker = tickerBySymbol[item.symbol]
                        val priceText = ticker?.let {
                            "$ " + NumberFormat.getNumberInstance(Locale.US).apply {
                                minimumFractionDigits = 2
                                maximumFractionDigits = 8
                            }.format(it.lastPrice)
                        } ?: if (marketLoadFailed) "暂不可用" else "加载中…"
                        Text(priceText, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        if (ticker != null) {
                            val changeText = String.format(Locale.US, "%+.2f%%", ticker.change24hPercent)
                            Text(changeText, color = if (ticker.change24hPercent >= 0) AppColors.Success else AppColors.Danger,
                                style = MaterialTheme.typography.labelSmall)
                        } else {
                            Text("点击查看详情", color = AppColors.TextHelper, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}
