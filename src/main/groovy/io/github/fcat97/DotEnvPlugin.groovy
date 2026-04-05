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
                JavaFile.builder(namespace, generateObfuscatedClass(rng, helperName, value, namespace)).build().writeTo(outputRoot)
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

    /** Pick a unique random byte value (0–255) not already in {@code used}. */
    protected int uniqueRandomByte(Random rng, Set<Integer> used) {
        int v
        do { v = rng.nextInt(256) } while (!used.add(v))
        v
    }

    /** XOR-encrypt/decrypt a single program value with a position-dependent key byte. */
    protected int encryptByte(int key, int pos, int value) {
        value ^ ((key >>> ((pos & 3) << 3)) & 0xFF)
    }

    /**
     * Returns a boolean expression string that is true iff {@code varExpr == constant}.
     * Uses Mixed Boolean-Arithmetic (MBA) to disguise the comparison.
     */
    protected String generateMbaEquality(Random rng, String v, int c) {
        switch (rng.nextInt(4)) {
            case 0: return "($v & $c) + ($v | $c) == ${c + c}"
            case 1: return "(($v - $c) | ($c - $v)) >>> 31 == 0"
            case 2: return "$v * $v + ${c * c} - 2 * $v * $c == 0"
            default: return "($v & $c) + ($v | $c) >= ${c + c} && ($v & $c) + ($v | $c) <= ${c + c}"
        }
    }

    /** Returns a random expression that is mathematically always true. */
    protected String randomAlwaysTruePredicate(Random rng) {
        def pool = [
            '(_op * _op) >= 0',
            '(_op | ~_op) == -1',
            '(_op ^ 0) == _op',
            '(_op - _op) == 0',
        ]
        pool[rng.nextInt(pool.size())]
    }

    /** Returns a random expression that is mathematically always false. */
    protected String randomAlwaysFalsePredicate(Random rng) {
        def pool = [
            '_op * 0 != 0',
            '(_op & 0) != 0',
            '_op != _op',
            '(_op ^ _op) != 0',
        ]
        pool[rng.nextInt(pool.size())]
    }

    /** Injects an unreachable code block guarded by an always-false opaque predicate. */
    protected void injectDeadCode(Random rng, MethodSpec.Builder method) {
        method.beginControlFlow('if ($L)', randomAlwaysFalsePredicate(rng))
        switch (rng.nextInt(6)) {
            case 0:
                // Fake char computation
                int fakeA = rng.nextInt(13) + 2
                int fakeB = rng.nextInt(13) + 2
                method.addStatement('int _d = $L', fakeA)
                method.addStatement('_d = _d * $L', fakeB)
                method.addStatement('_s.append((char) _d)')
                break
            case 1:
                // Fake StringBuilder + misleading return
                method.addStatement('$T _f = new $T()', StringBuilder.class, StringBuilder.class)
                method.addStatement('_f.append((char) $L)', rng.nextInt(26) + 65)
                method.addStatement('return _f.toString()')
                break
            case 2:
                // Simple misleading direct append
                method.addStatement('_s.append((char) $L)', rng.nextInt(26) + 65)
                break
            case 3:
                // Fake hash loop
                int loopBound = rng.nextInt(5) + 3
                method.addStatement('int _h = $L', rng.nextInt(1000))
                method.beginControlFlow('for (int _i = 0; _i < $L; _i++)', loopBound)
                method.addStatement('_h = (_h * 31 + _i) ^ _op')
                method.endControlFlow()
                method.addStatement('_s.append((char) (_h & 0x7F))')
                break
            case 4:
                // Fake array lookup
                method.addStatement('int[] _fa = {$L, $L, $L}', rng.nextInt(100), rng.nextInt(100), rng.nextInt(100))
                method.addStatement('int _fi = _op % 3')
                method.addStatement('_s.append((char) (_fa[_fi < 0 ? -_fi : _fi] + 32))')
                break
            default:
                // Fake bitwise "decryption"
                method.addStatement('int _x = $L', rng.nextInt(0xFFFF))
                method.addStatement('_x = (_x >>> 3) | (_x << 29)')
                method.addStatement('_x = _x ^ $L', rng.nextInt(0xFFFF))
                method.addStatement('_s.append((char) (_x & 0xFF))')
                break
        }
        method.endControlFlow()
    }

    /**
     * Builds the VM program: encodes the string as APPEND opcodes with interleaved
     * NOP and FAKE_JMP dead opcodes. Returns the plain (unencrypted) program.
     */
    protected List<Integer> buildVmProgram(Random rng, String value, int appendOp, int nopOp, int fakeJmpOp, int haltOp) {
        List<Integer> prog = []
        for (char ch : value.toCharArray()) {
            if (rng.nextBoolean()) {
                prog << (rng.nextBoolean() ? nopOp : fakeJmpOp)
                prog << rng.nextInt(256)
            }
            prog << appendOp
            prog << (int) ch
        }
        if (rng.nextBoolean()) {
            prog << nopOp
            prog << rng.nextInt(256)
        }
        prog << haltOp
        prog
    }

    /**
     * Generates a helper class whose {@code get()} method reconstructs {@code value} via
     * a custom VM interpreter with XOR-encrypted bytecode, MBA-hardened dispatch,
     * opaque predicates, and dead code injection.
     */
    protected TypeSpec generateObfuscatedClass(Random rng, String className, String value, String namespace) {
        Set<Integer> usedOps = new HashSet<>()
        int appendOp = uniqueRandomByte(rng, usedOps)
        int nopOp = uniqueRandomByte(rng, usedOps)
        int fakeJmpOp = uniqueRandomByte(rng, usedOps)
        int haltOp = uniqueRandomByte(rng, usedOps)

        String fqcn = "${namespace}.${className}"
        int xorKey = fqcn.hashCode()
        List<Integer> plainProg = buildVmProgram(rng, value, appendOp, nopOp, fakeJmpOp, haltOp)
        List<Integer> encProg = []
        plainProg.eachWithIndex { b, idx -> encProg << encryptByte(xorKey, idx, b as int) }

        int opSeed = rng.nextInt(9000) + 1000

        MethodSpec.Builder method = MethodSpec.methodBuilder("get")
                .addModifiers(Modifier.STATIC)
                .returns(String.class)

        method.addStatement('$T _s = new $T()', StringBuilder.class, StringBuilder.class)
        method.addStatement('int _k = $L.class.getName().hashCode()', className)
        method.addStatement('int _pc = 0')
        method.addStatement('int _op = $L', opSeed)

        String arrayLit = encProg.collect { it.toString() }.join(', ')
        method.addStatement('int[] _pg = {$L}', arrayLit)

        method.beginControlFlow('while (_pc < _pg.length)')
        method.addStatement('int _oc = _pg[_pc] ^ ((_k >>> ((_pc & 3) << 3)) & 0xFF)')

        // APPEND handler
        method.beginControlFlow('if ($L)', generateMbaEquality(rng, '_oc', appendOp))
        method.addStatement('_pc++')
        method.addStatement('int _ch = _pg[_pc] ^ ((_k >>> ((_pc & 3) << 3)) & 0xFF)')
        method.beginControlFlow('if ($L)', randomAlwaysTruePredicate(rng))
        method.addStatement('_s.append((char) _ch)')
        method.endControlFlow()
        if (rng.nextBoolean()) { injectDeadCode(rng, method) }

        // NOP handler
        method.nextControlFlow('else if ($L)', generateMbaEquality(rng, '_oc', nopOp))
        method.addStatement('_pc++')
        method.addStatement('int _jk = _pg[_pc] ^ 0x$L', Integer.toHexString(rng.nextInt(256)).padLeft(2, '0'))
        method.addStatement('_jk = (_jk ^ _op) + 2 * (_jk & _op)')
        if (rng.nextBoolean()) { injectDeadCode(rng, method) }

        // FAKE_JMP handler
        method.nextControlFlow('else if ($L)', generateMbaEquality(rng, '_oc', fakeJmpOp))
        method.addStatement('_pc++')
        method.beginControlFlow('if ($L)', randomAlwaysTruePredicate(rng))
        method.addStatement('int _jk2 = _pg[_pc]')
        method.addStatement('_jk2 = ~_jk2 + 1')
        method.nextControlFlow('else')
        method.addStatement('_pc = _pg[_pc] & 0xFF')
        method.endControlFlow()

        // HALT handler
        method.nextControlFlow('else if ($L)', generateMbaEquality(rng, '_oc', haltOp))
        method.addStatement('break')

        // Default dead branch
        method.nextControlFlow('else')
        injectDeadCode(rng, method)

        method.endControlFlow()  // if/else chain
        method.addStatement('_pc++')
        method.endControlFlow()  // while

        method.addStatement('return _s.toString()')

        TypeSpec.classBuilder(className)
                .addModifiers(Modifier.FINAL)
                .addMethod(method.build())
                .build()
    }
}