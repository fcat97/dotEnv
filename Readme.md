# DotEnv Gradle Plugin

A Gradle plugin to generate a `DotEnv` class from a `.env` file for Android, Java, Kotlin/JVM, and Kotlin Multiplatform (KMP) projects.
The generated class contains your environment variables as typed constants, ready for use in your source code.

---

## Purpose

**DotEnv Gradle Plugin** is designed to make module-scoped environment management easy, especially for multi-module projects.
Unlike Gradle's `local.properties` (which is always global to the project root), this plugin allows each module to have its own `.env` file and its own generated `DotEnv` class.
This is particularly useful for modularized Android, Java, Kotlin backend, or KMP projects where each module may need distinct configuration, secrets, or toggles.

---

## Why use this over `local.properties`?

- **Module-level config:**
  `local.properties` is global and cannot be used per module. With DotEnv, each module can have its own `.env` and matching `DotEnv` class.

- **No manual parsing:**
  No need to write Groovy or Kotlin scripts to parse `.env` or `.properties` files—everything is generated for you.

- **Typed constants:**
  DotEnv supports not only strings, but booleans, long, double, and string arrays, so your config is strongly typed.

- **Easy IDE integration:**
  IDE code completion works out of the box for generated constants.

- **Customizable namespace:**
  By default, each module's class is in `dotenv.{module-name}.DotEnv`, but you can override the namespace for your needs.

- **No risk of leaking secrets between modules:**
  Each module only has access to its own `.env` values.

---

## Features

