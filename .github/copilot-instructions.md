# Copilot Instructions

## Project Overview

This is a **Gradle plugin** (`io.github.fcat97.dotenv`) written in Groovy. It generates a `DotEnv.java` class at build time from a module's `.env` file, making environment variables available as typed `public static final` constants.

## Build & Publish

```bash
# Build
./gradlew build

# Publish to Gradle Plugin Portal (requires GRADLE_PUBLISH_KEY + GRADLE_PUBLISH_SECRET)
./gradlew publishPlugins
```

There are no tests in this project. The build pipeline runs on GitHub Actions and publishes on GitHub release events.

## Architecture

The entire plugin lives in a single file:
**`src/main/groovy/io/github/fcat97/DotEnvPlugin.groovy`**

It contains three classes:

- **`DotEnvExtension`** – DSL config block (`namespace`, `envFilepath`). Applied via `project.extensions.create("dotenv", DotEnvExtension)`.
- **`DotEnvPlugin`** – Registers the `generateDotEnv` task and hooks it into `compileJava` (Java projects) or `preBuild` (Android projects) depending on which plugin is applied.
- **`GenerateDotEnvTask`** – Reads the `.env` file, parses each line, infers the Java type, and uses [JavaPoet](https://github.com/square/javapoet) to write `DotEnv.java` to `build/generated/dotenv/src/main/java/`.

## Key Conventions

### Type inference logic (in `GenerateDotEnvTask.generate()`)
Values are matched in this priority order:
1. Starts with `[` / ends with `]` → `String[]` (JSON-style list)
2. Contains `,` → `String[]` (comma-separated list)
3. `true` / `false` (case-insensitive) → `boolean`
4. Regex `/^-?\d+[lL]?$/` → `long`
5. Regex `/^-?\d*\.\d+([eE][+-]?\d+)?$/` → `double`
6. Everything else → `String`

### Namespace resolution
Default: `dotenv.{module-name}` where the module name is sanitized with `replaceAll(/[^A-Za-z0-9_]/, "_")`.  
Override via `dotenv { namespace = "com.example.myenv" }` in the module's `build.gradle`.

### Generated output path
```
build/generated/dotenv/src/main/java/{namespace/as/path}/DotEnv.java
```

### `.env` parsing rules
- Lines starting with `#` are ignored (comments)
- Lines without `=` are ignored
- Double-quoted string values have quotes stripped
- Keys are uppercased and non-alphanumeric characters replaced with `_`

### Code generation
All Java source generation uses **JavaPoet** (`com.squareup:javapoet:1.13.0`). Do not generate Java source via string concatenation — use `FieldSpec`, `TypeSpec`, `JavaFile`, and `CodeBlock` builders.

## Obfuscation

Sensitive fields can be obfuscated so the secret never appears as a string literal anywhere in compiled output.

```groovy
dotenv {
    obfuscate = ["API_KEY", "TOKEN"]   // field names as they appear in DotEnv.java
}
```

**How it works:**
- For each obfuscated field, a private helper class is generated alongside `DotEnv.java` with a random 8-hex-char name (e.g. `_a3f2b1c.java`). The class name and all values change on every build.
- The helper uses a **Custom Virtual Machine** architecture with 4 hardening layers:

  1. **Custom VM (Bytecode Interpreter)** — the string is encoded as a bytecode program (`int[] _pg`) with custom opcodes (`APPEND`, `NOP`, `FAKE_JMP`, `HALT`). Opcode values are randomized per build. A `while` loop with a program counter (`_pc`) interprets the encrypted bytecode. The reverse-engineer must first reverse the VM architecture before understanding the logic.
  2. **XOR Encryption** — all program bytes (opcodes + operands) are XOR-encrypted at build time with a key derived from `ClassName.class.getName().hashCode()`. The key is never stored — it's derived at runtime from the class's own identity. This also acts as accidental anti-tampering: renaming the class (e.g. via ProGuard) breaks decryption.
  3. **Mixed Boolean-Arithmetic (MBA)** — opcode comparisons use algebraically equivalent but hard-to-simplify expressions instead of simple `==`. Pool: `(x & C) + (x | C) == 2C` (bitwise identity), `((x - C) | (C - x)) >>> 31 == 0` (sign-bit trick), `x² + C² - 2xC == 0` (quadratic form), compound `>=` + `<=`. Randomly selected per comparison per build.
  4. **Enriched Dead Code** — 6 patterns: fake char arithmetic, fake StringBuilder+return, misleading append, fake hash loops, fake array lookups, fake bitwise "decryption". Injected both as always-false guarded blocks in the interpreter dispatch and as fake VM opcodes (`NOP`, `FAKE_JMP`) interleaved in the encrypted program.

- `DotEnv.java` delegates: `public static final String API_KEY = _a3f2b1c.get();`
- Consumer API is unchanged. Only `String` fields can be obfuscated; listing a `boolean`, `long`, `double`, or `String[]` field fails the build.

**Limitations:** the arithmetic key is embedded in the bytecode, so a determined reverse-engineer can still recover values — obfuscation makes it non-trivial, not impossible.

### Plugin compatibility
The plugin hooks into three plugin IDs: `java`, `com.android.library`, `com.android.application`. Any new target platforms must register their own `project.plugins.withId(...)` block.

### Java compatibility
Source and target compatibility is set to **Java 8** (`VERSION_1_8`).

### Groovy + Gradle decoration pitfall
Helper methods called from within closures inside a `@TaskAction` must be `protected`, not `private`. Gradle wraps task classes in a `_Decorated` proxy and routes property/method access through the MOP — `private` members become invisible. Similarly, never store mutable state (e.g. `Random`) as a task field; create it as a local variable inside `@TaskAction` and pass it as a parameter.

### Stict Rule
- Do not commit without human review
