# Plasma release rules. Engine libraries (libVLC, libmpv) add their own keep rules when they arrive.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# smbj, for the network folder (brief 5.6). Its event bus, MBassador, finds the methods it calls by
# their @Handler annotation, and makes its own parts by reflection.
-keep class net.engio.mbassy.** { *; }
-keepclassmembers class com.hierynomus.** {
    @net.engio.mbassy.listener.Handler <methods>;
}
# Parts of them Plasma doesn't use name classes Android doesn't have: Kerberos, and EL filters.
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
