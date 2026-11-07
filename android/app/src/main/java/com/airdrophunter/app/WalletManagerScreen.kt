package com.airdrophunter.app
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airdrophunter.app.data.*
import com.airdrophunter.app.ui.theme.AppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WalletManagerUiState(val wallets: List<WalletDto> = emptyList(), val isLoading: Boolean = false, val isSaving: Boolean = false, val error: String? = null)
class WalletManagerViewModel : ViewModel() {
 private val _state = MutableStateFlow(WalletManagerUiState(isLoading=true)); val state=_state.asStateFlow()
 private val gson=com.google.gson.Gson()
 private val listType=object:com.google.gson.reflect.TypeToken<List<WalletDto>>(){}.type
 fun load(isGuest:Boolean=false)=viewModelScope.launch {
  _state.value=_state.value.copy(isLoading=true,error=null)
  if(isGuest){val local=withContext(kotlinx.coroutines.Dispatchers.IO){CacheStore.readLocal("wallets")?.let{runCatching{gson.fromJson<List<WalletDto>>(it,listType)}.getOrNull()}}.orEmpty();_state.value=_state.value.copy(wallets=local,isLoading=false);return@launch}
  try{val rows=ApiClient.service.getWallets();_state.value=_state.value.copy(wallets=rows,isLoading=false);withContext(kotlinx.coroutines.Dispatchers.IO){CacheStore.write("wallets",gson.toJson(rows))}}catch(_:Exception){_state.value=_state.value.copy(isLoading=false,error="服务器繁忙，请稍后重试")}
 }
 fun add(req:WalletWriteRequest,isGuest:Boolean=false)=viewModelScope.launch {
  _state.value=_state.value.copy(isSaving=true,error=null)
  try{if(isGuest){val w=WalletDto(id=java.util.UUID.randomUUID().toString(),wallet_name=req.wallet_name,chain=req.chain,network_icon=when(req.chain){"EVM"->"E";"TRON"->"T";else->"S"},address=req.address);val rows=_state.value.wallets.filterNot{if(w.ecosystem==WalletEcosystem.EVM)it.address.equals(req.address,true) else it.address==req.address}+w;withContext(kotlinx.coroutines.Dispatchers.IO){CacheStore.writeLocal("wallets",gson.toJson(rows))};_state.value=_state.value.copy(wallets=rows,isSaving=false)}else{ApiClient.service.addWallet(req);val rows=ApiClient.service.getWallets();_state.value=_state.value.copy(wallets=rows,isSaving=false);withContext(kotlinx.coroutines.Dispatchers.IO){CacheStore.write("wallets",gson.toJson(rows))}}}catch(_:Exception){_state.value=_state.value.copy(isSaving=false,error="保存失败，请检查地址或稍后重试")}
 }
 fun delete(w:WalletDto,isGuest:Boolean=false)=viewModelScope.launch {try{val rows=_state.value.wallets.filterNot{it.id==w.id};if(isGuest)withContext(kotlinx.coroutines.Dispatchers.IO){CacheStore.writeLocal("wallets",gson.toJson(rows))}else{ApiClient.service.deleteWallet(w.id);withContext(kotlinx.coroutines.Dispatchers.IO){CacheStore.write("wallets",gson.toJson(rows))}};_state.value=_state.value.copy(wallets=rows,error=null)}catch(_:Exception){_state.value=_state.value.copy(error="删除失败，请稍后重试")}}
}
private fun detectEcosystem(a:String):String?=when(com.airdrophunter.app.data.AddressDetector.detect(a)){com.airdrophunter.app.data.DetectedNetwork.EVM->WalletEcosystem.EVM;com.airdrophunter.app.data.DetectedNetwork.TRON->WalletEcosystem.TRON;com.airdrophunter.app.data.DetectedNetwork.SOL->WalletEcosystem.SOL;else->null}

