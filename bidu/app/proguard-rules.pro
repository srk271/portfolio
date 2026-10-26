# Vosk is accessed through JNA; both must survive shrinking untouched.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# Room entities/DAOs are referenced by generated code; keep annotated classes readable.
-keep class dev.bidu.app.data.room.** { *; }

# Keep enum names that are persisted by name (tool outcomes, states, languages).
-keepclassmembers enum dev.bidu.core.** { *; }
