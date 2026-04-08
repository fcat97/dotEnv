package io.github.fcat97

import io.github.fcat97.internal.EnvParser
import io.github.fcat97.internal.JavaGenerator
import io.github.fcat97.internal.KotlinGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*

class GenerateDotEnvTask extends DefaultTask {

    @Input
    def envFilePath

    @Input
    String javaOutputDir

    @Input
    String kotlinOutputDir

    @Internal
    Closure getNamespace

    @Internal
    Closure getTargetType

    @Input
    def obfuscatedFields = []

    @TaskAction
    void generate() {
        Random rng = new Random()
        String resolvedPath = envFilePath instanceof Provider ? envFilePath.get() : envFilePath
        File envFile = new File(resolvedPath)
        if (!envFile.exists()) {
            throw new GradleException(".env file not found: ${resolvedPath}. Create a .env file in the module root or set 'envFilepath' in the dotenv block.")
        }

        List<String> toObfuscate = ((obfuscatedFields instanceof Provider
            ? obfuscatedFields.get()
            : obfuscatedFields) ?: []) as List<String>

        def lines = EnvParser.readLines(envFile)
        String namespace = getNamespace()
        String targetType = getTargetType ? getTargetType() : 'java'
        String resolvedOutputDir = (targetType == 'java') ? javaOutputDir : kotlinOutputDir
        File outputRoot = new File(resolvedOutputDir)
        outputRoot.mkdirs()

        if (targetType != 'java') {
            def gen = new KotlinGenerator()
            gen.generate(rng, lines, toObfuscate, namespace, outputRoot)
            gen.warnings.each { logger.warn(it) }
            logger.lifecycle("Generated: ${resolvedOutputDir}/${namespace.replace('.', '/')}/DotEnv.kt")
        } else {
            def gen = new JavaGenerator()
            gen.generate(rng, lines, toObfuscate, namespace, outputRoot)
            gen.warnings.each { logger.warn(it) }
            logger.lifecycle("Generated: ${resolvedOutputDir}/${namespace.replace('.', '/')}/DotEnv.java")
        }
    }
}
