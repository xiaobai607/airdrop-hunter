package com.airdrophunter.app.data

import com.google.gson.annotations.SerializedName

data class LocalBackupSnapshot(
 @SerializedName("app_name") val appName:String=APP_NAME, val formatVersion:Int=CURRENT_VERSION, val createdAt:Long, val sourceScope:String,
 val cachedPayloads:List<CachedPayload> = emptyList(),
 val pendingBills:List<PendingBillEntity> = emptyList(),
 val pnlRecords:List<PnlRecordEntity> = emptyList(),
 val projectRatings:List<ProjectRatingEntity> = emptyList(),
 val userProjects:List<UserProjectEntity> = emptyList(),
 val userInteractions:List<UserInteractionEntity> = emptyList(),
 val communityOutbox:List<CommunitySyncOutboxEntity> = emptyList(),
 val communityFavorites:List<CommunityFavoriteEntity> = emptyList(),
 val projectWalletAssignments:List<ProjectWalletAssignmentEntity> = emptyList()
) {
 val totalRecords:Int get() = cachedPayloads.size+pendingBills.size+pnlRecords.size+projectRatings.size+
  userProjects.size+userInteractions.size+communityOutbox.size+communityFavorites.size+projectWalletAssignments.size
 companion object { const val APP_NAME="AirdropHunter"; const val CURRENT_VERSION=2; const val MAX_RECORDS=100_000 }
}
