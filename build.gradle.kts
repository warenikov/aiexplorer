plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "ru.aiforopenide"
version = "0.6.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local("/Applications/OpenIDE.app/Contents")
        bundledPlugin("org.jetbrains.plugins.terminal")
        bundledPlugin("com.intellij.modules.jcef")
    }
}

kotlin {
    jvmToolchain(25)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }
}

tasks {
    instrumentCode {
        enabled = false
    }
    patchPluginXml {
        pluginDescription = """
            AI Explorer for OpenIDE. Run the full interactive Kimi Code CLI
            in embedded terminals and resume previous sessions.
        """.trimIndent()
    }
}
