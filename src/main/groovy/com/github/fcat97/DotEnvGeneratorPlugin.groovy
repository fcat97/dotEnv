package com.github.fcat97

import org.gradle.api.*
import org.gradle.api.tasks.*

class DotEnvGeneratorPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        // This is the standard location for generated Java sources in Android
        def outputDir = new File(project.buildDir, "generated/dotenv/src/main/java")
        def envFilePath = new File(project.projectDir, ".env").absolutePath

        project.tasks.register('generateDotEnv', GenerateDotEnvTask) { task ->
            task.envFilePath = envFilePath
            task.outputDir = outputDir.absolutePath
        }

        project.plugins.withId('java') {
            project.sourceSets.main.java.srcDir outputDir
            project.tasks.named('compileJava').configure {
                dependsOn 'generateDotEnv'
            }
        }

        // For Android projects
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

class GenerateDotEnvTask extends DefaultTask {
    @Input
    String envFilePath

    @Input
    String outputDir

    @TaskAction
    void generate() {
        File envFile = new File(envFilePath)
        if (!envFile.exists()) {
            logger.lifecycle(".env file not found: ${envFilePath}. Skipping DotEnv generation.")
            return
        }

        // Make sure to create the package directory 'dotenv' inside outputDir
        File packageDir = new File(outputDir, "dotenv")
        packageDir.mkdirs()
        def outputFile = new File(packageDir, "DotEnv.java")
        def lines = envFile.readLines().findAll { it && !it.startsWith('#') && it.contains('=') }
        outputFile.withWriter('UTF-8') { writer ->
            writer.println "package dotenv;"
            writer.println ""
            writer.println "public class DotEnv {"
            lines.each { line ->
                def (key, value) = line.split('=', 2)
                key = key.trim().replaceAll(/[^A-Za-z0-9_]/, "_").toUpperCase()
                value = value.trim()
                if (value.startsWith('"') && value.endsWith('"') && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1)
                }
                value = value.replaceAll('"', '\\"')
                writer.println "    public static final String ${key} = \"${value}\";"
            }
            writer.println "}"
        }
        logger.lifecycle("Generated: ${outputFile.absolutePath}")
    }
}