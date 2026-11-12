package com.airdrophunter.app.data

import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object LocalBackupCodec {
 private val magic="AHDAT001".toByteArray(Charsets.US_ASCII)
 private const val VERSION:Byte=2
 private const val SALT=16
 private const val NONCE=12
 private const val ITERATIONS=210_000
 private const val MAX_BYTES=64*1024*1024
 private const val IMPORT_FAILURE="文件损坏或密码错误，导入失败"
 private val random=SecureRandom()
 private val gson=Gson()

 fun encode(snapshot:LocalBackupSnapshot,password:String):ByteArray {
  require(password.length>=8) { "备份密码至少需要8位" }; validate(snapshot)
  val json=gson.toJson(snapshot).toByteArray(Charsets.UTF_8)
  require(json.size<=MAX_BYTES) { "备份数据过大，暂不支持导出" }
  val compressed=ByteArrayOutputStream().use { out -> GZIPOutputStream(out).use { it.write(json) }; out.toByteArray() }
  val salt=ByteArray(SALT).also(random::nextBytes); val nonce=ByteArray(NONCE).also(random::nextBytes)
  val header=magic+byteArrayOf(VERSION)+salt+nonce
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.ENCRYPT_MODE,derive(password,salt),GCMParameterSpec(128,nonce)); cipher.updateAAD(header)
  val result=header+cipher.doFinal(compressed)
  require(result.size<=MAX_BYTES) { "备份文件过大，无法保存" }; return result
 }

 fun decode(bytes:ByteArray,password:String):LocalBackupSnapshot {
  return try {
   require(password.length>=8)
   require(bytes.size in (magic.size+1+SALT+NONCE+16)..MAX_BYTES)
   require(bytes.copyOfRange(0,magic.size).contentEquals(magic))
   require(bytes[magic.size]==VERSION)
   val headerSize=magic.size+1+SALT+NONCE; val header=bytes.copyOfRange(0,headerSize)
   val salt=bytes.copyOfRange(magic.size+1,magic.size+1+SALT)
   val nonce=bytes.copyOfRange(magic.size+1+SALT,headerSize)
   val cipher=Cipher.getInstance("AES/GCM/NoPadding")
   cipher.init(Cipher.DECRYPT_MODE,derive(password,salt),GCMParameterSpec(128,nonce)); cipher.updateAAD(header)
   val compressed=cipher.doFinal(bytes.copyOfRange(headerSize,bytes.size))
   val json=GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
    val out=ByteArrayOutputStream(); val buffer=ByteArray(8192); var total=0
    while(true) { val n=input.read(buffer); if(n<0) break; total+=n; require(total<=MAX_BYTES); out.write(buffer,0,n) }
    out.toByteArray()
   }
   val snapshot=gson.fromJson(String(json,Charsets.UTF_8),LocalBackupSnapshot::class.java)
    ?:throw IllegalArgumentException("empty backup")
   validate(snapshot); snapshot
  } catch (failure:Exception) {
   throw IllegalArgumentException(IMPORT_FAILURE,failure)
  }
 }

 private fun validate(s:LocalBackupSnapshot) {
  require(s.appName==LocalBackupSnapshot.APP_NAME) { "invalid app name" }
  require(s.formatVersion==LocalBackupSnapshot.CURRENT_VERSION) { "unsupported format" }
  require(s.createdAt>0 && (s.sourceScope=="guest" || s.sourceScope.startsWith("user:")) ) { "invalid backup scope" }
  require(s.totalRecords<=LocalBackupSnapshot.MAX_RECORDS) { "too many records" }
  val prefixes=if(s.sourceScope=="guest") listOf("guest:","signed-out:") else listOf("${s.sourceScope}:","${s.sourceScope.removePrefix("user:")}:")
  require(s.cachedPayloads.all { row -> prefixes.any { row.cacheKey.startsWith(it) } }) { "invalid cache scope" }
  require(s.web3Drafts.orEmpty().all { it.accountScope == s.sourceScope }) { "invalid Web3 draft scope" }
 }
 private fun derive(password:String,salt:ByteArray):SecretKeySpec {
  val spec=PBEKeySpec(password.toCharArray(),salt,ITERATIONS,256)
  return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") }
   finally { spec.clearPassword() }
 }
}
