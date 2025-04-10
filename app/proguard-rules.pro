# Add project specific ProGuard rules here.

# Keep the GPUImage library
-keep class jp.co.cyberagent.android.gpuimage.** { *; }

# Keep the application classes
-keep class com.imageeditor.** { *; }

# Keep the FilterType enum
-keepclassmembers class com.imageeditor.FilterType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# General Android rules
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
