# R8 is enabled for release builds. AndroidX, Compose and coroutines ship consumer rules;
# components declared in AndroidManifest.xml are kept automatically.

# LauncherViewModel is instantiated reflectively by the default ViewModelProvider factory.
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}
