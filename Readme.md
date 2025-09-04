# DotEnv Gradle Plugin

A Gradle plugin to generate a `DotEnv` Java class from a `.env` file for Android and Java/Kotlin projects.  
The generated class contains your environment variables as `public static final` fields, ready for use in your source code.

---

## Purpose

**DotEnv Gradle Plugin** is designed to make module-scoped environment management easy, especially for multi-module projects.  
Unlike Gradle’s `local.properties` (which is always global to the project root), this plugin allows each module to have its own `.env` file and its own generated `DotEnv` class.  
This is particularly useful for modularized Android or Java projects where each module may need distinct configuration, secrets, or toggles.

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
  By default, each module’s class is in `dotenv.{module-name}.DotEnv`, but you can override the namespace for your needs.

- **No risk of leaking secrets between modules:**  
  Each module only has access to its own `.env` values.

---

## Features

- **Automatic code generation**: Generates a Java class at build time.
- **Works for Android & Java modules**: Supports both `com.android.library`/`com.android.application` and `java` plugins.
- **Supports primitives**: Recognizes boolean, long, double, and generates appropriately typed fields.
- **List support**: Recognizes JSON-style and comma-separated lists and creates a `String[]` field.
- **Namespace per module**: Each module gets its own DotEnv class in a unique package.
- **Customizable namespace**: Override the package via plugin configuration.
- **No runtime dependency**: All values are constants in the generated class.

---

## Usage

### 1. Apply the Plugin

Add to your module-level `build.gradle`:

```groovy
plugins {
    id 'java' // or 'com.android.library' or 'com.android.application'
    id 'io.github.fcat97.dotenv'
}
```

### 2. (Optional) Configure the Namespace

By default, the generated class will be in `dotenv.{module-name}` (e.g. `dotenv.app` for module `app`).

You can override this by specifying the `namespace` in a `dotenv` block:

```groovy
dotenv {
    namespace = "com.example.mydotenv"
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
```
build/generated/dotenv/src/main/java/dotenv/{module-name}/DotEnv.java
```
or, if you specified a custom namespace:
```
build/generated/dotenv/src/main/java/{custom/namespace}/DotEnv.java
```

### 5. Use in your code

If you use the default namespace:
```java
import dotenv.app.DotEnv; // for module 'app'
import dotenv.feature_login.DotEnv; // for module 'feature_login'
```

If you set a custom namespace:
```java
import com.example.mydotenv.DotEnv;
```

Example usage:
```java
String apiKey = DotEnv.API_KEY;
boolean isProd = DotEnv.IS_PROD;
long timeout = DotEnv.TIMEOUT;
double pi = DotEnv.PI;
long bigNum = DotEnv.LONG_NUMBER;
String[] platforms = DotEnv.PLATFORMS;
String[] languages = DotEnv.LANGUAGES;
```

---

## Supported `.env` Formats

| Format                                   | Result in DotEnv.java                                 | Example usage                             |
|-------------------------------------------|-------------------------------------------------------|-------------------------------------------|
| Simple string                            | `public static final String`                          | `API_KEY=abc123`                          |
| Double-quoted string                     | `public static final String` (quotes are stripped)    | `URL="https://foo.com"`                   |
| Boolean                                  | `public static final boolean`                         | `IS_PROD=true` / `IS_PROD=false`          |
| Long integer                             | `public static final long`                            | `TIMEOUT=1234`                            |
| Double/float                             | `public static final double`                          | `PI=3.1415`                               |
| JSON-style list (`[a, b, c]`)            | `public static final String[]`                        | `PLATFORMS=["android","desktop"]`         |
| Comma-separated list (`a,b,c`)           | `public static final String[]`                        | `LANGUAGES=en,fr,es`                      |

### Examples

.env file:
```
API_KEY=super-secret
NAME="Test Name"
IS_PROD=false
TIMEOUT=5000
PI=3.1415
LONG_NUMBER=9223372036854775807
PLATFORMS=["android", "desktop"]
LANGUAGES=en,fr,es
```

Result:
```java
public static final String API_KEY = "super-secret";
public static final String NAME = "Test Name";
public static final boolean IS_PROD = false;
public static final long TIMEOUT = 5000L;
public static final double PI = 3.1415;
public static final long LONG_NUMBER = 9223372036854775807L;
public static final String[] PLATFORMS = { "android", "desktop" };
public static final String[] LANGUAGES = { "en", "fr", "es" };
```

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
  - Checking `build/generated/dotenv/src/main/java/{your/namespace}/DotEnv.java` for the generated file.
- If using Kotlin, you can access constants the same way as in Java.

---

## Contributing

Feel free to open issues or PRs for new features or bug fixes!

---
