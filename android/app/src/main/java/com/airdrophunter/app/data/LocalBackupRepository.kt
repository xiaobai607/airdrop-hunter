package com.airdrophunter.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object LocalBackupRepository {
 private const val MAX_BYTES=64*1024*1024
 suspend fun writeBackup(context:Context,uri:Uri,password:String):Long=withContext(Dispatchers.IO) {
  val snapshot=CacheStore.exportLocalBackup(); val bytes=LocalBackupCodec.encode(snapshot,password)
  val output=context.contentResolver.openOutputStream(uri,"w") ?: error("无法创建备份文件")
  output.use { it.write(bytes); it.flush() }; snapshot.createdAt
 }
 suspend fun readBackup(context:Context,uri:Uri,password:String):LocalBackupSnapshot=withContext(Dispatchers.IO) {
  val input=context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
  val bytes=input.use { stream ->
   val out=ByteArrayOutputStream(); val buffer=ByteArray(8192); var total=0
   while(true) { val n=stream.read(buffer); if(n<0) break; total+=n; require(total<=MAX_BYTES) { "备份文件超过64MB安全限制" }; out.write(buffer,0,n) }
   out.toByteArray()
  }
  LocalBackupCodec.decode(bytes,password)
 }
 suspend fun restore(snapshot:LocalBackupSnapshot)=CacheStore.restoreLocalBackup(snapshot)
}
