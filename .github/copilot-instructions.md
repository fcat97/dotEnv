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
- The helper uses three layers of obfuscation:
  1. **Control Flow Flattening** — a `while` loop driven by a random state machine; state values (e.g. `7842 → 2341 → 5521`) are unique random ints that change every build
  2. **Opaque Predicates** — real code is wrapped in always-true conditions (e.g. `(_op * _op) >= 0`); dead code sits under always-false conditions (e.g. `_op != _op`). `_op` is a random int seed embedded at build time
  3. **Dead Code Injection** — unreachable blocks under always-false guards contain fake char computations, misleading `append()` calls, and occasional fake `return` statements
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
