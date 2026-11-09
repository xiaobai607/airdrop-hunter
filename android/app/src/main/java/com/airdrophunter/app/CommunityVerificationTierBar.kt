package com.airdrophunter.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale

data class CommunityVerificationTierSegment(
    val key: String,
    val label: String,
    val amount: Double,
    val share: Float
)

private val communityVerificationTierDefinitions = listOf(
    Triple("user_reported", "用户自报", Color(0x66EF6262)),
    Triple("transaction_linked", "链上已确认", Color(0xFFFFB45C)),
    Triple("amount_verified", "链上精确核验", Color(0xFFCC4E4E))
)

fun communityVerificationTierSegments(values: Map<String, Double>): List<CommunityVerificationTierSegment> {
    val positive = communityVerificationTierDefinitions.mapNotNull { (key, label, _) ->
        val amount = values[key]?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
        Triple(key, label, amount)
    }
    if (positive.isEmpty()) return emptyList()

    val scale = positive.maxOf { it.third }
    val scaledTotal = positive.sumOf { it.third / scale }
    if (!scaledTotal.isFinite() || scaledTotal <= 0.0) return emptyList()

    return positive.map { (key, label, amount) ->
        CommunityVerificationTierSegment(
            key = key,
            label = label,
            amount = amount,
            share = ((amount / scale) / scaledTotal).toFloat()
        )
    }
}

@Composable
fun CommunityVerificationTierBar(
    values: Map<String, Double>,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val segments = remember(values) { communityVerificationTierSegments(values) }
    if (segments.isEmpty()) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(if (compact) 6.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            segments.forEach { segment ->
                val color = communityVerificationTierDefinitions.first { it.first == segment.key }.third
                Box(
                    Modifier.weight(segment.share).fillMaxHeight()
                        .background(color, RoundedCornerShape(5.dp))
                )
            }
        }
        Text(
            text = segments.joinToString(" · ") {
                "${it.label} ${String.format(Locale.US, "$%,.2f", it.amount)}"
            },
            color = if (compact) Color(0xFF7B858F) else Color(0xFFB9C8C0),
            style = MaterialTheme.typography.labelSmall
        )
    }
}