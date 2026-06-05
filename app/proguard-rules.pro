# Shizuku 相关类不混淆
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# 保持 ShizukuProvider
-keep class rikka.shizuku.ShizukuProvider { *; }

# 保持反射调用的 Shizuku.newProcess 方法
-keepclassmembers class rikka.shizuku.Shizuku {
    private static java.lang.Process newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}

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
