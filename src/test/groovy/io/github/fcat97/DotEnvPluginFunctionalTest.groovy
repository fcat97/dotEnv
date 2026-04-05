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
    void testMissingEnvFileSkipsGenerationGracefully() {
        writeSettings()
        writeBuildGradle()
        // intentionally no .env file

        def result = runTask()

        assertEquals(TaskOutcome.SUCCESS, result.task(':generateDotEnv').outcome)
        assertTrue('Should log a skip message', result.output.contains('.env file not found'))
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
}
