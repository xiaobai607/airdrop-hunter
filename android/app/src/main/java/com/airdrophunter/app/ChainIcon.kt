package com.airdrophunter.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun DynamicChainIcon(network: String, modifier: Modifier = Modifier) {
    // Wallet chain marks are packaged in the APK: no remote fetch, decode, or cache miss on page entry.
    ChainIcon(chain = network, modifier = modifier)
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
    if (iconRes != 0) Image(painter = painterResource(iconRes), contentDescription = chain, modifier = modifier)
    else {
        val palette = listOf(Color(0xFF2878D0), Color(0xFF6B5DD3), Color(0xFF168A83), Color(0xFFB66A2C), Color(0xFF327A65), Color(0xFF596579))
        val color = remember(key) { palette.random() }
        val initial = chain.trim().firstOrNull()?.uppercase() ?: "?"
        ChainIconFallback(initial, color, modifier)
    }
}
