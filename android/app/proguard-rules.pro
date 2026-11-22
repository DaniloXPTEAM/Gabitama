-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.novoagentehb.app.agent.** { *; }
-keep class com.novoagentehb.app.data.local.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
