# CPRP Launcher Proguard rules
-keep class com.cprp.launcher.NativeBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
