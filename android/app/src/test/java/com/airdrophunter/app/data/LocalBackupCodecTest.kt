package com.airdrophunter.app.data

import org.junit.Assert.assertEquals
import com.google.gson.Gson
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalBackupCodecTest {
 private fun sample()=LocalBackupSnapshot(
  createdAt=1_800_000_000_000L,sourceScope="guest",
  cachedPayloads=listOf(CachedPayload("guest:local:wallets","""[{"id":"w1"}]""",123L)),
  pendingBills=listOf(PendingBillEntity("bill-1","0xabc","0xwallet",1.25,"manual",456L,accountScope="guest")),
  web3Drafts=listOf(Web3TransactionDraftEntity(
   id="web3-1", accountScope="guest", origin="https://example.org", chainId=42161L,
   walletAddress="0x1111111111111111111111111111111111111111",
   toAddress="0x2222222222222222222222222222222222222222",
   valueWei="100", dataHex="0x", callbackState="a".repeat(48),
   walletConnectRequestId=7L, txHash="0x"+"a".repeat(64),
   status=Web3DraftStatus.WAITING_RECEIPT, estimatedGas="21000",
   gasUsed=null, effectiveGasPrice=null, l1Fee=null, totalFeeWei=null,
   receiptStatus=null, lastError=null, createdAt=789L, updatedAt=790L
  ))
 )
 @Test fun jsonUsesRequiredSnakeCaseAppIdentity() {
  val json=Gson().toJson(sample())
  assertTrue(json.contains("\"app_name\":\"AirdropHunter\""))
  assertTrue(!json.contains("\"appName\""))
 }
 @Test fun encryptedBackupRoundTripsWithoutPlaintext() {
  val bytes=LocalBackupCodec.encode(sample(),"correct-horse-1")
  val restored=LocalBackupCodec.decode(bytes,"correct-horse-1")
  assertEquals(LocalBackupSnapshot.APP_NAME,restored.appName)
  assertEquals(sample().createdAt,restored.createdAt)
  assertEquals(sample().cachedPayloads,restored.cachedPayloads)
  assertEquals(sample().pendingBills,restored.pendingBills)
  assertEquals(sample().web3Drafts,restored.web3Drafts)
  assertEquals(-1,String(bytes,Charsets.ISO_8859_1).indexOf("0xwallet"))
 }
 @Test fun wrongPasswordIsRejected() {
  val error=assertThrows(IllegalArgumentException::class.java) { LocalBackupCodec.decode(LocalBackupCodec.encode(sample(),"correct-horse-1"),"incorrect-horse") }
  assertEquals("文件损坏或密码错误，导入失败",error.message)
 }
 @Test fun shortImportPasswordUsesTheSameSafeFailureMessage() {
  val error=assertThrows(IllegalArgumentException::class.java) {
   LocalBackupCodec.decode(LocalBackupCodec.encode(sample(),"correct-horse-1"),"short")
  }
  assertEquals("文件损坏或密码错误，导入失败",error.message)
 }
 @Test fun tamperedFileIsRejected() {
  val bytes=LocalBackupCodec.encode(sample(),"correct-horse-1"); bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
  val error=assertThrows(IllegalArgumentException::class.java) { LocalBackupCodec.decode(bytes,"correct-horse-1") }
  assertEquals("文件损坏或密码错误，导入失败",error.message)
 }
 @Test fun wrongAppNameIsRejectedBeforeExport() {
  assertThrows(IllegalArgumentException::class.java) { LocalBackupCodec.encode(sample().copy(appName="OtherApp"),"correct-horse-1") }
 }
 @Test fun invalidScopeAndShortPasswordAreRejected() {
  assertThrows(IllegalArgumentException::class.java) { LocalBackupCodec.encode(sample().copy(sourceScope="invalid-session"),"correct-horse-1") }
  assertThrows(IllegalArgumentException::class.java) {
   LocalBackupCodec.encode(sample().copy(web3Drafts=sample().web3Drafts?.map { it.copy(accountScope="user:other") }),"correct-horse-1")
  }
  assertThrows(IllegalArgumentException::class.java) { LocalBackupCodec.encode(sample(),"short") }
 }
}
