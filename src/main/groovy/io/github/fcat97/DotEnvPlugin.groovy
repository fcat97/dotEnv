package io.github.fcat97

import org.gradle.api.*

class DotEnvPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        def extension = project.extensions.create("dotenv", DotEnvExtension)

        def outputDir = new File(project.buildDir, "generated/dotenv/src/main/java")

        project.tasks.register('generateDotEnv', GenerateDotEnvTask) { task ->
            task.envFilePath = project.provider { new File(project.projectDir, extension.envFilepath).absolutePath }
            task.outputDir = outputDir.absolutePath
            task.getNamespace = { ->
                if (extension.namespace) {
                    return extension.namespace
                } else {
                    def moduleName = project.name.replaceAll(/[^A-Za-z0-9_]/, "_")
                    return "dotenv.${moduleName}"
                }
            }
            task.getTargetType = { ->
                if (project.plugins.hasPlugin('org.jetbrains.kotlin.multiplatform')) return 'kotlin-multiplatform'
                if (project.plugins.hasPlugin('org.jetbrains.kotlin.android')) return 'kotlin-android'
                if (project.plugins.hasPlugin('org.jetbrains.kotlin.jvm')) return 'kotlin-jvm'
                return 'java'
            }
            task.obfuscatedFields = project.provider { extension.obfuscate ?: [] }
        }

        project.plugins.withId('java') {
            project.sourceSets.main.java.srcDir outputDir
            project.tasks.named('compileJava').configure {
                dependsOn 'generateDotEnv'
            }
        }

        project.plugins.withId('org.jetbrains.kotlin.jvm') {
            project.sourceSets.main.java.srcDir outputDir
            project.tasks.named('compileKotlin').configure {
                dependsOn 'generateDotEnv'
            }
        }

        project.plugins.withId('org.jetbrains.kotlin.multiplatform') {
            project.kotlin.sourceSets.commonMain.kotlin.srcDir outputDir
            project.tasks.configureEach { task ->
                if (task.name.startsWith('compile') && task.name.contains('Kotlin')) {
                    task.dependsOn('generateDotEnv')
                }
            }
        }

        project.plugins.withId('com.android.library') {
            project.android.sourceSets.main.java.srcDir outputDir
            project.tasks.named('preBuild').configure {
                dependsOn 'generateDotEnv'
            }
        }

        project.plugins.withId('com.android.application') {
            project.android.sourceSets.main.java.srcDir outputDir
            project.tasks.named('preBuild').configure {
                dependsOn 'generateDotEnv'
            }
        }
    }
}

