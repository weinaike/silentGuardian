fun main() {
    val managedApps = setOf("com.google.android.apps.bard")
    val aliases = mapOf("com.google.android.googlequicksearchbox" to "com.google.android.apps.bard")

    fun getCanonical(pkg: String) = aliases.getOrDefault(pkg, pkg)

    // Events: 
    // 1. bard resumed
    // 2. googlequicksearchbox paused
}
