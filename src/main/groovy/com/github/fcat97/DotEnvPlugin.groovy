package com.github.fcat97

import org.gradle.api.*
import org.gradle.api.tasks.*
import com.squareup.javapoet.*
import javax.lang.model.element.Modifier

class DotEnvExtension {
    // Use 'namespace' for custom package, default is 'dotenv.{project-name}'
    String namespace = null
}

class DotEnvPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        // Register extension for user configuration
        def extension = project.extensions.create("dotenv", DotEnvExtension)

        def outputDir = new File(project.buildDir, "generated/dotenv/src/main/java")
        def envFilePath = new File(project.projectDir, ".env").absolutePath

        project.tasks.register('generateDotEnv', GenerateDotEnvTask) { task ->
            task.envFilePath = envFilePath
            task.outputDir = outputDir.absolutePath
            task.getNamespace = { ->
                // Determine namespace: user config or default
                if (extension.namespace) {
                    return extension.namespace
                } else {
                    // Default: dotenv.{project-name}, sanitize for java package
                    def moduleName = project.name.replaceAll(/[^A-Za-z0-9_]/, "_")
                    return "dotenv.${moduleName}"
                }
            }
        }

        project.plugins.withId('java') {
            project.sourceSets.main.java.srcDir outputDir
            project.tasks.named('compileJava').configure {
                dependsOn 'generateDotEnv'
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

class GenerateDotEnvTask extends DefaultTask {
    @Input
    String envFilePath

    @Input
    String outputDir

    @Internal
    Closure getNamespace

    @TaskAction
    void generate() {
        File envFile = new File(envFilePath)
        if (!envFile.exists()) {
            logger.lifecycle(".env file not found: ${envFilePath}. Skipping DotEnv generation.")
            return
        }

        def lines = envFile.readLines().findAll { it && !it.startsWith('#') && it.contains('=') }

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder("DotEnv")
                .addModifiers(Modifier.PUBLIC)

        lines.each { line ->
            def (key, value) = line.split('=', 2)
            key = key.trim().replaceAll(/[^A-Za-z0-9_]/, "_").toUpperCase()
            value = value.trim()
            // Remove surrounding double quotes if present
            if (value.startsWith('"') && value.endsWith('"') && value.length() >= 2) {
                value = value.substring(1, value.length() - 1)
            }

            // List detection
            boolean isList = false
            def items = []
            if (value.startsWith('[') && value.endsWith(']')) {
                isList = true
                def inner = value.substring(1, value.length() - 1).trim()
                items = inner.split(/,(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)/).collect { it.trim().replaceAll(/^"|"$/, '') }
            } else if (value.contains(',')) {
                isList = true
                items = value.split(',').collect { it.trim() }
            }

            if (isList) {
                // String[] constant
                CodeBlock.Builder arrayInit = CodeBlock.builder().add("{")
                items.eachWithIndex { v, idx ->
                    arrayInit.add("\$S", v)
                    if (idx < items.size() - 1) arrayInit.add(", ")
                }
                arrayInit.add("}")
                classBuilder.addField(FieldSpec.builder(String[].class, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer(arrayInit.build())
                        .build()
                )
            } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                // Boolean constant
                classBuilder.addField(FieldSpec.builder(boolean.class, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer(value.toLowerCase())
                        .build()
                )
            } else if (value ==~ /^-?\d+[lL]?$/) {
                // Long constant
                def longVal = value.replaceAll(/[lL]$/, '') // remove L suffix if any
                classBuilder.addField(FieldSpec.builder(long.class, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer(longVal + 'L')
                        .build()
                )
            } else if (value ==~ /^-?\d*\.\d+([eE][+-]?\d+)?$/) {
                // Double constant
                classBuilder.addField(FieldSpec.builder(double.class, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer(value)
                        .build()
                )
            } else {
                // String constant
                classBuilder.addField(FieldSpec.builder(String, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("\$S", value)
                        .build()
                )
            }
        }

        String namespace = getNamespace()
        JavaFile javaFile = JavaFile.builder(namespace, classBuilder.build()).build()
        File outputRoot = new File(outputDir)
        outputRoot.mkdirs()
        javaFile.writeTo(outputRoot)

        logger.lifecycle("Generated: ${outputDir}/${namespace.replace('.', '/')}/DotEnv.java")
    }
}