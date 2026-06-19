rootProject.name = "shady"
if (providers.gradleProperty("skipIntellijPlugin").orNull != "true") {
    include("intellij-plugin")
}
