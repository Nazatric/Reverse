# The Gadget — release ProGuard/R8 rules.
#
# Most of the app is Kotlin + Compose, which AGP keeps correctly by default. These rules cover the
# reflection-driven libraries (Room, kotlinx.serialization, HiveMQ/Netty, Media3) so the shrunk
# release build behaves exactly like the debug build.

# kotlinx.serialization — keep the generated serializers and @Serializable metadata.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.thegadget.app.**$$serializer { *; }
-keepclassmembers class com.thegadget.app.** { *** Companion; }
-keepclasseswithmembers class com.thegadget.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Room — generated DAO/Database implementations are referenced reflectively.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer — reflection into effect and session components.
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# HiveMQ MQTT client — built on Netty; keep the service loaders and transport classes.
-dontwarn com.hivemq.client.**
-dontwarn org.reactivestreams.**
-dontwarn io.netty.**
-keep class com.hivemq.client.** { *; }

# Guava ListenableFuture (used by Media3 + HiveMQ).
-dontwarn com.google.common.util.concurrent.**

# BlockHound is referenced transitively but never used at runtime.
-dontwarn reactor.blockhound.**
