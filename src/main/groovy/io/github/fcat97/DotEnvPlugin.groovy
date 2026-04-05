package io.github.fcat97

import org.gradle.api.*
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import com.squareup.javapoet.*
import javax.lang.model.element.Modifier

class DotEnvExtension {
    String namespace = null
    String envFilepath = ".env"
    List<String> obfuscate = []
}

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
            task.obfuscatedFields = project.provider { extension.obfuscate ?: [] }
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
    def envFilePath

    @Input
    String outputDir

    @Internal
    Closure getNamespace

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

        def lines = envFile.readLines().findAll { it && !it.startsWith('#') && it.contains('=') }

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder("DotEnv")
                .addModifiers(Modifier.PUBLIC)

        String namespace = getNamespace()
        File outputRoot = new File(outputDir)
        outputRoot.mkdirs()

        lines.each { line ->
            def (key, value) = line.split('=', 2)
            key = key.trim().replaceAll(/[^A-Za-z0-9_]/, "_").toUpperCase()
            value = value.trim()
            if (value.startsWith('"') && value.endsWith('"') && value.length() >= 2) {
                value = value.substring(1, value.length() - 1)
            }

            boolean shouldObfuscate = toObfuscate.contains(key)

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

            boolean isString = !isList &&
                !value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false") &&
                !(value ==~ /^-?\d+[lL]?$/) &&
                !(value ==~ /^-?\d*\.\d+([eE][+-]?\d+)?$/)

            if (shouldObfuscate && !isString) {
                throw new GradleException(
                    "Cannot obfuscate field '${key}': only String fields can be obfuscated, " +
                    "but '${key}' resolved to a non-String type. " +
                    "Remove '${key}' from the obfuscate list or change its value to a plain string."
                )
            }

            if (shouldObfuscate) {
                String helperName = "_" + generateRandomHex(rng, 8)
                JavaFile.builder(namespace, generateObfuscatedClass(rng, helperName, value)).build().writeTo(outputRoot)
                classBuilder.addField(FieldSpec.builder(String, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer('$L.get()', helperName)
                        .build()
                )
            } else if (isList) {
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
                classBuilder.addField(FieldSpec.builder(boolean.class, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer(value.toLowerCase())
                        .build()
                )
            } else if (value ==~ /^-?\d+[lL]?$/) {
                def longVal = value.replaceAll(/[lL]$/, '')
                classBuilder.addField(FieldSpec.builder(long.class, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer(longVal + 'L')
                        .build()
                )
            } else if (value ==~ /^-?\d*\.\d+([eE][+-]?\d+)?$/) {
                classBuilder.addField(FieldSpec.builder(double.class, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer(value)
                        .build()
                )
            } else {
                classBuilder.addField(FieldSpec.builder(String, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("\$S", value)
                        .build()
                )
            }
        }

        JavaFile.builder(namespace, classBuilder.build()).build().writeTo(outputRoot)
        logger.lifecycle("Generated: ${outputDir}/${namespace.replace('.', '/')}/DotEnv.java")
    }

    // ── obfuscation helpers ──────────────────────────────────────────────────

    protected String generateRandomHex(Random rng, int length) {
        def chars = ('a'..'f') + ('0'..'9')
        (1..length).collect { chars[rng.nextInt(chars.size())] }.join('')
    }

    protected TypeSpec generateObfuscatedClass(Random rng, String className, String value) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("get")
                .addModifiers(Modifier.STATIC)
                .returns(String.class)

        method.addStatement('$T _s = new $T()', StringBuilder.class, StringBuilder.class)

        value.toCharArray().eachWithIndex { char ch, int idx ->
            String varName = "_v${idx}"
            method.addCode(generateCharExpr(rng, (int) ch, varName))
            method.addStatement('_s.append((char) $L)', varName)
        }

        method.addStatement('return _s.toString()')

        TypeSpec.classBuilder(className)
                .addModifiers(Modifier.FINAL)
                .addMethod(method.build())
                .build()
    }

    /**
     * Generates a 3-step arithmetic expression that evaluates to {@code c}.
     * Operands are chosen randomly so the expression differs on every build.
     *   int _vN = a;
     *   _vN = _vN * b;
     *   _vN = _vN +/- remainder;  →  _vN == c
     */
    protected CodeBlock generateCharExpr(Random rng, int c, String varName) {
        int a = rng.nextInt(13) + 2   // 2–14
        int b = rng.nextInt(13) + 2   // 2–14
        int remainder = c - (a * b)

        CodeBlock.Builder block = CodeBlock.builder()
        block.addStatement('int $L = $L', varName, a)
        block.addStatement('$L = $L * $L', varName, varName, b)
        if (remainder >= 0) {
            block.addStatement('$L = $L + $L', varName, varName, remainder)
        } else {
            block.addStatement('$L = $L - $L', varName, varName, Math.abs(remainder))
        }
        block.build()
    }
}