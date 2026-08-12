# Add project specific ProGuard rules here.
-keep class rikka.shizuku.** { *; }
-keep class com.buge.appmanager.** { *; }

# Keep signature validation classes from being obfuscated
-keep class com.buge.appmanager.util.SignatureValidator { *; }
-keep class com.buge.appmanager.ui.SignatureWarningDialog { *; }

# Keep the expected SHA-256 constant from being stripped
-keepclassmembers class com.buge.appmanager.util.SignatureValidator {
    private static final java.lang.String EXPECTED_SHA256;
}