package cc.polyfrost.gradle.util

import cc.polyfrost.gradle.multiversion.Platform
import gradle.kotlin.dsl.accessors._11d1d69a77e50fb2b4b174f119312f10.loom
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

fun Project.setupPublishing(mavenUrl: String, mavenName: String, username: String, password: String) {
    if (mavenUrl.isBlank() || mavenName.isBlank() || username.isBlank() || password.isBlank()) {
        throw GradleException("None of the maven publishing properties can be blank.")
    }
    extensions.configure<PublishingExtension>("publishing") {
        publications {
            register<MavenPublication>("maven") {
                from(components.getByName("java"))

                pluginManager.withPlugin("cc.polyfrost.multi-version") {
                    val platform: Platform by extensions
                    val baseArtifactId = (if (parent == rootProject) rootProject.name.toLowerCase() else null)
                        ?: project.findProperty("baseArtifactId")?.toString()
                        ?: throw GradleException("No default base maven artifact id found. Set `baseArtifactId` in the `gradle.properties` file of the multi-version-root project.")
                    artifactId = "$baseArtifactId-$platform"
                }
            }
        }

        repositories {
            maven(mavenUrl) {
                name = mavenName
                credentials {
                    this@credentials.username = username
                    this@credentials.password = password
                }
            }
        }
    }

    pluginManager.withPlugin("cc.polyfrost.multi-version") {
        val platform: Platform by extensions

        if (platform.isLegacyForge) {
            // For legacy Forge we publish the dev jar rather than the srg mapped one, so the consumers do not need to deobf
            // (does not sound like best practise but that is how most mods do it and there isn't really any diversity in
            // mappings anyway).
            // To do that, we first stop loom from adding the remapped artifact,
            loom.setupRemappedVariants.set(false)
            // then remove the default artifact (which has a -dev classifier) from all configurations and finally re-add
            // it without the dev classifier.
            afterEvaluate {
                configurations.all {
                    if (artifacts.removeIf { it.classifier == "dev" }) {
                        project.artifacts.add(name, tasks.named("jar")) {
                            classifier = null
                        }
                    }
                    // And the same for the sources jar
                    if (artifacts.removeIf { it.classifier == "sources-dev" }) {
                        project.artifacts.add(name, tasks.named("sourcesJar")) {
                            classifier = "sources"
                        }
                    }
                }
            }
        }

        // Dependencies added to modApi get automatically added to apiElements by Loom, but it does not add them to
        // runtimeElements, which causes issues when another project depends on this one via one of the mod* configurations
        // because those seem to be reading the runtimeElements.
        // To work around that, we'll just disable the Gradle Module Metadata and just use maven pom only.
        tasks.withType<GenerateModuleMetadata> {
            enabled = false
        }
    }
}