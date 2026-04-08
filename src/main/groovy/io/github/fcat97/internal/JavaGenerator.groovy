package io.github.fcat97.internal

import com.squareup.javapoet.*
import io.github.fcat97.internal.EnvParser
import javax.lang.model.element.Modifier

/**
 * Generates DotEnv.java (and obfuscated helper classes) from parsed .env lines using JavaPoet.
 */
class JavaGenerator extends VmObfuscatorBase {

    final List<String> warnings = []

    void generate(Random rng, List<String> lines, List<String> toObfuscate, String namespace, File outputRoot) {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder("DotEnv")
                .addModifiers(Modifier.PUBLIC)

        Set<String> matchedObfuscateKeys = new HashSet<>()

        lines.each { line ->
            def (key, value) = line.split('=', 2)
            key = EnvParser.sanitizeKey(key)
            value = EnvParser.stripQuotes(value.trim())

            boolean shouldObfuscate = toObfuscate.contains(key)
            boolean isListVal = EnvParser.isList(value)

            if (shouldObfuscate && !EnvParser.isString(value)) {
                throw new org.gradle.api.GradleException(
                    "Cannot obfuscate field '${key}': only String fields can be obfuscated, " +
                    "but '${key}' resolved to a non-String type. " +
                    "Remove '${key}' from the obfuscate list or change its value to a plain string."
                )
            }

            if (shouldObfuscate) {
                matchedObfuscateKeys << key
                String helperName = "_" + generateRandomHex(rng, 8)
                JavaFile.builder(namespace, generateObfuscatedClass(rng, helperName, value, namespace)).build().writeTo(outputRoot)
                classBuilder.addField(FieldSpec.builder(String, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer('$L.get()', helperName)
                        .build()
                )
            } else if (isListVal) {
                def items = EnvParser.parseListItems(value)
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
            } else if (EnvParser.isBoolean(value)) {
                classBuilder.addField(FieldSpec.builder(boolean.class, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer(value.toLowerCase())
                        .build()
                )
            } else if (EnvParser.isLong(value)) {
                def longVal = value.replaceAll(/[lL]$/, '')
                classBuilder.addField(FieldSpec.builder(long.class, key)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer(longVal + 'L')
                        .build()
                )
            } else if (EnvParser.isDouble(value)) {
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

        toObfuscate.each { fieldName ->
            String key = fieldName.toUpperCase().replaceAll(/[^A-Z0-9_]/, '_')
            if (!matchedObfuscateKeys.contains(key)) {
                warnings << "dotenv: obfuscate key '${fieldName}' not found in .env file — skipping."
            }
        }

        JavaFile.builder(namespace, classBuilder.build()).build().writeTo(outputRoot)
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
}
