package io.github.fcat97

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class DotEnvPluginFunctionalTest {

    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder()

    // ── helpers ─────────────────────────────────────────────────────────────

    private void writeSettings(String projectName = 'test-project') {
        testProjectDir.newFile('settings.gradle').text =
            "rootProject.name = '${projectName}'"
    }

    private void writeBuildGradle(String extra = '') {
        testProjectDir.newFile('build.gradle').text = """
            plugins {
                id 'java'
                id 'io.github.fcat97.dotenv'
            }
            ${extra}
        """.stripIndent()
    }

    private void writeEnvFile(String content) {
        testProjectDir.newFile('.env').text = content
    }

    private String generatedFileContent(String namespace = 'dotenv/test_project') {
        new File(testProjectDir.root,
            "build/generated/dotenv/src/main/java/${namespace}/DotEnv.java").text
    }

    private def runTask(String task = 'generateDotEnv') {
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments(task, '--stacktrace')
            .withPluginClasspath()
            .build()
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void testAllSupportedTypesAreGeneratedCorrectly() {
        writeSettings()
        writeBuildGradle()
        writeEnvFile("""\
            API_KEY=super-secret
            NAME="Test Name"
            IS_PROD=false
            TIMEOUT=5000
            PI=3.1415
            LONG_NUMBER=9223372036854775807
            PLATFORMS=["android", "desktop"]
            LANGUAGES=en,fr,es
        """.stripIndent())

        def result = runTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)

        def content = generatedFileContent()
        assertTrue(content.contains('String API_KEY = "super-secret"'))
        assertTrue(content.contains('String NAME = "Test Name"'))
        assertTrue(content.contains('boolean IS_PROD = false'))
        assertTrue(content.contains('long TIMEOUT = 5000L'))
        assertTrue(content.contains('double PI = 3.1415'))
        assertTrue(content.contains('long LONG_NUMBER = 9223372036854775807L'))
        assertTrue(content.contains('String[] PLATFORMS'))
        assertTrue(content.contains('"android"'))
        assertTrue(content.contains('"desktop"'))
        assertTrue(content.contains('String[] LANGUAGES'))
        assertTrue(content.contains('"en"'))
        assertTrue(content.contains('"fr"'))
        assertTrue(content.contains('"es"'))
    }

    @Test
    void testDefaultNamespaceUsesModuleName() {
        writeSettings('my-module')
        writeBuildGradle()
        writeEnvFile('KEY=value')

        def result = runTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)

        def generatedFile = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/my_module/DotEnv.java')
        assertTrue('Generated file should use sanitized module name', generatedFile.exists())
        assertTrue(generatedFile.text.contains('package dotenv.my_module'))
    }

    @Test
    void testCustomNamespaceOverridesDefault() {
        writeSettings()
        writeBuildGradle("dotenv { namespace = 'com.example.config' }")
        writeEnvFile('KEY=value')

        def result = runTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)

        def generatedFile = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/com/example/config/DotEnv.java')
        assertTrue('Generated file with custom namespace should exist', generatedFile.exists())
        assertTrue(generatedFile.text.contains('package com.example.config'))
    }

    @Test
    void testMissingEnvFileFailsTheBuild() {
        writeSettings()
        writeBuildGradle()
        // intentionally no .env file

        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('generateDotEnv', '--stacktrace')
            .withPluginClasspath()
            .buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(':generateDotEnv').outcome)
        assertTrue('Should report missing .env file', result.output.contains('.env file not found'))
    }

    @Test
    void testCommentsAndBlankLinesAreIgnored() {
        writeSettings()
        writeBuildGradle()
        writeEnvFile("""\
            # This is a comment
            KEY=value

            # Another comment
            OTHER=123
        """.stripIndent())

        def result = runTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)

        def content = generatedFileContent()
        assertFalse('Comment text must not appear in output', content.contains('This is a comment'))
        assertTrue(content.contains('KEY'))
        assertTrue(content.contains('OTHER'))
    }

    @Test
    void testBooleanIsCaseInsensitive() {
        writeSettings()
        writeBuildGradle()
        writeEnvFile("""\
            FLAG_TRUE=true
            FLAG_FALSE=false
            FLAG_UPPER=TRUE
            FLAG_MIXED=False
        """.stripIndent())

        def result = runTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)

        def content = generatedFileContent()
        assertTrue(content.contains('boolean FLAG_TRUE = true'))
        assertTrue(content.contains('boolean FLAG_FALSE = false'))
        assertTrue(content.contains('boolean FLAG_UPPER = true'))
        assertTrue(content.contains('boolean FLAG_MIXED = false'))
    }

    @Test
    void testKeyWithSpecialCharsIsSanitized() {
        writeSettings()
        writeBuildGradle()
        writeEnvFile('my-key=value')

        def result = runTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)

        def content = generatedFileContent()
        assertTrue('Hyphen in key should become underscore', content.contains('MY_KEY'))
        assertFalse(content.contains('MY-KEY'))
    }

    @Test
    void testDoubleQuotedStringStripsQuotes() {
        writeSettings()
        writeBuildGradle()
        writeEnvFile('URL="https://api.example.com"')

        def result = runTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)

        def content = generatedFileContent()
        assertTrue(content.contains('String URL = "https://api.example.com"'))
        assertFalse('Outer quotes should be stripped', content.contains('""https://'))
    }

    @Test
    void testJsonStyleListParsedAsStringArray() {
        writeSettings()
        writeBuildGradle()
        writeEnvFile('TAGS=["alpha", "beta", "gamma"]')

        def result = runTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)

        def content = generatedFileContent()
        assertTrue(content.contains('String[] TAGS'))
        assertTrue(content.contains('"alpha"'))
        assertTrue(content.contains('"beta"'))
        assertTrue(content.contains('"gamma"'))
    }

    @Test
    void testCommaSeparatedListParsedAsStringArray() {
        writeSettings()
        writeBuildGradle()
        writeEnvFile('ENVS=dev,staging,prod')

        def result = runTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)

        def content = generatedFileContent()
        assertTrue(content.contains('String[] ENVS'))
        assertTrue(content.contains('"dev"'))
        assertTrue(content.contains('"staging"'))
        assertTrue(content.contains('"prod"'))
    }

    @Test
    void testObfuscatedValueDeobfuscatesCorrectlyAtRuntime() {
        writeSettings()
        writeBuildGradle("""
            dotenv { obfuscate = ['SECRET', 'ANOTHER'] }
            task verifyObfuscation(type: JavaExec, dependsOn: classes) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = 'Verify'
            }
        """)
        writeEnvFile("SECRET=hello-world-123\nANOTHER=foo\$bar!baz")

        def srcDir = new File(testProjectDir.root, 'src/main/java')
        srcDir.mkdirs()
        new File(srcDir, 'Verify.java').text = """\
            public class Verify {
                public static void main(String[] args) {
                    System.out.println(dotenv.test_project.DotEnv.SECRET);
                    System.out.println(dotenv.test_project.DotEnv.ANOTHER);
                }
            }
        """.stripIndent()

        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('verifyObfuscation', '--stacktrace')
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(':verifyObfuscation').outcome)
        assertTrue('SECRET must deobfuscate to original value at runtime',
            result.output.contains('hello-world-123'))
        assertTrue('ANOTHER must deobfuscate to original value at runtime',
            result.output.contains('foo$bar!baz'))
    }

    @Test
    void testObfuscatedFieldDelegatesAndCompilesSuccessfully() {
        writeSettings()
        writeBuildGradle("dotenv { obfuscate = ['API_KEY'] }")
        writeEnvFile("API_KEY=super-secret\nPLAIN=visible")

        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('compileJava', '--stacktrace')
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(':compileJava').outcome)

        def dotEnvContent = generatedFileContent()
        // DotEnv.java must NOT contain the plain secret
        assertFalse('Plain secret must not appear in DotEnv.java', dotEnvContent.contains('super-secret'))
        // Must delegate to a helper class via a method call
        assertTrue('DotEnv.java must contain a helper delegation call',
            (dotEnvContent =~ /_[a-f0-9]{8}\.get\(\)/).find())
        // Non-obfuscated field must still be a plain literal
        assertTrue(dotEnvContent.contains('String PLAIN = "visible"'))
    }

    @Test
    void testObfuscatedHelperUsesVmPattern() {
        writeSettings()
        writeBuildGradle("dotenv { obfuscate = ['KEY'] }")
        writeEnvFile('KEY=secret')

        runTask()

        def genDir = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project')
        def helperFile = genDir.listFiles().find { it.name =~ /^_[a-f0-9]{8}\.java$/ }

        assertNotNull('Helper class file should exist', helperFile)
        def content = helperFile.text
        assertTrue('Helper should use while loop (VM interpreter)', content.contains('while'))
        assertTrue('Helper should use program counter (_pc)', content.contains('_pc'))
        assertTrue('Helper should have encrypted program array (_pg)', content.contains('_pg'))
        assertTrue('Helper should decrypt via _raw variable', content.contains('_raw'))
        assertTrue('Helper should use rolling state (_st)', content.contains('_st'))
        assertTrue('Helper should derive key via custom hash (not hashCode)',
            content.contains('.class.getName().getBytes()'))
        assertTrue('Helper should use Integer.rotateLeft for key rotation',
            content.contains('Integer.rotateLeft'))
        assertTrue('Helper should embed opaque predicate seed (_op)', content.contains('_op'))
        assertTrue('Helper should use switch dispatch', content.contains('switch'))
        assertFalse('Plain secret must not appear in helper class', content.contains('secret'))
    }

    @Test
    void testObfuscatedHelperUsesMbaExpressions() {
        writeSettings()
        writeBuildGradle("dotenv { obfuscate = ['KEY'] }")
        writeEnvFile('KEY=test')

        runTask()

        def genDir = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project')
        def helperFile = genDir.listFiles().find { it.name =~ /^_[a-f0-9]{8}\.java$/ }

        assertNotNull('Helper class file should exist', helperFile)
        def content = helperFile.text
        // Should contain MBA scrambled dispatch (_sv variable)
        assertTrue('Helper should use MBA-scrambled dispatch (_sv)',
            content.contains('_sv'))
        // Should contain MBA indicators (XOR + AND compound expressions)
        assertTrue('Helper should use MBA expressions (^ and & compound ops)',
            (content =~ /\(_raw \^ \d+\) \+ 2 \* \(_raw & \d+\)/).find())
        // Should NOT contain simple opcode equality like `_raw == 42`
        assertFalse('Helper should not use simple opcode equality',
            (content =~ /_raw\s*==\s*\d+/).find())
    }

    @Test
    void testObfuscatingNonStringFieldFailsTheBuild() {
        writeSettings()
        writeBuildGradle("dotenv { obfuscate = ['IS_PROD'] }")
        writeEnvFile('IS_PROD=true')

        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('generateDotEnv', '--stacktrace')
            .withPluginClasspath()
            .buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(':generateDotEnv').outcome)
        assertTrue('Should report that only String fields can be obfuscated',
            result.output.contains("Cannot obfuscate field 'IS_PROD'"))
    }

    @Test
    void testObfuscatedOutputDiffersAcrossBuilds() {
        writeSettings()
        writeBuildGradle("dotenv { obfuscate = ['SECRET'] }")
        writeEnvFile('SECRET=my-secret-value')

        runTask()
        def call1 = (generatedFileContent() =~ /_[a-f0-9]{8}\.get\(\)/)[0]

        // Clean build output and rebuild
        new File(testProjectDir.root, 'build').deleteDir()
        runTask()
        def call2 = (generatedFileContent() =~ /_[a-f0-9]{8}\.get\(\)/)[0]

        assertNotNull('First build should have an obfuscated call', call1)
        assertNotNull('Second build should have an obfuscated call', call2)
        assertNotEquals('Helper class name must differ between builds', call1, call2)
    }

    @Test
    void testNonObfuscatedFieldsUnaffectedWhenObfuscateListIsPresent() {
        writeSettings()
        writeBuildGradle("dotenv { obfuscate = ['SECRET'] }")
        writeEnvFile("""\
            SECRET=hidden
            API_URL="https://api.example.com"
            TIMEOUT=3000
            IS_DEBUG=true
        """.stripIndent())

        runTask()

        def content = generatedFileContent()
        // Plain fields must still be literals
        assertTrue(content.contains('String API_URL = "https://api.example.com"'))
        assertTrue(content.contains('long TIMEOUT = 3000L'))
        assertTrue(content.contains('boolean IS_DEBUG = true'))
        // Obfuscated field must not be a literal
        assertFalse(content.contains('"hidden"'))
    }

    @Test
    void testGenerateDotEnvRunsBeforeCompileJava() {
        writeSettings()
        writeBuildGradle()
        writeEnvFile('KEY=value')

        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('compileJava', '--dry-run')
            .withPluginClasspath()
            .build()

        def output = result.output
        def generateIndex = output.indexOf(':generateDotEnv')
        def compileIndex = output.indexOf(':compileJava')
        assertTrue('generateDotEnv must appear before compileJava in task graph',
            generateIndex >= 0 && compileIndex >= 0 && generateIndex < compileIndex)
    }

    @Test
    void testObfuscatedHelperUsesNonLinearJumps() {
        writeSettings()
        writeBuildGradle("dotenv { obfuscate = ['KEY'] }")
        writeEnvFile('KEY=abcdefghij')

        runTask()

        def genDir = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project')
        def helperFile = genDir.listFiles().find { it.name =~ /^_[a-f0-9]{8}\.java$/ }

        assertNotNull('Helper class file should exist', helperFile)
        def content = helperFile.text
        // JUMP target handling: _pc = _tgt (non-linear jump)
        assertTrue('Helper should contain non-linear jump (_tgt assignment to _pc)',
            content.contains('_pc = _tgt'))
        // The program should not use simple hashCode()
        assertFalse('Helper should NOT use String.hashCode()',
            content.contains('.hashCode()'))
        // Rolling state variable must be present
        assertTrue('Helper should use rolling state (_st) for decryption',
            content.contains('_st = (_st * 31'))
    }

    @Test
    void testObfuscatedHelperUsesCustomHashNotHashCode() {
        writeSettings()
        writeBuildGradle("dotenv { obfuscate = ['KEY'] }")
        writeEnvFile('KEY=test')

        runTask()

        def genDir = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project')
        def helperFile = genDir.listFiles().find { it.name =~ /^_[a-f0-9]{8}\.java$/ }

        assertNotNull('Helper class file should exist', helperFile)
        def content = helperFile.text
        // Should use custom hash with byte iteration, not hashCode()
        assertTrue('Helper should iterate over class name bytes',
            content.contains('.class.getName().getBytes()'))
        assertFalse('Helper must NOT use String.hashCode()',
            content.contains('.hashCode()'))
        // Should contain a long salt literal
        assertTrue('Helper should contain a long salt constant',
            (content =~ /long _ha = -?\d+L/).find())
        // Should contain a long prime multiplier
        assertTrue('Helper should contain a long prime multiplier',
            (content =~ /_ha \*= -?\d+L/).find())
    }

    @Test
    void testObfuscatedHelperUsesHardenedPredicates() {
        writeSettings()
        writeBuildGradle("dotenv { obfuscate = ['KEY'] }")
        writeEnvFile('KEY=test')

        runTask()

        def genDir = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project')
        def helperFile = genDir.listFiles().find { it.name =~ /^_[a-f0-9]{8}\.java$/ }

        assertNotNull('Helper class file should exist', helperFile)
        def content = helperFile.text
        // Hardened predicates use compound MBA with multiple live variables
        // They should NOT use trivially-foldable patterns like (_op * 0 != 0)
        assertFalse('Should not use trivially-foldable predicate (_op * 0)',
            content.contains('_op * 0'))
        assertFalse('Should not use trivially-foldable predicate (_op & 0)',
            content.contains('(_op & 0)'))
        assertFalse('Should not use trivially-foldable predicate (_op ^ 0)',
            content.contains('(_op ^ 0)'))
        // Should use MBA with two variables (e.g., _op and _pc or _st)
        assertTrue('Should use hardened MBA predicates with multiple runtime variables',
            content.contains('_op & _pc') || content.contains('_op & _st') ||
            content.contains('_st ^ _op') || content.contains('_pc ^ _op') ||
            content.contains('_st | _op') || content.contains('_pc | _op'))
    }

    // ── Kotlin JVM compatibility ─────────────────────────────────────────────

    @Test
    void testKotlinJvmProjectGeneratesAndCompilesCorrectly() {
        testProjectDir.newFile('settings.gradle').text = """\
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = 'test-project'
        """.stripIndent()

        testProjectDir.newFile('build.gradle').text = """\
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.9.25'
                id 'io.github.fcat97.dotenv'
            }
            repositories { mavenCentral() }
        """.stripIndent()

        testProjectDir.newFile('.env').text = """\
            API_KEY=kotlin-secret
            IS_DEBUG=false
        """.stripIndent()

        // Kotlin source file that references the generated DotEnv class
        def srcDir = testProjectDir.newFolder('src', 'main', 'kotlin', 'example')
        new File(srcDir, 'App.kt').text = """\
            package example
            import dotenv.test_project.DotEnv
            fun getKey(): String = DotEnv.API_KEY
            fun isDebug(): Boolean = DotEnv.IS_DEBUG
        """.stripIndent()

        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('compileKotlin', '--stacktrace')
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(':compileKotlin').outcome)

        def content = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project/DotEnv.java').text
        assertTrue(content.contains('String API_KEY = "kotlin-secret"'))
        assertTrue(content.contains('boolean IS_DEBUG = false'))
    }
}
