# DataLens ProGuard rules.
# Compose + Material3 ship consumer rules; Room ships consumer rules.
# The app uses reflection-free code paths, so no extra keep rules are required.
# Keep names used by Room generated implementations (defensive):
-keep class * extends androidx.room.RoomDatabase
-dontwarn org.slf4j.**
