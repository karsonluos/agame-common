# Map provider keys

The `amapmap`, `amaplocation`, and `mapboxmap` AARs do not contain provider keys.
The consuming Android application must supply them during manifest merging:

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        manifestPlaceholders["AMAP_API_KEY"] =
            providers.gradleProperty("AMAP_API_KEY").get()

        resValue(
            "string",
            "mapbox_access_token",
            providers.gradleProperty("MAPBOX_ACCESS_TOKEN").get(),
        )
    }
}
```

Store the values outside version control, for example in the consuming project's
`~/.gradle/gradle.properties` or CI secrets:

```properties
AMAP_API_KEY=your-amap-key
MAPBOX_ACCESS_TOKEN=your-mapbox-public-token
```

If a provider is not used, do not include that provider's AAR. A missing AMap
placeholder fails the consuming app's manifest merge. The Mapbox AAR contains an
empty default resource so it can be published independently; the consuming app's
`resValue` overrides that resource with its own token.

`MAPBOX_DOWNLOADS_TOKEN` is separate from `MAPBOX_ACCESS_TOKEN`: it authorizes Gradle
to download Mapbox SDK artifacts and must be configured in the consuming project's
Mapbox Maven repository credentials or CI environment.
