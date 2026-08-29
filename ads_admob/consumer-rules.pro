# Consumer ProGuard rules for the ads_admob AAR.
# AppLovin OMID optionally integrates Amazon Privacy Pass, which is absent from
# the standard Android runtime classpath.
-dontwarn com.amazon.privacypass.PrivacyPass
-dontwarn com.amazon.privacypass.VerificationContext
-dontwarn com.amazon.privacypass.callback.AttestAPICallback
