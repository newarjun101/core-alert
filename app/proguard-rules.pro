# CoreAlert R8 rules

# Keep JNI native method names (Android bindings resolve them reflectively)
-keepclasseswithmembernames class * { native <methods>; }

