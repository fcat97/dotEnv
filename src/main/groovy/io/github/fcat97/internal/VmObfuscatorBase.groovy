package io.github.fcat97.internal

class VmObfuscatorBase {

    String generateRandomHex(Random rng, int length) {
        def chars = ('a'..'f') + ('0'..'9')
        (1..length).collect { chars[rng.nextInt(chars.size())] }.join('')
    }

    /** Pick a unique random byte value (0–255) not already in {@code used}. */
    int uniqueRandomByte(Random rng, Set<Integer> used) {
        int v
        do { v = rng.nextInt(256) } while (!used.add(v))
        v
    }

    /** Generate a random large odd number for use as a custom hash multiplier. */
    long generateRandomPrime(Random rng) {
        long p = rng.nextLong() | 1L
        if (p < 0) p = ~p | 1L
        if (p < 0x100000L) p += 0x100000L
        return p
    }

    /** Compute custom hash of a string using salt and prime (build-time mirror of runtime). */
    int computeCustomHash(String input, long salt, long prime) {
        long h = salt
        for (byte b : input.getBytes()) {
            h ^= (b & 0xFFL)
            h *= prime
        }
        return (int)(h ^ (h >>> 32))
    }

    /** Encrypt/decrypt a single value using positional key rotation + rolling state. */
    int encryptValue(int key, int pos, int state, int plain) {
        int rotatedKey = Integer.rotateLeft(key, pos % 31)
        int stateMask = state | (state << 16)
        return plain ^ rotatedKey ^ stateMask
    }

    /** Returns a boolean expression that is always true, using MBA identities with live runtime variables. */
    String randomAlwaysTruePredicate(Random rng) {
        def pool = [
            '((_op ^ _pc) + 2 * (_op & _pc)) == (_op + _pc)',
            '((_st | _op) - (_st ^ _op)) == (_st & _op)',
            '((_pc & _op) + (_pc | _op)) == (_pc + _op)',
            '((_op ^ _st) + 2 * (_op & _st)) == (_op + _st)',
        ]
        pool[rng.nextInt(pool.size())]
    }

    /** Returns a boolean expression that is always false, using MBA identities with live runtime variables. */
    String randomAlwaysFalsePredicate(Random rng) {
        def pool = [
            '((_op ^ _pc) + 2 * (_op & _pc)) != (_op + _pc)',
            '((_st | _op) - (_st ^ _op)) != (_st & _op)',
            '((_pc & _op) + (_pc | _op)) != (_pc + _op)',
            '((_op ^ _st) + 2 * (_op & _st)) != (_op + _st)',
        ]
        pool[rng.nextInt(pool.size())]
    }

    /** Simulate VM execution to determine position visit order (for state-dependent encryption). */
    List<Integer> simulateExecution(List<Integer> program, int appendOp, int nopOp, int jumpOp, int haltOp) {
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
    Map buildScrambledProgram(Random rng, String value, int appendOp, int nopOp, int jumpOp, int haltOp) {
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
    List<Integer> encryptWithRollingState(List<Integer> plainProg, List<Integer> executionOrder, int key) {
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
}