@Composable
fun WalletManagerScreen(modifier:Modifier=Modifier,onOpenBrowser:(String,WalletDto,List<WalletDto>,String)->Unit,isGuest:Boolean=false,vm:WalletManagerViewModel=viewModel()){
 val s by vm.state.collectAsStateWithLifecycle();var add by remember{mutableStateOf(false)};var addr by remember{mutableStateOf("")};var name by remember{mutableStateOf("")};var selected by remember{mutableStateOf<WalletDto?>(null)};val eco=remember(addr){detectEcosystem(addr)}
 LaunchedEffect(isGuest){vm.load(isGuest)}
 if(selected!=null){WalletDetailScreen(selected!!,onBack={selected=null});return}
 Column(modifier.fillMaxSize().background(AppColors.Background)){
  Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("我的钱包",color=AppColors.TextPrimary,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("管理地址，查看真实链上资产",color=AppColors.TextSecondary,style=MaterialTheme.typography.bodySmall)};Button(onClick={addr="";name="";add=true},colors=ButtonDefaults.buttonColors(containerColor=AppColors.Primary)){Icon(Icons.Default.Add,null);Text("添加钱包")}}
  s.error?.let{Text(it,Modifier.padding(horizontal=18.dp),color=AppColors.Danger)}
  when{ s.isLoading&&s.wallets.isEmpty()->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=AppColors.Primary)}
   s.wallets.isEmpty()->Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("还没有添加钱包",color=AppColors.TextPrimary,fontWeight=FontWeight.SemiBold);Text("添加公开地址后，即可查询对应网络的真实资产",color=AppColors.TextSecondary)}
   else->LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(s.wallets,key={it.id}){w->Card(Modifier.fillMaxWidth().clickable{selected=w},shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=AppColors.Surface),border=BorderStroke(1.dp,AppColors.Border)){Row(Modifier.padding(16.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){DynamicChainIcon(w.ecosystem,Modifier.size(42.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(w.wallet_name.ifBlank{"钱包"},color=AppColors.TextPrimary,fontWeight=FontWeight.SemiBold);Text(shortAddress(w.address),color=AppColors.TextSecondary,style=MaterialTheme.typography.bodySmall);Text(WalletEcosystem.label(w.ecosystem),color=AppColors.TextHelper,style=MaterialTheme.typography.labelSmall)};IconButton(onClick={vm.delete(w,isGuest)}){Icon(Icons.Default.DeleteOutline,"删除钱包",tint=AppColors.Danger)}}}}}
  }
 }
 if(add)AlertDialog(onDismissRequest={add=false},containerColor=AppColors.Surface,title={Text("添加公开钱包地址")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(addr,{addr=it},label={Text("钱包地址")},singleLine=true);OutlinedTextField(name,{name=it},label={Text("备注名称（可选）")},singleLine=true);Text(when(eco){WalletEcosystem.TRON->"TRON：第一版支持真实余额查询";WalletEcosystem.EVM->"EVM：余额查询接入中";WalletEcosystem.SOL->"Solana：余额查询接入中";else->"支持 EVM、TRON 和 Solana 公开地址"},color=AppColors.TextSecondary,style=MaterialTheme.typography.bodySmall);Text(if(isGuest)"地址仅保存在本地设备。" else "仅保存公开地址，不保存私钥或助记词。",color=AppColors.TextHelper,style=MaterialTheme.typography.bodySmall)}},confirmButton={Button(enabled=eco!=null&&!s.isSaving,onClick={val e=eco?:return@Button;vm.add(WalletWriteRequest(name.trim().ifBlank{"$e Wallet"},e,addr.trim()),isGuest);add=false},colors=ButtonDefaults.buttonColors(containerColor=AppColors.Primary)){Text("保存")}},dismissButton={TextButton(onClick={add=false}){Text("取消")}})
}
internal fun shortAddress(address:String)=if(address.length>13)address.take(6)+"…"+address.takeLast(4)else address
