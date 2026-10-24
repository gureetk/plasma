# kotlin-logging, which the Jellyfin SDK logs with, can hand its logs to SLF4J. Plasma doesn't ship
# SLF4J: JellyfinClient points kotlin-logging at Android's own log instead.
-dontwarn org.slf4j.**
