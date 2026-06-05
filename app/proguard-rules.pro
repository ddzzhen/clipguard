# Shizuku 相关类不混淆
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class com.clipguard.app.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.clipguard.app.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 保持数据模型
-keep class com.clipguard.app.model.** { *; }

# 保持服务类
-keep class com.clipguard.app.service.ClipboardGuardService { *; }
-keep class com.clipguard.app.service.ClipboardAccessibilityService { *; }

# 保持 Shizuku UserService 和接口
-keep class com.clipguard.app.shizuku.ShizukuUserService { *; }
-keep class com.clipguard.app.shizuku.IClipGuardInterface { *; }
