package com.airdrophunter.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.memory.MemoryCache
import coil.disk.DiskCache
import java.io.File

private object DynamicChainImageLoader {
    @Volatile private var cached: ImageLoader? = null
    fun get(context: android.content.Context): ImageLoader = cached ?: synchronized(this) {
        cached ?: ImageLoader.Builder(context.applicationContext)
            .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.25).build() }
            .diskCache { DiskCache.Builder().directory(File(context.cacheDir, "chain_icon_cache")).maxSizeBytes(32L * 1024L * 1024L).build() }
            .crossfade(true).build().also { cached = it }
    }
}

private fun chainLogoUrl(network: String): String? {
    val slug = when (network.trim().lowercase()) {
        "eth", "ethereum", "erc20", "evm" -> "ethereum"
        "bsc", "bnb", "bnb chain", "smartchain" -> "smartchain"
        "arb", "arbitrum", "arbitrum one" -> "arbitrum"
        "tron", "trx", "trc20" -> "tron"
        "sol", "solana" -> "solana"
        "btc", "bitcoin" -> "bitcoin"
        else -> return null
    }
    return "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/$slug/info/logo.png"
}

@Composable
fun DynamicChainIcon(network: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loader = remember(context) { DynamicChainImageLoader.get(context) }
    val url = remember(network) { chainLogoUrl(network) }
    val palette = listOf(Color(0xFF2878D0), Color(0xFF6B5DD3), Color(0xFF168A83), Color(0xFFB66A2C), Color(0xFF327A65), Color(0xFF596579))
    val fallbackColor = remember(network) { palette[(network.lowercase().hashCode() and Int.MAX_VALUE) % palette.size] }
    val initial = remember(network) { network.trim().firstOrNull()?.uppercase() ?: "?" }
    if (url != null) {
        SubcomposeAsyncImage(model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            imageLoader = loader, contentDescription = network,
            modifier = modifier.clip(CircleShape).background(Color.White, CircleShape),
            contentScale = ContentScale.Fit) {
            if (painter.state is AsyncImagePainter.State.Error) ChainIconFallback(initial, fallbackColor, modifier)
            else SubcomposeAsyncImageContent()
        }
    } else ChainIconFallback(initial, fallbackColor, modifier)
}

@Composable
private fun ChainIconFallback(initial: String, color: Color, modifier: Modifier) {
    Box(modifier.background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(initial, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ChainIcon(chain: String, networkIcon: String = "", modifier: Modifier = Modifier) {
    val key = chain.trim().lowercase()
    val symbol = networkIcon.trim().uppercase()
    val iconRes = when {
        key.contains("tron") || key.contains("trc20") || symbol == "T" -> R.drawable.ic_chain_tron
        key.contains("solana") || symbol == "S" -> R.drawable.ic_chain_solana
        key.contains("bsc") || key.contains("bnb") -> R.drawable.ic_chain_bsc
        key.contains("bitcoin") || symbol == "B" -> R.drawable.ic_chain_bitcoin
        key.contains("ethereum") || key.contains("erc20") || key == "evm" || symbol == "E" -> R.drawable.ic_chain_ethereum
        else -> 0
    }
    if (iconRes != 0) Image(painter = painterResource(iconRes), contentDescription = chain,
        modifier = modifier.clip(CircleShape).background(Color.White, CircleShape), contentScale = ContentScale.Fit)
    else {
        val palette = listOf(Color(0xFF2878D0), Color(0xFF6B5DD3), Color(0xFF168A83), Color(0xFFB66A2C), Color(0xFF327A65), Color(0xFF596579))
        val color = remember(key) { palette.random() }
        val initial = chain.trim().firstOrNull()?.uppercase() ?: "?"
        ChainIconFallback(initial, color, modifier)
    }
}
