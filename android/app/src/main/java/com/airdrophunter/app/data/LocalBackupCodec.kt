package com.airdrophunter.app.data

import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object LocalBackupCodec {
 private val magic="AHDAT001".toByteArray(Charsets.US_ASCII)
 private const val VERSION:Byte=1
 private const val SALT=16
 private const val NONCE=12
 private const val ITERATIONS=210_000
 private const val MAX_BYTES=64*1024*1024
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
  require(password.length>=8) { "备份密码至少需要8位" }
  require(bytes.size in (magic.size+1+SALT+NONCE+16)..MAX_BYTES) { "备份文件大小异常或文件已损坏" }
  require(bytes.copyOfRange(0,magic.size).contentEquals(magic)) { "这不是 Airdrop Hunter 备份文件" }
  require(bytes[magic.size]==VERSION) { "备份文件版本不受支持" }
  val headerSize=magic.size+1+SALT+NONCE; val header=bytes.copyOfRange(0,headerSize)
  val salt=bytes.copyOfRange(magic.size+1,magic.size+1+SALT)
  val nonce=bytes.copyOfRange(magic.size+1+SALT,headerSize)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE,derive(password,salt),GCMParameterSpec(128,nonce)); cipher.updateAAD(header)
  val compressed=try { cipher.doFinal(bytes.copyOfRange(headerSize,bytes.size)) }
   catch (_:AEADBadTagException) { throw IllegalArgumentException("密码错误或备份文件已损坏") }
  val json=GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
   val out=ByteArrayOutputStream(); val buffer=ByteArray(8192); var total=0
   while(true) { val n=input.read(buffer); if(n<0) break; total+=n; require(total<=MAX_BYTES) { "备份解压后超出安全大小限制" }; out.write(buffer,0,n) }
   out.toByteArray()
  }
  val snapshot=try { gson.fromJson(String(json,Charsets.UTF_8),LocalBackupSnapshot::class.java) }
   catch(e:Exception) { throw IllegalArgumentException("备份内容无法识别或已损坏",e) }
   ?:throw IllegalArgumentException("备份内容为空")
  validate(snapshot); return snapshot
 }

 private fun validate(s:LocalBackupSnapshot) {
  require(s.formatVersion==LocalBackupSnapshot.CURRENT_VERSION) { "备份文件版本不受支持" }
  require(s.createdAt>0 && (s.sourceScope=="guest" || s.sourceScope.startsWith("user:")) ) { "备份文件信息无效" }
  require(s.totalRecords<=LocalBackupSnapshot.MAX_RECORDS) { "备份记录数量超过安全限制" }
  val prefixes=if(s.sourceScope=="guest") listOf("guest:","signed-out:") else listOf("${s.sourceScope}:","${s.sourceScope.removePrefix("user:")}:")
  require(s.cachedPayloads.all { row -> prefixes.any { row.cacheKey.startsWith(it) } }) { "备份文件包含不允许的缓存数据" }
 }
 private fun derive(password:String,salt:ByteArray):SecretKeySpec {
  val spec=PBEKeySpec(password.toCharArray(),salt,ITERATIONS,256)
  return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") }
   finally { spec.clearPassword() }
 }
}
