# Leaderboards

`iranking` provides the platform-neutral leaderboard contract. Add `gpgsranking`
to use Google Play Games Services v2.

The consuming app must configure its Play Games Services application ID:

```kotlin
android {
    defaultConfig {
        resValue(
            "string",
            "games_app_id",
            providers.gradleProperty("GOOGLE_PLAY_GAMES_APP_ID").get(),
        )
    }
}
```

Keep `GOOGLE_PLAY_GAMES_APP_ID` in the consuming app's Gradle properties or CI
configuration. Configure the corresponding Android app, OAuth credentials, and
leaderboards in Play Console before calling the adapter.

```kotlin
val rankings = GooglePlayGamesLeaderboardAdapter()

rankings.submitScore(activity, "leaderboard_id", score = 12_500L)
rankings.showLeaderboard(activity, "leaderboard_id")
rankings.showAllLeaderboards(activity)
```
