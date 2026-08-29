# Map provider keys

The `amapmap`, `amaplocation`, and `mapboxmap` AARs do not contain provider keys.
The consuming Android application must supply them during manifest merging:

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        manifestPlaceholders["AMAP_API_KEY"] = providers.gradleProperty("AMAP_API_KEY").get()
        manifestPlaceholders["MAPBOX_ACCESS_TOKEN"] = providers.gradleProperty("MAPBOX_ACCESS_TOKEN").get()
    }
}
```

Store the values outside version control, for example in the consuming project's
`~/.gradle/gradle.properties` or CI secrets:

```properties
AMAP_API_KEY=your-amap-key
MAPBOX_ACCESS_TOKEN=your-mapbox-public-token
```

If a provider is not used, do not include that provider's AAR. A missing placeholder
for an included AAR fails the consuming app's manifest merge so a key cannot be
accidentally omitted.

`MAPBOX_DOWNLOADS_TOKEN` is separate from `MAPBOX_ACCESS_TOKEN`: it authorizes Gradle
to download Mapbox SDK artifacts and must be configured in the consuming project's
Mapbox Maven repository credentials or CI environment.
