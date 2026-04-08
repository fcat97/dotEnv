package io.github.fcat97.internal

import com.squareup.kotlinpoet.ClassName as KClassName
import com.squareup.kotlinpoet.FileSpec as KFileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName as KParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec as KTypeSpec

/**
 * Generates DotEnv.kt (and obfuscated internal objects) from parsed .env lines using KotlinPoet.
 */
class KotlinGenerator extends VmObfuscatorBase {

    void generate(Random rng, List<String> lines, List<String> toObfuscate, String namespace, File outputRoot) {
        def objectBuilder = KTypeSpec.objectBuilder("DotEnv")
        Map<String, String> helperNames = [:]

        // Generate obfuscated helper objects first so we know the class names
        toObfuscate.each { fieldName ->
            String key = fieldName.toUpperCase().replaceAll(/[^A-Z0-9_]/, '_')
            def matchLine = lines.find { line ->
                if (line.startsWith('#') || !line.contains('=')) return false
                def k = line.split('=', 2)[0].trim().toUpperCase().replaceAll(/[^A-Z0-9_]/, '_')
                return k == key
            }
            if (matchLine == null) {
                throw new IllegalArgumentException("dotenv: obfuscate field '${fieldName}' not found in .env file")
            }
            def parts = matchLine.split('=', 2)
            String rawVal = EnvParser.stripQuotes(parts[1].trim())

            String helperName = "_${Integer.toHexString(rng.nextInt() & 0xFFFFFF).padLeft(6, '0')}"
            helperNames[key] = helperName
            def helper = generateKotlinObfuscatedObject(rng, helperName, rawVal)
            KFileSpec.builder(namespace, helperName)
                    .addType(helper)
                    .build()
                    .writeTo(outputRoot)
        }

        // Generate properties
        lines.each { line ->
            if (line.startsWith('#') || !line.contains('=')) return
            def parts = line.split('=', 2)
            String key = EnvParser.sanitizeKey(parts[0])
            String raw = EnvParser.stripQuotes(parts[1].trim())

            if (helperNames.containsKey(key)) {
                // Obfuscated field — runtime call, cannot be const
                def helperName = helperNames[key]
                def propSpec = PropertySpec.builder(key, new KClassName('kotlin', 'String'))
                        .initializer('%L.get()', helperName)
                        .build()
                objectBuilder.addProperty(propSpec)
                return
            }

            if (EnvParser.isList(raw)) {
                def items = EnvParser.parseListItems(raw)
                def itemsLiteral = items.collect { '"' + it + '"' }.join(', ')
                def arrType = KParameterizedTypeName.get(
                        new KClassName('kotlin', 'Array'),
                        new KClassName('kotlin', 'String')
                )
                def propSpec = PropertySpec.builder(key, arrType)
                        .initializer("arrayOf(${itemsLiteral})")
                        .build()
                objectBuilder.addProperty(propSpec)
            } else if (EnvParser.isBoolean(raw)) {
                def propSpec = PropertySpec.builder(key, Boolean.TYPE)
                        .addModifiers(KModifier.CONST)
                        .initializer(raw.toLowerCase())
                        .build()
                objectBuilder.addProperty(propSpec)
            } else if (EnvParser.isLong(raw)) {
                long lval = Long.parseLong(raw.replaceAll(/[lL]$/, ''))
                def propSpec = PropertySpec.builder(key, Long.TYPE)
                        .addModifiers(KModifier.CONST)
                        .initializer("${lval}L")
                        .build()
                objectBuilder.addProperty(propSpec)
            } else if (EnvParser.isDouble(raw)) {
                def propSpec = PropertySpec.builder(key, Double.TYPE)
                        .addModifiers(KModifier.CONST)
                        .initializer(raw)
                        .build()
                objectBuilder.addProperty(propSpec)
            } else {
                def propSpec = PropertySpec.builder(key, new KClassName('kotlin', 'String'))
                        .addModifiers(KModifier.CONST)
                        .initializer('%S', raw)
                        .build()
                objectBuilder.addProperty(propSpec)
            }
        }

        KFileSpec.builder(namespace, 'DotEnv')
                .addType(objectBuilder.build())
                .build()
                .writeTo(outputRoot)
    }

