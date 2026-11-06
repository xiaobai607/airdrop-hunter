package com.airdrophunter.app
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import java.math.BigDecimal
import java.text.DecimalFormat

private val chartColors=listOf(Color(0xFF16A085),Color(0xFF3977D5),Color(0xFFF0A33A),Color(0xFF8B65C7),Color(0xFFDB6B78))
@Composable
fun WalletDetailScreen(wallet:WalletDto,onBack:()->Unit,vm:WalletDetailViewModel=viewModel()){
 val s by vm.uiState.collectAsStateWithLifecycle();val clipboard=LocalClipboardManager.current
 LaunchedEffect(wallet.address){if(wallet.ecosystem==WalletEcosystem.TRON)vm.loadWallet(wallet.address)}
 DisposableEffect(Unit){onDispose{vm.clear()}}
 Column(Modifier.fillMaxSize().background(AppColors.Background)){
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"返回",tint=AppColors.TextPrimary)};Column(Modifier.weight(1f)){Text(wallet.wallet_name.ifBlank{"钱包资产"},color=AppColors.TextPrimary,fontWeight=FontWeight.SemiBold);Text(shortAddress(wallet.address),color=AppColors.TextSecondary,style=MaterialTheme.typography.labelSmall)};IconButton(onClick={clipboard.setText(AnnotatedString(wallet.address))}){Icon(Icons.Default.ContentCopy,"复制地址",tint=AppColors.Primary)}}
  if(wallet.ecosystem!=WalletEcosystem.TRON){Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){DynamicChainIcon(wallet.ecosystem,Modifier.size(54.dp));Spacer(Modifier.height(14.dp));Text("该网络余额查询尚未接入",color=AppColors.TextPrimary,fontWeight=FontWeight.SemiBold);Text("目前先开放 TRON 主网真实资产查询。",color=AppColors.TextSecondary)};return}
  Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Surface(shape=RoundedCornerShape(20.dp),color=AppColors.Primary){Text("TRON",Modifier.padding(horizontal=16.dp,vertical=8.dp),color=Color.White)};listOf("Ethereum · 接入中","BNB Chain · 接入中").forEach{Surface(shape=RoundedCornerShape(20.dp),color=AppColors.Surface){Text(it,Modifier.padding(horizontal=12.dp,vertical=8.dp),color=AppColors.TextHelper,style=MaterialTheme.typography.labelSmall)}}}
  Row(Modifier.fillMaxWidth().padding(start=20.dp,end=12.dp,top=22.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("总资产估值",color=AppColors.TextSecondary,style=MaterialTheme.typography.bodySmall);Text(s.totalUsd?.let{"$ "+fmt(it)}?:"—",color=AppColors.TextPrimary,fontSize=30.sp,fontWeight=FontWeight.Bold);Text("USD · TRONSCAN 实时返回",color=AppColors.TextHelper,style=MaterialTheme.typography.labelSmall)};IconButton(onClick={vm.refresh()}){Icon(Icons.Default.Refresh,"刷新资产",tint=AppColors.Primary)}}
  when{ s.isLoading&&s.tokenList.isEmpty()->Box(Modifier.fillMaxWidth().height(180.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=AppColors.Primary)}
   s.errorMessage!=null&&s.tokenList.isEmpty()->Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("暂时无法获取链上资产",color=AppColors.TextPrimary,fontWeight=FontWeight.SemiBold);Text(s.errorMessage.orEmpty(),color=AppColors.Danger,style=MaterialTheme.typography.bodySmall);TextButton(onClick={vm.refresh()}){Text("重试")}}
   else->{AssetChart(s.tokenList);Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Text("币种资产",color=AppColors.TextPrimary,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));if(s.isLoading)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=AppColors.Primary)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=16.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){items(s.tokenList,key={it.tokenId.ifBlank{it.symbol+it.name}}){TokenRow(it)}}
   }
  }
 }
}
@Composable private fun AssetChart(tokens:List<TronTokenBalance>){
 val valid=tokens.filter{(it.usdValue?:BigDecimal.ZERO)>BigDecimal.ZERO};if(valid.isEmpty())return
 val sum=valid.fold(BigDecimal.ZERO){a,t->a+(t.usdValue?:BigDecimal.ZERO)}
 Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=AppColors.Surface)){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Canvas(Modifier.size(108.dp)){var start=-90f;valid.forEachIndexed{i,t->val sweep=((t.usdValue?:BigDecimal.ZERO).toDouble()/sum.toDouble()*360).toFloat();drawArc(chartColors[i%chartColors.size],start,sweep,false,style=Stroke(16.dp.toPx()));start+=sweep}};Spacer(Modifier.width(18.dp));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)){valid.take(5).forEachIndexed{i,t->Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).clip(CircleShape).background(chartColors[i%chartColors.size]));Spacer(Modifier.width(7.dp));Text(t.symbol,Modifier.weight(1f),color=AppColors.TextSecondary,style=MaterialTheme.typography.labelMedium);Text(DecimalFormat("0.#").format((t.usdValue?:BigDecimal.ZERO).toDouble()/sum.toDouble()*100)+"%",color=AppColors.TextPrimary,style=MaterialTheme.typography.labelSmall)}}}}}
@Composable private fun TokenRow(t:TronTokenBalance){Card(shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=AppColors.Surface)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){if(!t.logoUrl.isNullOrBlank())AsyncImage(t.logoUrl,t.symbol,Modifier.size(38.dp).clip(CircleShape))else Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE8F3F1)),contentAlignment=Alignment.Center){Text(t.symbol.take(1),color=AppColors.Primary,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(t.symbol,color=AppColors.TextPrimary,fontWeight=FontWeight.SemiBold);Text(t.name,color=AppColors.TextHelper,style=MaterialTheme.typography.labelSmall,maxLines=1)};Column(horizontalAlignment=Alignment.End){Text(DecimalFormat("#,##0.########").format(t.quantity),color=AppColors.TextPrimary,fontWeight=FontWeight.SemiBold);Text(t.usdValue?.let{"$ "+fmt(it)}?:"估值暂无",color=AppColors.TextSecondary,style=MaterialTheme.typography.labelSmall)}}}}
private fun fmt(v:BigDecimal)=DecimalFormat("#,##0.00").format(v)