- **Automatic code generation**: Generates `DotEnv.java` (Java/Android) or `DotEnv.kt` (Kotlin/KMP) at build time.
- **Works for Android, Java, Kotlin/JVM, Spring Boot & KMP**: Supports `com.android.library`, `com.android.application`, `java`, `org.jetbrains.kotlin.jvm`, and `org.jetbrains.kotlin.multiplatform` plugins.
- **Supports primitives**: Recognizes boolean, long, double, and generates appropriately typed fields.
- **List support**: Recognizes JSON-style and comma-separated lists and creates a `String[]` / `Array<String>` field.
- **Namespace per module**: Each module gets its own DotEnv class in a unique package.
- **Customizable namespace**: Override the package via plugin configuration.
- **No runtime dependency**: All values are constants in the generated class.
- **Obfuscation**: Sensitive string fields can be obfuscated so the secret never appears as a literal in compiled output (see [Obfuscation](#obfuscation) below).

---

## Usage

### 1. Apply the Plugin

**Java / Android** (`build.gradle`):

```groovy
plugins {
    id 'java' // or 'com.android.library', 'com.android.application'
    id 'io.github.fcat97.dotenv'
}
```

**Kotlin/JVM / Spring Boot** (`build.gradle.kts`):

```kotlin
plugins {
    kotlin("jvm") version "1.9.25"
    id("io.github.fcat97.dotenv")
}
```

**Kotlin Multiplatform** (`build.gradle.kts`):

```kotlin
plugins {
    kotlin("multiplatform") version "1.9.25"
    id("io.github.fcat97.dotenv")
}

kotlin {
    jvm()
    // js(IR) { browser() }
    // wasmJs { browser() }
    // linuxX64()
    // etc.
}
```

### 2. (Optional) Configure the Plugin

By default, the generated class will be in `dotenv.{module-name}` (e.g. `dotenv.app` for module `app`).

**Groovy DSL:**
```groovy
dotenv {
    namespace = "com.example.mydotenv"   // optional: override package name
    obfuscate = ["API_KEY", "SECRET"]    // optional: obfuscate sensitive String fields
}
```

**Kotlin DSL:**
```kotlin
dotenv {
    namespace = "com.example.mydotenv"
    obfuscate = listOf("API_KEY", "SECRET")
}
```

### 3. Create your `.env` file

Place a `.env` file in your module's root directory.

**Example:**
```
API_KEY=super-secret-api-key
ENDPOINT_URL="https://api.example.com"
IS_PROD=true
TIMEOUT=3000
PI=3.1415
LONG_NUMBER=9223372036854775807
PLATFORMS=["android", "desktop"]
LANGUAGES=en,fr,es
```

### 4. Build your project

On build, the plugin generates:

- **Java / Android** → `DotEnv.java`
- **Kotlin/JVM, Kotlin Multiplatform** → `DotEnv.kt`

```
build/generated/dotenv/src/main/java/dotenv/{module-name}/DotEnv.java   (Java/Android)
build/generated/dotenv/src/main/java/dotenv/{module-name}/DotEnv.kt     (Kotlin/KMP)
```

### 5. Use in your code

**Java:**
```java
import dotenv.app.DotEnv;

String apiKey = DotEnv.API_KEY;
boolean isProd = DotEnv.IS_PROD;
long timeout = DotEnv.TIMEOUT;
double pi = DotEnv.PI;
String[] platforms = DotEnv.PLATFORMS;
```

**Kotlin:**
```kotlin
import dotenv.app.DotEnv

val apiKey: String = DotEnv.API_KEY
val isProd: Boolean = DotEnv.IS_PROD
val timeout: Long = DotEnv.TIMEOUT
val pi: Double = DotEnv.PI
val platforms: Array<String> = DotEnv.PLATFORMS
```

---

## Supported `.env` Formats

| Format                                   | Java (`DotEnv.java`)                           | Kotlin (`DotEnv.kt`)         | Example                           |
|------------------------------------------|------------------------------------------------|------------------------------|-----------------------------------|
| Simple string                            | `public static final String`                   | `const val … : String`       | `API_KEY=abc123`                  |
| Double-quoted string                     | `public static final String` (quotes stripped) | `const val … : String`       | `URL="https://foo.com"`           |
| Boolean                                  | `public static final boolean`                  | `const val … : Boolean`      | `IS_PROD=true`                    |
| Long integer                             | `public static final long`                     | `const val … : Long`         | `TIMEOUT=1234`                    |
| Double/float                             | `public static final double`                   | `const val … : Double`       | `PI=3.1415`                       |
| JSON-style list (`[a, b, c]`)            | `public static final String[]`                 | `val … : Array<String>`      | `PLATFORMS=["android","desktop"]` |
| Comma-separated list (`a,b,c`)           | `public static final String[]`                 | `val … : Array<String>`      | `LANGUAGES=en,fr,es`              |

### Example: Kotlin generated output

`.env`:
```
API_KEY=super-secret
IS_PROD=false
TIMEOUT=5000
PI=3.1415
PLATFORMS=["android", "desktop"]
LANGUAGES=en,fr,es
```

`DotEnv.kt`:
```kotlin
package dotenv.app

object DotEnv {
    const val API_KEY: String = "super-secret"
    const val IS_PROD: Boolean = false
    const val TIMEOUT: Long = 5000L
    const val PI: Double = 3.1415
    val PLATFORMS: Array<String> = arrayOf("android", "desktop")
    val LANGUAGES: Array<String> = arrayOf("en", "fr", "es")
}
```

---

## Obfuscation

Sensitive `String` fields can be obfuscated so the plaintext value never appears as a string literal in the compiled output (class files, DEX, JS, WASM, or native binaries).

```groovy
dotenv {
    obfuscate = ["API_KEY", "TOKEN"]
}
```

For each obfuscated field, the plugin generates a private helper file (e.g. `_a3f2b9.kt`) alongside `DotEnv.kt`. The helper uses a small custom VM interpreter to reconstruct the value at runtime without ever storing it as a plain string literal. The VM uses:

- A randomised opcode set (changed every build)
- Non-linear bytecode execution via JUMP opcodes (shuffled physical block order)
- Rolling-state XOR decryption (each byte depends on all previously decoded bytes)
- MBA-scrambled opcode dispatch (defeats symbolic execution tools)
- Dead code injection (increases reverse-engineering effort)

`DotEnv.kt` delegates obfuscated fields transparently:
```kotlin
val API_KEY: String = _a3f2b9.get()  // value reconstructed at runtime
```

Consumer code is unchanged — access is identical to non-obfuscated fields.

**Limitations:**
- Only `String` fields can be obfuscated. Listing a `boolean`, `long`, `double`, or `Array<String>` field in `obfuscate` will fail the build.
- The Kotlin helper is safe for all Kotlin targets (JVM, JS, WASM, Native) — no JVM reflection is used.
- A determined reverse-engineer can still recover values by tracing VM execution, but it is significantly harder than reading a plain string literal.

---

## Not Supported / Limitations

- **No support for nested objects or multi-dimensional arrays** (e.g., `FOO=[["a","b"],["c","d"]]` is not supported).
- **No support for export syntax** (`export KEY=value` is ignored).
- **No escaping of special characters inside lists** (e.g., commas inside quoted list items may not parse as intended).
- **No runtime reload**: The class is generated at build time; changes to `.env` require a rebuild.

---

## Regenerating on Change

If you change your `.env` file, simply rebuild your project to regenerate the `DotEnv` class.
For real-time regeneration, set up an IDE or OS file watcher to run the `generateDotEnv` task automatically on `.env` changes.

---

## Troubleshooting

- If the `DotEnv` class isn't found, try:
  - Cleaning and rebuilding the project.
  - Ensuring the `.env` file exists in the module root.
  - Checking `build/generated/dotenv/src/main/java/{your/namespace}/DotEnv.java` (Java/Android) or `DotEnv.kt` (Kotlin/KMP) for the generated file.
  - For Kotlin projects, ensure the `kotlin("jvm")` or `kotlin("multiplatform")` plugin is applied — the plugin automatically selects the correct generator.

---

## Contributing

Feel free to open issues or PRs for new features or bug fixes!

---

## Support

If you find this plugin helpful, consider buying me a coffee! ☕

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-Support-yellow?style=for-the-badge&logo=buy-me-a-coffee&logoColor=white)](https://buymeacoffee.com/szaman97)

---