    protected KTypeSpec generateKotlinObfuscatedObject(Random rng, String objectName, String secret) {
        // Randomized opcodes (same layout as Java path — enables reusing buildScrambledProgram)
        int OP_APPEND = rng.nextInt(50) + 1
        int OP_NOP    = OP_APPEND + rng.nextInt(50) + 1
        int OP_JUMP   = OP_NOP    + rng.nextInt(50) + 1
        int OP_HALT   = OP_JUMP   + rng.nextInt(50) + 1

        Map built = buildScrambledProgram(rng, secret, OP_APPEND, OP_NOP, OP_JUMP, OP_HALT)
        List<Integer> plainProg = built.program

        // Per-build embedded key bytes (no class-name reflection: JS/Native safe)
        long salt = rng.nextLong()
        long prime = generateRandomPrime(rng)
        int keyLen = 4 + rng.nextInt(5)
        byte[] keyBytes = new byte[keyLen]
        rng.nextBytes(keyBytes)
        String keyBytesLiteral = keyBytes.collect { b -> String.valueOf(b & 0xFF) }.join(', ')
        long expectedHash = salt
        for (int i = 0; i < keyLen; i++) {
            expectedHash = (expectedHash ^ (keyBytes[i] & 0xFF)) * prime
        }

        // MBA scramble constant for opcode dispatch obfuscation.
        // Identity: (x xor C) + 2*(x and C) = x + C  →  _op = _raw + mbaC
        long mbaC = rng.nextLong() & 0x7FFFFFFFL
        int caseAppend = (int)((long)OP_APPEND + mbaC)
        int caseNop    = (int)((long)OP_NOP    + mbaC)
        int caseJump   = (int)((long)OP_JUMP   + mbaC)
        int caseHalt   = (int)((long)OP_HALT   + mbaC)

        // Encrypt only APPEND operands in execution order
        List<Integer> encrypted = new ArrayList<>(plainProg)
        int st = 0, pc = 0, safety = 0
        while (pc >= 0 && pc < plainProg.size() && safety++ < 100000) {
            int op = plainProg[pc]
            if (op == OP_JUMP) {
                pc++
                pc = plainProg[pc]
            } else if (op == OP_APPEND) {
                pc++
                int orig = plainProg[pc]
                encrypted[pc] = (orig ^ st) & 0xFF
                st = (st * 31 + orig) & 0xFF
                pc++
            } else if (op == OP_NOP) {
                pc += 2  // skip NOP and its operand
            } else if (op == OP_HALT) {
                break
            } else {
                pc++
            }
        }
        String progLiteral = encrypted.join(', ')

        def method = FunSpec.builder('get')
                .returns(new KClassName('kotlin', 'String'))

        if (rng.nextBoolean()) { injectKotlinDeadCode(rng, method) }

        // Anti-tamper hash check (uses embedded key bytes — no class-name reflection)
        method.addCode("val _key = intArrayOf(${keyBytesLiteral})\n")
        method.addCode("var _h = ${salt}L\n")
        method.addCode("for (_b in _key) { _h = (_h xor _b.toLong()) * ${prime}L }\n")
        method.addCode("if (_h != ${expectedHash}L) return \"\"\n")

        // VM state
        method.addCode("val _prog = intArrayOf(${progLiteral})\n")
        method.addCode("val _s = StringBuilder()\n")
        method.addCode("var _pc = 0\n")
        method.addCode("var _st = 0\n")
        method.addCode("val _C = ${mbaC}L\n")

        // VM interpreter loop
        method.beginControlFlow("while (_pc >= 0 && _pc < _prog.size)")
        method.addCode("val _raw = _prog[_pc]\n")
        method.addCode("val _op = ((_raw.toLong() xor _C) + 2L * (_raw.toLong() and _C)).toInt()\n")
        method.addCode("_pc++\n")

        method.addCode("when (_op) {\n")

        // APPEND: decrypt next byte and accumulate into result string
        method.addCode("${caseAppend} -> {\n")
        if (rng.nextBoolean()) { method.addCode(generateKotlinDeadCodeBlock(rng)) }
        method.addCode("val _cv = _prog[_pc]\n")
        method.addCode("_pc++\n")
        method.addCode("val _dc = (_cv xor _st) and 0xFF\n")
        method.addCode("_st = (_st * 31 + _dc) and 0xFF\n")
        method.addCode("_s.append(_dc.toChar())\n")
        if (rng.nextBoolean()) { method.addCode(generateKotlinDeadCodeBlock(rng)) }
        method.addCode("}\n")

        // NOP: skip the operand byte
        method.addCode("${caseNop} -> {\n")
        if (rng.nextBoolean()) { method.addCode(generateKotlinDeadCodeBlock(rng)) }
        method.addCode("_pc++\n")
        method.addCode("}\n")

        // JUMP: read target at current position and jump to it
        method.addCode("${caseJump} -> {\n")
        method.addCode("_pc = _prog[_pc]\n")
        if (rng.nextBoolean()) { method.addCode(generateKotlinDeadCodeBlock(rng)) }
        method.addCode("}\n")

        // HALT: signal loop exit
        method.addCode("${caseHalt} -> {\n")
        method.addCode("_pc = -1\n")
        method.addCode("}\n")

        // Default dead path (opaque: never reached with well-formed program)
        method.addCode("else -> {\n")
        method.addCode(generateKotlinDeadCodeBlock(rng))
        method.addCode("_pc++\n")
        method.addCode("}\n")

        method.addCode("}\n")  // end when
        method.endControlFlow()  // while

        if (rng.nextBoolean()) { injectKotlinDeadCode(rng, method) }
        method.addStatement("return _s.toString()")

        KTypeSpec.objectBuilder(objectName)
                .addModifiers(KModifier.INTERNAL)
                .addFunction(method.build())
                .build()
    }

