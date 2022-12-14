import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.tasks.BuildSearchableOptionsTask
import org.jetbrains.intellij.tasks.PublishPluginTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.intellij.tasks.RunPluginVerifierTask.FailureLevel
import org.jetbrains.intellij.tasks.VerifyPluginTask

fun properties(key: String) = project.findProperty(key).toString()

plugins {
  // gradle-intellij-plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
  id("org.jetbrains.intellij") version "1.10.0"
  // gradle-changelog-plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
  id("org.jetbrains.changelog") version "1.3.1"

  id("intellijbsp.kotlin-conventions")
}

group = properties("pluginGroup")
version = properties("pluginVersion")

dependencies {
  implementation(project(":magicmetamodel"))
  testImplementation(project(":test-utils"))
  implementation("ch.epfl.scala:bsp4j:2.1.0-M3")
  implementation("com.google.code.gson:gson:2.10")

  testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
  testImplementation("io.kotest:kotest-assertions-core:5.5.4")
}

tasks.runIde{
  jvmArgs("-Xmx8000m")
}

// Configure gradle-intellij-plugin plugin.
// Read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
  pluginName.set(properties("pluginName"))
  version.set(properties("platformVersion"))
  type.set(properties("platformType"))
  downloadSources.set(properties("platformDownloadSources").toBoolean())
  updateSinceUntilBuild.set(true)

  // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
  plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

// Configure gradle-changelog-plugin plugin.
// Read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
  version.set(properties("pluginVersion"))
  groups.set(emptyList())
}

subprojects {
  apply(plugin = "org.jetbrains.intellij")

  intellij {
    version.set(properties("platformVersion"))
  }

  tasks.withType(PublishPluginTask::class.java) {
    enabled = false
  }

  tasks.withType(VerifyPluginTask::class.java) {
    enabled = false
  }

  tasks.withType(BuildSearchableOptionsTask::class.java) {
    enabled = false
  }

  tasks.withType(RunIdeTask::class.java) {
    enabled = false
  }
}
repositories {
  mavenCentral()
}

tasks {
  patchPluginXml {
    version.set(properties("pluginVersion"))
    sinceBuild.set(properties("pluginSinceBuild"))
    untilBuild.set(properties("pluginUntilBuild"))

    // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
    pluginDescription.set(
      File(projectDir, "README.md").readText().lines().run {
        val start = "<!-- Plugin description -->"
        val end = "<!-- Plugin description end -->"

        if (!containsAll(listOf(start, end))) {
          throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
        }
        subList(indexOf(start) + 1, indexOf(end))
      }.joinToString("\n").run { markdownToHTML(this) }
    )

    // Get the latest available change notes from the changelog file
    changeNotes.set(provider { changelog.getLatest().toHTML() })
  }

  runPluginVerifier {
    ideVersions.set(properties("pluginVerifierIdeVersions").split(',').map(String::trim).filter(String::isNotEmpty))
    failureLevel.set(setOf(
      FailureLevel.COMPATIBILITY_PROBLEMS,
      FailureLevel.NOT_DYNAMIC
    ))
  }

  publishPlugin {
    dependsOn("patchChangelog")
    token.set(System.getenv("PUBLISH_TOKEN"))
    // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
    // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
    // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
    channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
  }
}
