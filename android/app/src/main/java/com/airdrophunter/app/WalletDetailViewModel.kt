package com.airdrophunter.app
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airdrophunter.app.data.TronTokenBalance
import com.airdrophunter.app.data.TronscanRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class WalletDetailUiState(val address:String="",val isLoading:Boolean=false,val errorMessage:String?=null,val trxBalance:BigDecimal?=null,val totalUsd:BigDecimal?=null,val tokenList:List<TronTokenBalance> = emptyList(),val lastUpdatedEpochSeconds:Long?=null)
class WalletDetailViewModel(private val repository:TronscanRepository=TronscanRepository()):ViewModel(){
 private val _uiState=MutableStateFlow(WalletDetailUiState());val uiState=_uiState.asStateFlow()
 private var requestJob:Job?=null;private var queuedJob:Job?=null;private var queued=false;private var lastRequestAt=0L;private val interval=5000L
 fun loadWallet(address:String){val a=address.trim();if(a.isBlank()){_uiState.value=WalletDetailUiState(errorMessage="钱包地址为空，无法查询链上资产。");return};_uiState.value=_uiState.value.copy(address=a);startRequest(a)}
 private fun startRequest(a:String){requestJob?.cancel();requestJob=viewModelScope.launch{_uiState.value=_uiState.value.copy(address=a,isLoading=true,errorMessage=null);lastRequestAt=System.currentTimeMillis();try{val r=repository.fetchWalletAssets(a);_uiState.value=_uiState.value.copy(address=r.address,isLoading=false,errorMessage=null,trxBalance=r.trxBalance,totalUsd=r.totalUsd,tokenList=r.tokenList,lastUpdatedEpochSeconds=r.lastUpdatedEpochSeconds)}catch(c:CancellationException){throw c}catch(e:Exception){_uiState.value=_uiState.value.copy(address=a,isLoading=false,errorMessage=e.message?:"查询 TRON 链上资产失败。")}finally{if(queued)schedule(a)}}}
 fun refresh(){val a=_uiState.value.address;if(a.isBlank())return;queued=true;if(requestJob?.isActive!=true)schedule(a)}
 private fun schedule(a:String){if(queuedJob?.isActive==true)return;queuedJob=viewModelScope.launch{val wait=(interval-(System.currentTimeMillis()-lastRequestAt)).coerceAtLeast(0);if(wait>0)delay(wait);if(queued&&_uiState.value.address==a){queued=false;startRequest(a)}}}
 fun clear(){requestJob?.cancel();queuedJob?.cancel();requestJob=null;queuedJob=null;queued=false;_uiState.value=WalletDetailUiState()}
 override fun onCleared(){requestJob?.cancel();queuedJob?.cancel();super.onCleared()}
}