    protected void injectKotlinDeadCode(Random rng, FunSpec.Builder method) {
        int suffix = rng.nextInt(0xFFFF)
        int pattern = rng.nextInt(5)
        switch (pattern) {
            case 0:
                method.addCode("var _dx${suffix} = ${rng.nextInt(1000)}\n")
                method.beginControlFlow("for (_di${suffix} in 0 until ${rng.nextInt(3) + 1})")
                method.addCode("_dx${suffix} = (_dx${suffix} * 31 + _di${suffix}) and 0x7FFFFFFF\n")
                method.endControlFlow()
                break
            case 1:
                method.addCode("val _dsb${suffix} = StringBuilder()\n")
                method.addCode("_dsb${suffix}.append(${rng.nextInt(9999)})\n")
                break
            case 2:
                long c1 = rng.nextLong() & 0x7FFFFFFFL
                long c2 = rng.nextLong() & 0x7FFFFFFFL
                method.addCode("val _da${suffix} = intArrayOf(${c1}, ${c2})\n")
                method.addCode("val _db${suffix} = _da${suffix}[0] xor _da${suffix}[1]\n")
                break
            case 3:
                method.addCode("val _dc2${suffix} = ${rng.nextInt(1000)}L\n")
                method.addCode("val _dd${suffix} = (_dc2${suffix} xor ${rng.nextInt(255)}) * ${rng.nextInt(100) + 2}L\n")
                break
            case 4:
                method.addCode("val _de${suffix} = \"${rng.nextInt(99999)}\"\n")
                method.addCode("val _df${suffix} = _de${suffix}.length\n")
                break
        }
    }

    protected String generateKotlinDeadCodeBlock(Random rng) {
        int suffix = rng.nextInt(0xFFFF)
        int pattern = rng.nextInt(4)
        switch (pattern) {
            case 0:
                return "var _zx${suffix} = ${rng.nextInt(500)}\n_zx${suffix} = _zx${suffix} xor ${rng.nextInt(255)}\n"
            case 1:
                return "val _zy${suffix} = \"${rng.nextInt(99999)}\"\n"
            case 2:
                long c = rng.nextLong() & 0xFFFFL
                return "val _zz${suffix} = ${c}L * ${rng.nextInt(100) + 1}L\n"
            case 3:
                return "val _za${suffix} = intArrayOf(${rng.nextInt(9)}, ${rng.nextInt(9)})\n"
            default:
                return ""
        }
    }
}
