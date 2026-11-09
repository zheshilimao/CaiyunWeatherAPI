# ==========================================================
# R8 规则：保护 WebView JS 桥与入口
# ==========================================================

# WebView 通过反射调用 @JavascriptInterface 标注的方法，
# 方法名被混淆后 H5 将无法再调用 addJavascriptInterface 暴露的桥。
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# JS 桥实现类整体保留（含字段与构造器），避免桥对象被裁剪。
-keep class com.api.caiyunweather.** { *; }

# 保留注解与源码行号/源文件名，便于崩溃堆栈还原（可选择映射文件上传）。
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# org.json 由 Android 平台运行时提供，不会出现在混淆警告中。
