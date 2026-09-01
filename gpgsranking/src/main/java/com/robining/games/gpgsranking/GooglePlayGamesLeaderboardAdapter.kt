package com.robining.games.gpgsranking

import android.app.Activity
import com.google.android.gms.games.LeaderboardsClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.leaderboard.LeaderboardScore
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.robining.games.ranking.LeaderboardAdapter
import com.robining.games.ranking.LeaderboardCollection
import com.robining.games.ranking.LeaderboardEntry
import com.robining.games.ranking.LeaderboardPage
import com.robining.games.ranking.LeaderboardTimeSpan
import com.robining.games.ranking.LeaderboardUiOptions

/** Google Play Games Services v2 implementation of [LeaderboardAdapter]. */
class GooglePlayGamesLeaderboardAdapter : LeaderboardAdapter {
    override val id: String = "google-play-games"

    override fun submitScore(
        activity: Activity,
        leaderboardId: String,
        score: Long,
        tag: String?,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        val client = PlayGames.getLeaderboardsClient(activity)
        val task = if (tag == null) {
            client.submitScoreImmediate(leaderboardId, score)
        } else {
            client.submitScoreImmediate(leaderboardId, score, tag)
        }
        task.addOnSuccessListener { onComplete(Result.success(Unit)) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    override fun loadCurrentPlayerScore(
        activity: Activity,
        leaderboardId: String,
        timeSpan: LeaderboardTimeSpan,
        collection: LeaderboardCollection,
        onComplete: (Result<LeaderboardEntry?>) -> Unit,
    ) {
        PlayGames.getLeaderboardsClient(activity)
            .loadCurrentPlayerLeaderboardScore(leaderboardId, timeSpan.toGoogle(), collection.toGoogle())
            .addOnSuccessListener { data -> onComplete(Result.success(data.get()?.toEntry())) }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    override fun loadTopScores(
        activity: Activity,
        leaderboardId: String,
        maxResults: Int,
        timeSpan: LeaderboardTimeSpan,
        collection: LeaderboardCollection,
        onComplete: (Result<LeaderboardPage>) -> Unit,
    ) {
        PlayGames.getLeaderboardsClient(activity)
            .loadTopScores(leaderboardId, timeSpan.toGoogle(), collection.toGoogle(), maxResults, false)
            .addOnSuccessListener { data ->
                val scores = data.get()
                if (scores == null) {
                    onComplete(Result.success(LeaderboardPage(leaderboardId, null, emptyList())))
                    return@addOnSuccessListener
                }
                try {
                    val entries = scores.scores.map { it.toEntry() }
                    onComplete(
                        Result.success(
                            LeaderboardPage(
                                leaderboardId = scores.leaderboard?.leaderboardId ?: leaderboardId,
                                leaderboardName = scores.leaderboard?.displayName,
                                entries = entries,
                            ),
                        ),
                    )
                } finally {
                    scores.release()
                }
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    override fun showLeaderboard(
        activity: Activity,
        leaderboardId: String,
        options: LeaderboardUiOptions,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        PlayGames.getLeaderboardsClient(activity)
            .getLeaderboardIntent(leaderboardId, options.timeSpan.toGoogle(), options.collection.toGoogle())
            .addOnSuccessListener { intent ->
                activity.startActivityForResult(intent, options.requestCode)
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    override fun showAllLeaderboards(
        activity: Activity,
        requestCode: Int,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        PlayGames.getLeaderboardsClient(activity)
            .allLeaderboardsIntent
            .addOnSuccessListener { intent ->
                activity.startActivityForResult(intent, requestCode)
                onComplete(Result.success(Unit))
            }
            .addOnFailureListener { onComplete(Result.failure(it)) }
    }

    private fun LeaderboardTimeSpan.toGoogle(): Int = when (this) {
        LeaderboardTimeSpan.DAILY -> LeaderboardVariant.TIME_SPAN_DAILY
        LeaderboardTimeSpan.WEEKLY -> LeaderboardVariant.TIME_SPAN_WEEKLY
        LeaderboardTimeSpan.ALL_TIME -> LeaderboardVariant.TIME_SPAN_ALL_TIME
    }

    private fun LeaderboardCollection.toGoogle(): Int = when (this) {
        LeaderboardCollection.PUBLIC -> LeaderboardVariant.COLLECTION_PUBLIC
        LeaderboardCollection.FRIENDS -> LeaderboardVariant.COLLECTION_FRIENDS
    }

    private fun LeaderboardScore.toEntry() = LeaderboardEntry(
        rank = rank.takeUnless { it == LeaderboardScore.LEADERBOARD_RANK_UNKNOWN.toLong() },
        displayRank = displayRank,
        score = rawScore,
        displayScore = displayScore,
        playerId = scoreHolder?.playerId,
        playerName = scoreHolderDisplayName,
        timestampMillis = timestampMillis,
    )
}
