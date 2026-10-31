package dev.animeshvarma.aetherion.util

import dev.animeshvarma.aetherion.network.models.*

/**
 * Canned data so the profile + logs UI can be demonstrated without a live
 * session. Nothing here is fetched from Instagram; it exists purely so the
 * client screens are reviewable.
 */
object DemoMode {

    fun profile(): OwnProfileData = OwnProfileData(
        userId = "0",
        userIdB64 = "MA==",
        username = "demo_account",
        fullName = "Demo Account",
        biography = "Sample data rendered by Aetherion's Material 3 profile screen.\n" +
                "No network call was made to produce this view.",
        avatarUrl = "",
        mediaCount = 42,
        followerCount = 1284,
        followingCount = 317,
        isPrivate = false,
        isVerified = false,
        is2faEnabled = false,
        category = "Software",
        pronouns = listOf("they", "them"),
        externalUrl = "https://example.com",
        bioLinks = emptyList(),
        highlights = emptyList(),
        posts = emptyList(),
        textFeedCount = 0,
        executedFlowName = "Demo mode — canned data",
        executedSteps = listOf(
            "demo. no network requests issued",
            "demo. profile screen rendered from local fixture",
            "demo. logs tab reads from LoggingInterceptor ring buffer"
        )
    )
}