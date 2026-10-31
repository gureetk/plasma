# Plasma release rules. libmpv's AAR brings its own keep rule; libVLC's brings none (below).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# libVLC (brief 5.4). Its native library finds its Java side by name as it loads: classes, the
# fields it writes, and the methods it calls back, such as Media.createVideoTrackFromNative and the
# Dialog class, which nothing in Java calls. Without these, R8 removes them and libVLC can't load,
# so the first channel played would stop Plasma.
-keep class org.videolan.libvlc.** { *; }

# smbj, for the network folder (brief 5.6). Its event bus, MBassador, finds the methods it calls by
# their @Handler annotation, and makes its own parts by reflection.
-keep class net.engio.mbassy.** { *; }
-keepclassmembers class com.hierynomus.** {
    @net.engio.mbassy.listener.Handler <methods>;
}
# Parts of them Plasma doesn't use name classes Android doesn't have: Kerberos, and EL filters.
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
