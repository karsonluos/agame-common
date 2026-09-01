package com.robining.games.ranking

import android.app.Activity

/**
 * Platform-neutral leaderboard contract. Implementations are responsible for
 * authenticating with their platform before invoking these operations.
 */
interface LeaderboardAdapter {
    val id: String

    /** Sends a score for the current player. A tag is optional platform metadata. */
    fun submitScore(
        activity: Activity,
        leaderboardId: String,
        score: Long,
        tag: String? = null,
        onComplete: (Result<Unit>) -> Unit = {},
    )

    /** Loads the current player's score and rank, or null when no score exists. */
    fun loadCurrentPlayerScore(
        activity: Activity,
        leaderboardId: String,
        timeSpan: LeaderboardTimeSpan = LeaderboardTimeSpan.ALL_TIME,
        collection: LeaderboardCollection = LeaderboardCollection.PUBLIC,
        onComplete: (Result<LeaderboardEntry?>) -> Unit,
    )

    /** Loads the leading entries for custom in-game leaderboard UI. */
    fun loadTopScores(
        activity: Activity,
        leaderboardId: String,
        maxResults: Int = DEFAULT_TOP_SCORE_COUNT,
        timeSpan: LeaderboardTimeSpan = LeaderboardTimeSpan.ALL_TIME,
        collection: LeaderboardCollection = LeaderboardCollection.PUBLIC,
        onComplete: (Result<LeaderboardPage>) -> Unit,
    )

    /** Opens the provider's native UI for one leaderboard. */
    fun showLeaderboard(
        activity: Activity,
        leaderboardId: String,
        options: LeaderboardUiOptions = LeaderboardUiOptions(),
        onComplete: (Result<Unit>) -> Unit = {},
    )

    /** Opens the provider's native UI listing all leaderboards. */
    fun showAllLeaderboards(
        activity: Activity,
        requestCode: Int = DEFAULT_UI_REQUEST_CODE,
        onComplete: (Result<Unit>) -> Unit = {},
    )

    companion object {
        const val DEFAULT_TOP_SCORE_COUNT = 25
        const val DEFAULT_UI_REQUEST_CODE = 9_201
    }
}

enum class LeaderboardTimeSpan {
    DAILY,
    WEEKLY,
    ALL_TIME,
}

enum class LeaderboardCollection {
    PUBLIC,
    FRIENDS,
}

data class LeaderboardUiOptions(
    val timeSpan: LeaderboardTimeSpan = LeaderboardTimeSpan.ALL_TIME,
    val collection: LeaderboardCollection = LeaderboardCollection.PUBLIC,
    val requestCode: Int = LeaderboardAdapter.DEFAULT_UI_REQUEST_CODE,
)

data class LeaderboardEntry(
    val rank: Long?,
    val displayRank: String?,
    val score: Long,
    val displayScore: String,
    val playerId: String?,
    val playerName: String?,
    val timestampMillis: Long,
)

data class LeaderboardPage(
    val leaderboardId: String,
    val leaderboardName: String?,
    val entries: List<LeaderboardEntry>,
)
