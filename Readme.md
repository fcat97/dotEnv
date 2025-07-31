# DotEnv Gradle Plugin

A Gradle plugin to generate a `DotEnv` Java class from a `.env` file for Android and Java/Kotlin projects.
The generated class contains your environment variables as `public static final` fields, ready for use in your source code.

---

## Features

- **Automatic code generation**: Generates a Java class at build time.
- **Works for Android & Java modules**: Supports both `com.android.library`/`com.android.application` and `java` plugins.
- **Supports primitives**: Recognizes boolean, long, double, and generates appropriately typed fields.
- **List support**: Recognizes JSON-style and comma-separated lists and creates a `String[]` field.
- **No runtime dependency**: All values are constants in the generated class.

---

## Usage

### 1. Apply the Plugin

Add to your module-level `build.gradle`:

```groovy
plugins {
    id 'java' // or 'com.android.library' or 'com.android.application'
    id 'com.github.fcat97.dotenv-generator'
}
```

### 2. Create your `.env` file

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

### 3. Build your project

On build, the plugin generates `build/generated/dotenv/src/main/java/dotenv/DotEnv.java`.

### 4. Use in your code

```java
import dotenv.DotEnv;

String apiKey = DotEnv.API_KEY;
String endpoint = DotEnv.ENDPOINT_URL;
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

- **No support for integer (int) type:** All numeric values are treated as long or double based on format.
- **No nested objects or multi-dimensional arrays** (e.g., `FOO=[["a","b"],["c","d"]]` is not supported).
- **No support for export syntax** (`export KEY=value` is ignored).
- **No escaping of special characters inside lists** (e.g., commas inside quoted list items may not parse as intended).
- **No runtime reload**: The class is generated at build time; changes to `.env` require a rebuild.

---

## Regenerating on Change

If you change your `.env` file, simply rebuild your project to regenerate the `DotEnv` class.

---

## Troubleshooting

- If the `DotEnv` class isn't found, try:
   - Cleaning and rebuilding the project.
   - Ensuring the `.env` file exists in the module root.
   - Checking `build/generated/dotenv/src/main/java/dotenv/DotEnv.java` for the generated file.
- If using Kotlin, you can access constants the same way as in Java.

---

## Contributing

Feel free to open issues or PRs for new features or bug fixes!

---