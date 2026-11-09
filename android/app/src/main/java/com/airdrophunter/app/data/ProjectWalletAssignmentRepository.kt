package com.airdrophunter.app.data

/**
 * Keeps guest assignments on-device and synchronizes signed-in assignments to the
 * authenticated account-private API. No wallet signing material is involved.
 */
class ProjectWalletAssignmentRepository(private val api: ApiService) {
    suspend fun load(projectKey: String, guest: Boolean): Set<String> {
        require(projectKey.isNotBlank() && projectKey.length <= 160) { "项目标识无效" }
        if (guest) return CacheStore.readProjectWalletAssignments(projectKey)
        val result = api.getProjectWalletAssignments(projectKey).wallet_ids.toSet()
        CacheStore.saveProjectWalletAssignments(projectKey, result)
        return result
    }

    suspend fun save(projectKey: String, walletIds: Set<String>, guest: Boolean): Set<String> {
        require(projectKey.isNotBlank() && projectKey.length <= 160) { "项目标识无效" }
        require(walletIds.size <= 100 && walletIds.all { it.isNotBlank() && it.length <= 80 }) {
            "钱包关联数量或标识无效"
        }
        val saved = if (guest) walletIds else
            api.replaceProjectWalletAssignments(projectKey, ProjectWalletAssignmentDto(projectKey, walletIds.toList())).wallet_ids.toSet()
        CacheStore.saveProjectWalletAssignments(projectKey, saved)
        return saved
    }
}
