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

    // ── Kotlin JVM: helper closures ──────────────────────────────────────────

    private void writeKotlinSettings(String projectName = 'test-project') {
        testProjectDir.newFile('settings.gradle').text = """\
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = '${projectName}'
        """.stripIndent()
    }

    private void writeKotlinBuildGradle(String extra = '') {
        testProjectDir.newFile('build.gradle').text = """\
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.9.25'
                id 'io.github.fcat97.dotenv'
            }
            repositories { mavenCentral() }
            ${extra}
        """.stripIndent()
    }

    private String generatedKtFileContent(String namespace = 'dotenv/test_project') {
        new File(testProjectDir.root,
            "build/generated/dotenv/src/main/java/${namespace}/DotEnv.kt").text
    }

    private def runKotlinTask(String task = 'generateDotEnv') {
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments(task, '--stacktrace')
            .withPluginClasspath()
            .build()
    }

    // ── Kotlin JVM compatibility ─────────────────────────────────────────────

    @Test
    void testKotlinJvmProjectGeneratesAndCompilesCorrectly() {
        writeKotlinSettings()
        writeKotlinBuildGradle()

        testProjectDir.newFile('.env').text = """\
            API_KEY=kotlin-secret
            IS_DEBUG=false
        """.stripIndent()

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

        def content = generatedKtFileContent()
        assertTrue(content.contains('const val API_KEY: String = "kotlin-secret"'))
        assertTrue(content.contains('const val IS_DEBUG: Boolean = false'))
    }

    // ── Section A: Kotlin/JVM file generation ────────────────────────────────

    @Test
    void testKotlinJvmGeneratesKtFileNotJavaFile() {
        writeKotlinSettings()
        writeKotlinBuildGradle()
        testProjectDir.newFile('.env').text = 'KEY=value'

        def result = runKotlinTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)

        def genDir = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project')
        assertTrue('DotEnv.kt should be generated for Kotlin JVM',
            new File(genDir, 'DotEnv.kt').exists())
        assertFalse('DotEnv.java must NOT be generated for Kotlin JVM',
            new File(genDir, 'DotEnv.java').exists())
    }

    @Test
    void testKotlinJvmAllTypesHaveCorrectConstValSyntax() {
        writeKotlinSettings()
        writeKotlinBuildGradle()
        testProjectDir.newFile('.env').text = """\
            API_KEY=super-secret
            IS_PROD=false
            TIMEOUT=5000
            PI=3.1415
            PLATFORMS=["android", "desktop"]
            LANGUAGES=en,fr,es
        """.stripIndent()

        def result = runKotlinTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)

        def content = generatedKtFileContent()
        assertTrue(content.contains('const val API_KEY: String = "super-secret"'))
        assertTrue(content.contains('const val IS_PROD: Boolean = false'))
        assertTrue(content.contains('const val TIMEOUT: Long = 5000L'))
        assertTrue(content.contains('const val PI: Double = 3.1415'))
        // Arrays cannot be const val
        assertTrue(content.contains('val PLATFORMS'))
        assertTrue(content.contains('arrayOf'))
        assertTrue(content.contains('"android"'))
        assertTrue(content.contains('"desktop"'))
        assertTrue(content.contains('val LANGUAGES'))
        assertTrue(content.contains('"en"'))
        assertTrue(content.contains('"fr"'))
        assertTrue(content.contains('"es"'))
        // Arrays must NOT be declared const
        assertFalse('Array fields must not be const val', (content =~ /const val PLATFORMS/).find())
        assertFalse('Array fields must not be const val', (content =~ /const val LANGUAGES/).find())
    }

    @Test
    void testKotlinJvmDollarSignInStringEncodedCorrectly() {
        writeKotlinSettings()
        writeKotlinBuildGradle()
        testProjectDir.newFile('.env').text = 'MSG=hello$world\nPRICE=costs $5'

        runKotlinTask()

        def content = generatedKtFileContent()
        assertTrue('MSG field should be present', content.contains('const val MSG'))
        assertTrue('PRICE field should be present', content.contains('const val PRICE'))
        // Must not contain unquoted $ template references that would cause Kotlin compile errors
        assertFalse('Must not contain ${world} template reference', content.contains('${world}'))
        assertFalse('Must not contain ${5} template reference', content.contains('${5}'))

        // Verify it actually compiles
        def srcDir = testProjectDir.newFolder('src', 'main', 'kotlin', 'example')
        new File(srcDir, 'App.kt').text = """\
            package example
            import dotenv.test_project.DotEnv
            fun getMsg(): String = DotEnv.MSG
        """.stripIndent()

        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('compileKotlin', '--stacktrace')
            .withPluginClasspath()
            .build()
        assertEquals(TaskOutcome.SUCCESS, result.task(':compileKotlin').outcome)
    }

    // ── Section B: Kotlin/JVM task wiring ────────────────────────────────────

    @Test
    void testKotlinJvmGenerateDotEnvRunsBeforeCompileKotlin() {
        writeKotlinSettings()
        writeKotlinBuildGradle()
        testProjectDir.newFile('.env').text = 'KEY=value'

        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('compileKotlin', '--dry-run')
            .withPluginClasspath()
            .build()

        def output = result.output
        def generateIndex = output.indexOf(':generateDotEnv')
        def compileIndex  = output.indexOf(':compileKotlin')
        assertTrue('generateDotEnv must appear before compileKotlin in task graph',
            generateIndex >= 0 && compileIndex >= 0 && generateIndex < compileIndex)
    }

    // ── Section C: Kotlin/JVM obfuscation structure ──────────────────────────

    @Test
    void testKotlinJvmObfuscatedFieldDelegatesCorrectly() {
        writeKotlinSettings()
        writeKotlinBuildGradle("dotenv { obfuscate = ['API_KEY'] }")
        testProjectDir.newFile('.env').text = 'API_KEY=secret\nPLAIN=visible'

        runKotlinTask()

        def content = generatedKtFileContent()
        assertFalse('Plain secret must not appear in DotEnv.kt', content.contains('secret'))
        assertTrue('Obfuscated field must delegate to helper via .get()',
            (content =~ /_[a-f0-9]+\.get\(\)/).find())
        assertTrue('Non-obfuscated field must remain const val',
            content.contains('const val PLAIN: String = "visible"'))
    }

    @Test
    void testKotlinJvmObfuscatedHelperIsKotlinInternalObject() {
        writeKotlinSettings()
        writeKotlinBuildGradle("dotenv { obfuscate = ['KEY'] }")
        testProjectDir.newFile('.env').text = 'KEY=test'

        runKotlinTask()

        def genDir = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project')
        def helperFile = genDir.listFiles().find { it.name =~ /^_[a-f0-9]{6}\.kt$/ }

        assertNotNull('Kotlin helper file (.kt) should exist', helperFile)
        def content = helperFile.text
        assertTrue('Kotlin helper must use internal object', content.contains('internal object'))
        assertTrue('Kotlin helper must have a get(): String function',
            content.contains('fun get()') || content.contains('fun `get`()'))
    }

    @Test
    void testKotlinJvmObfuscatedHelperUsesKotlinVmPattern() {
        writeKotlinSettings()
        writeKotlinBuildGradle("dotenv { obfuscate = ['KEY'] }")
        testProjectDir.newFile('.env').text = 'KEY=abcdefghij'

        runKotlinTask()

        def genDir = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project')
        def helperFile = genDir.listFiles().find { it.name =~ /^_[a-f0-9]{6}\.kt$/ }

        assertNotNull('Kotlin helper file should exist', helperFile)
        def content = helperFile.text
        assertTrue('Helper should use intArrayOf for program array', content.contains('intArrayOf'))
        assertTrue('Helper should have program counter (_pc)', content.contains('_pc'))
        assertTrue('Helper should have rolling state (_st)', content.contains('_st'))
        assertTrue('Helper should use a while loop (VM interpreter)', content.contains('while'))
        assertTrue('Helper should use when for opcode dispatch (Kotlin switch)', content.contains('when'))
        assertTrue('Helper should use rolling state update (_st * 31)', content.contains('_st * 31'))
        // JS/Native safety: no JVM reflection
        assertFalse('Helper must NOT use .class.getName() (not safe for JS/Native)',
            content.contains('.class.getName()'))
        assertFalse('Helper must NOT use .hashCode()',
            content.contains('.hashCode()'))
        // Plain secret must not appear
        assertFalse('Plain secret must not appear in helper', content.contains('abcdefghij'))
    }

    @Test
    void testKotlinJvmObfuscatedHelperUsesMbaDispatch() {
        writeKotlinSettings()
        writeKotlinBuildGradle("dotenv { obfuscate = ['KEY'] }")
        testProjectDir.newFile('.env').text = 'KEY=test'

        runKotlinTask()

        def genDir = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project')
        def helperFile = genDir.listFiles().find { it.name =~ /^_[a-f0-9]{6}\.kt$/ }

        assertNotNull('Kotlin helper file should exist', helperFile)
        def content = helperFile.text
        // MBA-scrambled dispatch uses xor + and compound expression
        assertTrue('Helper should use MBA dispatch (xor + and compound)',
            (content =~ /xor _C\) \+ 2L \* \(/).find())
        // No plain opcode comparison
        assertFalse('Helper must not use plain opcode equality (_raw == NN)',
            (content =~ /_raw\s*==\s*\d+/).find())
    }

    // ── Section D: Kotlin/JVM obfuscation runtime correctness ────────────────

    @Test
    void testKotlinJvmObfuscationRuntimeCorrectness() {
        writeKotlinSettings()
        writeKotlinBuildGradle("""\
            dotenv { obfuscate = ['SECRET', 'ANOTHER'] }
            task verifyObfuscation(type: JavaExec, dependsOn: compileKotlin) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = 'example.VerifyKt'
            }
        """)
        testProjectDir.newFile('.env').text = "SECRET=hello-world-123\nANOTHER=foo\$bar!baz"

        def srcDir = testProjectDir.newFolder('src', 'main', 'kotlin', 'example')
        new File(srcDir, 'Verify.kt').text = """\
            package example
            fun main() {
                println(dotenv.test_project.DotEnv.SECRET)
                println(dotenv.test_project.DotEnv.ANOTHER)
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

    // ── Section E: KMP file generation ───────────────────────────────────────

    private void writeKmpSettings(String projectName = 'test-project') {
        testProjectDir.newFile('settings.gradle').text = """\
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = '${projectName}'
        """.stripIndent()
    }

    private void writeKmpBuildGradle(String extra = '') {
        testProjectDir.newFile('build.gradle').text = """\
            plugins {
                id 'org.jetbrains.kotlin.multiplatform' version '1.9.25'
                id 'io.github.fcat97.dotenv'
            }
            repositories { mavenCentral() }
            kotlin {
                jvm()
            }
            ${extra}
        """.stripIndent()
    }

    @Test
    void testKmpGeneratesDotEnvKtInCommonMainPath() {
        writeKmpSettings()
        writeKmpBuildGradle()
        testProjectDir.newFile('.env').text = 'KEY=value'

        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('generateDotEnv', '--stacktrace')
            .withPluginClasspath()
            .build()

        def ktFile = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project/DotEnv.kt')
        assertTrue('DotEnv.kt should be generated for KMP project', ktFile.exists())
        def content = ktFile.text
        assertTrue('Generated file should declare an object DotEnv', content.contains('object DotEnv'))
        assertTrue('Generated file should have correct package', content.contains('package dotenv.test_project'))
    }

    @Test
    void testKmpAllTypesGeneratedCorrectlyInDotEnvKt() {
        writeKmpSettings()
        writeKmpBuildGradle()
        testProjectDir.newFile('.env').text = """\
            API_KEY=kmp-secret
            IS_PROD=true
            TIMEOUT=9000
            PI=2.718
            PLATFORMS=["android", "ios", "desktop"]
            ENVS=dev,prod
        """.stripIndent()

        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('generateDotEnv', '--stacktrace')
            .withPluginClasspath()
            .build()

        def content = new File(testProjectDir.root,
            'build/generated/dotenv/src/main/java/dotenv/test_project/DotEnv.kt').text
        assertTrue(content.contains('const val API_KEY: String = "kmp-secret"'))
        assertTrue(content.contains('const val IS_PROD: Boolean = true'))
        assertTrue(content.contains('const val TIMEOUT: Long = 9000L'))
        assertTrue(content.contains('const val PI: Double = 2.718'))
        assertTrue(content.contains('val PLATFORMS'))
        assertTrue(content.contains('"android"'))
        assertTrue(content.contains('"ios"'))
        assertTrue(content.contains('"desktop"'))
        assertTrue(content.contains('val ENVS'))
        assertTrue(content.contains('"dev"'))
        assertTrue(content.contains('"prod"'))
    }

    // ── Section F: Kotlin/JVM obfuscation randomization ──────────────────────

    @Test
    void testKotlinJvmObfuscatedOutputDiffersAcrossBuilds() {
        writeKotlinSettings()
        writeKotlinBuildGradle("dotenv { obfuscate = ['SECRET'] }")
        testProjectDir.newFile('.env').text = 'SECRET=my-secret-value'

        runKotlinTask()
        def content1 = generatedKtFileContent()
        def call1 = (content1 =~ /_[a-f0-9]+\.get\(\)/)[0]

        new File(testProjectDir.root, 'build').deleteDir()
        runKotlinTask()
        def content2 = generatedKtFileContent()
        def call2 = (content2 =~ /_[a-f0-9]+\.get\(\)/)[0]

        assertNotNull('First build should have an obfuscated call', call1)
        assertNotNull('Second build should have an obfuscated call', call2)
        assertNotEquals('Kotlin helper class name must differ between builds', call1, call2)
    }
}
