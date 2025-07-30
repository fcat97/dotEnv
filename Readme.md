# DotEnv Gradle Plugin (Groovy)

This plugin generates a `DotEnv` Java class in each module containing all key-value pairs from the module's `.env` file.

## Usage

1. **Build and publish the plugin locally:**
   ```
   ./gradlew publishToMavenLocal
   ```

2. **In your application module's `build.gradle`:**
   ```groovy
   plugins {
       id 'java'
       id 'com.github.fcat97.dotenv-generator'
   }
   ```

3. **Create a `.env` file** in your module's root:
   ```
   API_KEY=abcdef
   DB_URL=jdbc:mysql://...
   ```

4. **Access in code:**
   ```java
   import dotenv.DotEnv;
   String apiKey = DotEnv.API_KEY;
   ```

5. **Build triggers code generation automatically.**

---

- The generated class is in `build/generated/dotenv/DotEnv.java`.
- The plugin automatically adds the generated directory to the source set.