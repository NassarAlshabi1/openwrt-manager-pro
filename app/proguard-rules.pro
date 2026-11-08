# R8 is disabled for v1 (minifyEnabled=false). Rules below are kept for future enabling.

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp / Okio ship consumer rules. Nothing extra required.
