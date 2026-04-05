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
Tests: `./gradlew test` (21 tests covering all types, obfuscation, runtime correctness, and structural verification).

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
- The helper uses a **Custom Virtual Machine** architecture with 5 hardening layers:

  1. **Custom Hash Key Derivation** — replaces the predictable `String.hashCode()` with a per-build FNV-like hash function using a random salt (embedded as `long` field) and a random large odd prime multiplier. An attacker must reverse-engineer the specific hash function for each build — there's no standard algorithm to look up. Also preserves anti-tamper: renaming the class breaks decryption.
  2. **Non-Linear Bytecode Execution** — the string is split into shuffled blocks connected by JUMP opcodes. The program array holds characters in random physical order, connected by jumps. A linear scan of the array produces garbage. Opcodes: `APPEND`, `NOP`, `JUMP`, `HALT` (values randomized per build). This defeats symbolic execution tools (Triton, Angr) by creating a non-linear control flow graph.
  3. **State-Dependent Rolling Decryption** — each byte's decryption depends on ALL previously decrypted bytes via a rolling state variable (`_st`). Combined with JUMP-based non-linear execution, an attacker cannot decrypt byte N without correctly tracing the full execution path from byte 0 through N-1 in execution order.
  4. **MBA-Scrambled Switch Dispatch** — opcode dispatch uses a `switch` on a value computed via the MBA identity `(x ^ C) + 2*(x & C) = x + C`. The scramble constant `C` changes per build. Decompilers see an opaque math function instead of simple opcode comparisons.
  5. **Hardened Opaque Predicates + Dead Code** — always-true/false predicates use MBA identities with live runtime variables (`_op`, `_pc`, `_st`) so decompilers cannot constant-fold them. 6 dead code patterns (fake hash loops, fake StringBuilder, fake arrays, fake crypto, etc.) are injected both inside switch cases and around the VM loop.

- `DotEnv.java` delegates: `public static final String API_KEY = _a3f2b1c.get();`
- Consumer API is unchanged. Only `String` fields can be obfuscated; listing a `boolean`, `long`, `double`, or `String[]` field fails the build.

**Limitations:** A determined reverse-engineer can still recover values by tracing the VM execution path, but the layered obfuscation makes it significantly harder than simple string literals or arithmetic encoding.

### Plugin compatibility
The plugin hooks into three plugin IDs: `java`, `com.android.library`, `com.android.application`. Any new target platforms must register their own `project.plugins.withId(...)` block.

### Java compatibility
Source and target compatibility is set to **Java 8** (`VERSION_1_8`).

### Groovy + Gradle decoration pitfall
Helper methods called from within closures inside a `@TaskAction` must be `protected`, not `private`. Gradle wraps task classes in a `_Decorated` proxy and routes property/method access through the MOP — `private` members become invisible. Similarly, never store mutable state (e.g. `Random`) as a task field; create it as a local variable inside `@TaskAction` and pass it as a parameter.

### Stict Rule
- Do not commit without human review
