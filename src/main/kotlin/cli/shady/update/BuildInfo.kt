package cli.shady.update

object BuildInfo {
    val VERSION: String = BuildInfo::class.java.`package`.implementationVersion ?: "development"
}
