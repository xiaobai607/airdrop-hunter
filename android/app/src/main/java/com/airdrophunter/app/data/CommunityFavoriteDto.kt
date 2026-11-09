package com.airdrophunter.app.data

data class CommunityFavoriteStateDto(
    val project_official_id: String,
    val project_name: String,
    val is_favorite: Boolean,
    val updated_at: Long
)

data class CommunityFavoriteSyncRequest(
    val items: List<CommunityFavoriteStateDto>
)

internal fun mergeCommunityFavoriteStates(
    local: List<CommunityFavoriteStateDto>,
    remote: List<CommunityFavoriteStateDto>
): List<CommunityFavoriteStateDto> {
    val merged = remote.associateBy { it.project_official_id }.toMutableMap()
    local.forEach { item ->
        val cloud = merged[item.project_official_id]
        if (cloud == null || item.updated_at >= cloud.updated_at) {
            merged[item.project_official_id] = item
        }
    }
    return merged.values.sortedBy { it.project_official_id }
}
