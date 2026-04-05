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

    /** Generate a random large odd number for use as a custom hash multiplier. */
    protected long generateRandomPrime(Random rng) {
        long p = rng.nextLong() | 1L
        if (p < 0) p = ~p | 1L
        if (p < 0x100000L) p += 0x100000L
        return p
    }

    /** Compute custom hash of a string using salt and prime (build-time mirror of runtime). */
    protected int computeCustomHash(String input, long salt, long prime) {
        long h = salt
        for (byte b : input.getBytes()) {
            h ^= (b & 0xFFL)
            h *= prime
        }
        return (int)(h ^ (h >>> 32))
    }

    /** Encrypt/decrypt a single value using positional key rotation + rolling state. */
    protected int encryptValue(int key, int pos, int state, int plain) {
        int rotatedKey = Integer.rotateLeft(key, pos % 31)
        int stateMask = state | (state << 16)
        return plain ^ rotatedKey ^ stateMask
    }

    /** Returns a boolean expression that is always true, using MBA identities with live runtime variables. */
    protected String randomAlwaysTruePredicate(Random rng) {
        def pool = [
            '((_op ^ _pc) + 2 * (_op & _pc)) == (_op + _pc)',
            '((_st | _op) - (_st ^ _op)) == (_st & _op)',
            '((_pc & _op) + (_pc | _op)) == (_pc + _op)',
            '((_op ^ _st) + 2 * (_op & _st)) == (_op + _st)',
        ]
        pool[rng.nextInt(pool.size())]
    }

    /** Returns a boolean expression that is always false, using MBA identities with live runtime variables. */
    protected String randomAlwaysFalsePredicate(Random rng) {
        def pool = [
            '((_op ^ _pc) + 2 * (_op & _pc)) != (_op + _pc)',
            '((_st | _op) - (_st ^ _op)) != (_st & _op)',
            '((_pc & _op) + (_pc | _op)) != (_pc + _op)',
            '((_op ^ _st) + 2 * (_op & _st)) != (_op + _st)',
        ]
        pool[rng.nextInt(pool.size())]
    }

    /** Injects an unreachable code block guarded by a hardened always-false opaque predicate. */
    protected void injectDeadCode(Random rng, MethodSpec.Builder method) {
        method.beginControlFlow('if ($L)', randomAlwaysFalsePredicate(rng))
        switch (rng.nextInt(6)) {
            case 0:
                int fakeA = rng.nextInt(13) + 2
                int fakeB = rng.nextInt(13) + 2
                method.addStatement('int _d = $L', fakeA)
                method.addStatement('_d = _d * $L', fakeB)
                method.addStatement('_s.append((char) _d)')
                break
            case 1:
                method.addStatement('$T _f = new $T()', StringBuilder.class, StringBuilder.class)
                method.addStatement('_f.append((char) $L)', rng.nextInt(26) + 65)
                method.addStatement('return _f.toString()')
                break
            case 2:
                method.addStatement('_s.append((char) $L)', rng.nextInt(26) + 65)
                break
            case 3:
                int loopBound = rng.nextInt(5) + 3
                method.addStatement('int _dh = $L', rng.nextInt(1000))
                method.beginControlFlow('for (int _i = 0; _i < $L; _i++)', loopBound)
                method.addStatement('_dh = (_dh * 31 + _i) ^ _op')
                method.endControlFlow()
                method.addStatement('_s.append((char) (_dh & 0x7F))')
                break
            case 4:
                method.addStatement('int[] _fa = {$L, $L, $L}', rng.nextInt(100), rng.nextInt(100), rng.nextInt(100))
                method.addStatement('int _fi = _op % 3')
                method.addStatement('_s.append((char) (_fa[_fi < 0 ? -_fi : _fi] + 32))')
                break
            default:
                method.addStatement('int _x = $L', rng.nextInt(0xFFFF))
                method.addStatement('_x = (_x >>> 3) | (_x << 29)')
                method.addStatement('_x = _x ^ $L', rng.nextInt(0xFFFF))
                method.addStatement('_s.append((char) (_x & 0xFF))')
                break
        }
        method.endControlFlow()
    }

    /** Returns a dead code block as raw Java code string (for use inside switch via addCode). */
    protected String generateDeadCodeBlock(Random rng) {
        def pred = randomAlwaysFalsePredicate(rng)
        def sb = new StringBuilder()
        sb.append("if (${pred}) {\n")
        switch (rng.nextInt(6)) {
            case 0:
                int fakeA = rng.nextInt(13) + 2
                int fakeB = rng.nextInt(13) + 2
                sb.append("  int _d = ${fakeA};\n")
                sb.append("  _d = _d * ${fakeB};\n")
                sb.append("  _s.append((char) _d);\n")
                break
            case 1:
                sb.append("  StringBuilder _f = new StringBuilder();\n")
                sb.append("  _f.append((char) ${rng.nextInt(26) + 65});\n")
                sb.append("  return _f.toString();\n")
                break
            case 2:
                sb.append("  _s.append((char) ${rng.nextInt(26) + 65});\n")
                break
            case 3:
                int loopBound = rng.nextInt(5) + 3
                sb.append("  int _dh = ${rng.nextInt(1000)};\n")
                sb.append("  for (int _i = 0; _i < ${loopBound}; _i++) {\n")
                sb.append("    _dh = (_dh * 31 + _i) ^ _op;\n")
                sb.append("  }\n")
                sb.append("  _s.append((char) (_dh & 0x7F));\n")
                break
            case 4:
                sb.append("  int[] _fa = {${rng.nextInt(100)}, ${rng.nextInt(100)}, ${rng.nextInt(100)}};\n")
                sb.append("  int _fi = _op % 3;\n")
                sb.append("  _s.append((char) (_fa[_fi < 0 ? -_fi : _fi] + 32));\n")
                break
            default:
                sb.append("  int _x = ${rng.nextInt(0xFFFF)};\n")
                sb.append("  _x = (_x >>> 3) | (_x << 29);\n")
                sb.append("  _x = _x ^ ${rng.nextInt(0xFFFF)};\n")
                sb.append("  _s.append((char) (_x & 0xFF));\n")
                break
        }
        sb.append("}\n")
        return sb.toString()
    }

    /** Simulate VM execution to determine position visit order (for state-dependent encryption). */
    protected List<Integer> simulateExecution(List<Integer> program, int appendOp, int nopOp, int jumpOp, int haltOp) {
        List<Integer> order = []
        int pc = 0
        int safety = 0
        while (pc >= 0 && pc < program.size() && safety++ < 100000) {
            order << pc
            int op = program[pc]
            if (op == jumpOp) {
                pc++
                order << pc
                pc = program[pc]
            } else if (op == appendOp || op == nopOp) {
                pc++
                order << pc
                pc++
            } else if (op == haltOp) {
                break
            } else {
                pc++
            }
        }
        return order
    }

    /**
     * Build scrambled VM program with JUMP opcodes for non-linear execution.
     * Characters are split into shuffled blocks connected by JUMPs.
     * Returns map with 'program' (plain values) and 'executionOrder' (visit indices).
     */
    protected Map buildScrambledProgram(Random rng, String value, int appendOp, int nopOp, int jumpOp, int haltOp) {
        List<List<Integer>> blocks = []
        char[] chars = value.toCharArray()
        int i = 0
        while (i < chars.length) {
            List<Integer> block = []
            int maxChunk = Math.min(3, chars.length - i)
            int chunkSize = maxChunk <= 1 ? 1 : (1 + rng.nextInt(maxChunk))
            for (int j = 0; j < chunkSize; j++) {
                if (rng.nextInt(3) == 0) {
                    block << nopOp
                    block << rng.nextInt(256)
                }
                block << appendOp
                block << (int) chars[i + j]
            }
            blocks << block
            i += chunkSize
        }

        blocks << [haltOp]
        int haltBlockIdx = blocks.size() - 1

        List<Integer> physicalOrder = (0..<blocks.size()).collect()
        Collections.shuffle(physicalOrder, rng)

        // Calculate physical start positions (initial JUMP occupies positions 0-1)
        int pos = 2
        Map<Integer, Integer> blockStart = [:]
        physicalOrder.each { blockIdx ->
            blockStart[blockIdx] = pos
            pos += blocks[blockIdx].size()
            if (blockIdx != haltBlockIdx) {
                pos += 2  // trailing JUMP + target
            }
        }

        List<Integer> program = new ArrayList<>(Collections.nCopies(pos, 0))

        // Initial JUMP → first logical block (block 0)
        program[0] = jumpOp
        program[1] = blockStart[0]

        // Fill blocks and trailing JUMPs
        physicalOrder.each { blockIdx ->
            int p = blockStart[blockIdx]
            blocks[blockIdx].eachWithIndex { val, idx -> program[p + idx] = val }
            if (blockIdx != haltBlockIdx) {
                int nextLogical = blockIdx + 1
                int jmpPos = p + blocks[blockIdx].size()
                program[jmpPos] = jumpOp
                program[jmpPos + 1] = blockStart[nextLogical]
            }
        }

        List<Integer> executionOrder = simulateExecution(program, appendOp, nopOp, jumpOp, haltOp)
        return [program: program, executionOrder: executionOrder]
    }

    /** Encrypt program values following execution order with rolling state. */
    protected List<Integer> encryptWithRollingState(List<Integer> plainProg, List<Integer> executionOrder, int key) {
        // Default: XOR with key for unvisited positions (produces garbage if decoded)
        List<Integer> encrypted = plainProg.collect { val -> val ^ Integer.rotateLeft(key, 0) }

        int _st = 0
        executionOrder.each { idx ->
            int plain = plainProg[idx]
            encrypted[idx] = encryptValue(key, idx, _st, plain)
            _st = (_st * 31 + plain) & 0xFFFF
        }
        return encrypted
    }

    /**
     * Generates a helper class with custom VM interpreter featuring:
     * - Custom hash key derivation (replaces String.hashCode)
     * - Non-linear bytecode execution via JUMP opcodes
     * - State-dependent rolling decryption
     * - MBA-scrambled switch dispatch
     * - Hardened opaque predicates with live runtime variables
     * - Dead code injection
     */
    protected TypeSpec generateObfuscatedClass(Random rng, String className, String value, String namespace) {
        Set<Integer> usedOps = new HashSet<>()
        int appendOp = uniqueRandomByte(rng, usedOps)
        int nopOp = uniqueRandomByte(rng, usedOps)
        int jumpOp = uniqueRandomByte(rng, usedOps)
        int haltOp = uniqueRandomByte(rng, usedOps)

        String fqcn = "${namespace}.${className}"
        long salt = rng.nextLong()
        long prime = generateRandomPrime(rng)
        int xorKey = computeCustomHash(fqcn, salt, prime)

        def result = buildScrambledProgram(rng, value, appendOp, nopOp, jumpOp, haltOp)
        List<Integer> plainProg = result.program
        List<Integer> executionOrder = result.executionOrder

        List<Integer> encProg = encryptWithRollingState(plainProg, executionOrder, xorKey)

        int opSeed = rng.nextInt(9000) + 1000
        int scrambleConst = rng.nextInt(200) + 50

        MethodSpec.Builder method = MethodSpec.methodBuilder("get")
                .addModifiers(Modifier.STATIC)
                .returns(String.class)

        method.addStatement('$T _s = new $T()', StringBuilder.class, StringBuilder.class)

        // Custom hash key derivation (not String.hashCode — per-build salt + prime)
        method.addStatement('long _ha = $LL', salt)
        method.beginControlFlow('for (byte _b : $L.class.getName().getBytes())', className)
        method.addStatement('_ha ^= (_b & 0xFFL)')
        method.addStatement('_ha *= $LL', prime)
        method.endControlFlow()
        method.addStatement('int _k = (int)(_ha ^ (_ha >>> 32))')

        method.addStatement('int _pc = 0')
        method.addStatement('int _op = $L', opSeed)
        method.addStatement('int _st = 0')

        String arrayLit = encProg.collect { it.toString() }.join(', ')
        method.addStatement('int[] _pg = {$L}', arrayLit)

        // Dead code before loop
        if (rng.nextBoolean()) { injectDeadCode(rng, method) }

        // VM interpreter with non-linear execution
        method.beginControlFlow('while (_pc >= 0 && _pc < _pg.length)')

        // Decrypt with rolling state: each byte depends on all previous in execution order
        method.addStatement('int _raw = _pg[_pc] ^ $T.rotateLeft(_k, _pc % 31) ^ (_st | (_st << 16))', Integer.class)
        method.addStatement('_st = (_st * 31 + _raw) & 0xFFFF')

        // MBA-scrambled dispatch: (x ^ C) + 2*(x & C) == x + C
        method.addStatement('int _sv = (_raw ^ $L) + 2 * (_raw & $L)', scrambleConst, scrambleConst)

        int appendCase = appendOp + scrambleConst
        int nopCase = nopOp + scrambleConst
        int jumpCase = jumpOp + scrambleConst
        int haltCase = haltOp + scrambleConst

        method.addCode('switch (_sv) {\n')

        // APPEND
        method.addCode('case $L: {\n', appendCase)
        method.addCode('  _pc++;\n')
        method.addCode('  int _ch = _pg[_pc] ^ Integer.rotateLeft(_k, _pc % 31) ^ (_st | (_st << 16));\n')
        method.addCode('  _st = (_st * 31 + _ch) & 0xFFFF;\n')
        method.addCode('  if ($L) {\n', randomAlwaysTruePredicate(rng))
        method.addCode('    _s.append((char) _ch);\n')
        method.addCode('  }\n')
        if (rng.nextBoolean()) { method.addCode(generateDeadCodeBlock(rng)) }
        method.addCode('  _pc++;\n')
        method.addCode('  break;\n')
        method.addCode('}\n')

        // NOP
        method.addCode('case $L: {\n', nopCase)
        method.addCode('  _pc++;\n')
        method.addCode('  int _nv = _pg[_pc] ^ Integer.rotateLeft(_k, _pc % 31) ^ (_st | (_st << 16));\n')
        method.addCode('  _st = (_st * 31 + _nv) & 0xFFFF;\n')
        method.addCode('  _nv = (_nv ^ _op) + 2 * (_nv & _op);\n')
        if (rng.nextBoolean()) { method.addCode(generateDeadCodeBlock(rng)) }
        method.addCode('  _pc++;\n')
        method.addCode('  break;\n')
        method.addCode('}\n')

        // JUMP (non-linear execution)
        method.addCode('case $L: {\n', jumpCase)
        method.addCode('  _pc++;\n')
        method.addCode('  int _tgt = _pg[_pc] ^ Integer.rotateLeft(_k, _pc % 31) ^ (_st | (_st << 16));\n')
        method.addCode('  _st = (_st * 31 + _tgt) & 0xFFFF;\n')
        method.addCode('  _pc = _tgt;\n')
        method.addCode('  break;\n')
        method.addCode('}\n')

        // HALT
        method.addCode('case $L: {\n', haltCase)
        method.addCode('  _pc = -1;\n')
        method.addCode('  break;\n')
        method.addCode('}\n')

        // Default dead path
        method.addCode('default: {\n')
        method.addCode(generateDeadCodeBlock(rng))
        method.addCode('  _pc++;\n')
        method.addCode('  break;\n')
        method.addCode('}\n')

        method.addCode('}\n')  // end switch
        method.endControlFlow()  // while

        // Dead code after loop
        if (rng.nextBoolean()) { injectDeadCode(rng, method) }

        method.addStatement('return _s.toString()')

        TypeSpec.classBuilder(className)
                .addModifiers(Modifier.FINAL)
                .addMethod(method.build())
                .build()
    }
}