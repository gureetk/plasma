// The XMLTV guide: a streaming reader and the matching of guide channels to Plasma's (brief 5.3, 6.2).
plugins {
    id("plasma.jvm.library")
}

dependencies {
    // Android ships an XmlPullParser implementation; the JVM (tests) needs one supplied.
    compileOnly(libs.kxml2)

    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
}
