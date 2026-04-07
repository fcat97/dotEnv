import { Copy, Info, ShieldCheck, TriangleAlert, RefreshCw, ListChecks, Unplug } from "lucide-react";

export default function DocsPage() {
  return (
    <>
      {/* ── Section 1: Installation ───────────────────────────────── */}
      <section className="mb-20" id="getting-started">
        <header className="mb-8">
          <div className="flex items-center gap-2 text-secondary mb-2">
            <span className="w-2 h-2 rounded-full bg-secondary shadow-[0_0_8px_#69f6b8]" />
            <span className="text-xs font-mono tracking-widest uppercase">Quick Start</span>
          </div>
          <h1 className="text-5xl font-headline font-bold text-on-surface mb-4 tracking-tight">
            Installation
          </h1>
          <p className="text-lg text-on-surface-variant leading-relaxed">
            Securely inject environment variables into your build process with precision engineering.
          </p>
        </header>

        <div className="space-y-8">
          {/* Step 1 — libs.versions.toml */}
          <div>
            <h2 className="text-2xl font-headline font-semibold text-primary mb-2">
              1. Add to Version Catalog
            </h2>
            <p className="text-on-surface-variant mb-4">
              Declare the plugin in your{" "}
              <code className="font-mono text-secondary">gradle/libs.versions.toml</code>:
            </p>
            <div className="relative">
              <div className="absolute -inset-1 bg-gradient-to-r from-primary/20 to-transparent rounded blur opacity-25" />
              <div className="relative bg-surface-container-highest p-6 rounded-lg border-l-2 border-primary">
                <div className="flex justify-between items-center mb-4">
                  <span className="text-xs text-outline font-mono">gradle/libs.versions.toml</span>
                  <Copy size={16} className="text-outline cursor-pointer hover:text-primary transition-colors" />
                </div>
                <pre className="font-mono text-sm leading-relaxed overflow-x-auto">
                  <span className="text-outline">{`[versions]`}</span>
                  {"\n"}
                  <span className="text-primary">{"dotenv"}</span>
                  {" = "}
                  <span className="text-secondary">{`"0.10.0"`}</span>
                  {"\n\n"}
                  <span className="text-outline">{`[plugins]`}</span>
                  {"\n"}
                  <span className="text-primary">{"dotEnv"}</span>
                  {" = \{ id = "}
                  <span className="text-secondary">{`"io.github.fcat97.dotenv"`}</span>
                  {", version.ref = "}
                  <span className="text-secondary">{`"dotenv"`}</span>
                  {" \}"}
                </pre>
              </div>
            </div>
          </div>

          {/* Step 2 — root build.gradle */}
          <div>
            <h2 className="text-2xl font-headline font-semibold text-primary mb-2">
              2. Declare in Root{" "}
              <code className="font-mono text-secondary text-xl">build.gradle</code>
            </h2>
            <p className="text-on-surface-variant mb-4">
              Add the plugin to your root-level{" "}
              <code className="font-mono text-secondary">build.gradle</code> (apply{" "}
              <code className="font-mono">false</code> — do not apply at root level):
            </p>
            <div className="relative">
              <div className="absolute -inset-1 bg-gradient-to-r from-primary/20 to-transparent rounded blur opacity-25" />
              <div className="relative bg-surface-container-highest p-6 rounded-lg border-l-2 border-primary">
                <div className="flex justify-between items-center mb-4">
                  <span className="text-xs text-outline font-mono">build.gradle (root)</span>
                  <Copy size={16} className="text-outline cursor-pointer hover:text-primary transition-colors" />
                </div>
                <pre className="font-mono text-sm leading-relaxed">
                  <span className="text-tertiary">{"plugins"}</span>
                  {" {\n"}
                  {"    "}
                  <span className="text-primary">{"alias"}</span>
                  {"(libs.plugins.dotEnv) apply "}
                  <span className="text-secondary">{"false"}</span>
                  {"\n}"}
                </pre>
              </div>
            </div>
          </div>

          {/* Step 3 — module build.gradle */}
          <div>
            <h2 className="text-2xl font-headline font-semibold text-primary mb-2">
              3. Apply in Your Module
            </h2>
            <p className="text-on-surface-variant mb-4">
              Apply the plugin in the module where your{" "}
              <code className="font-mono text-secondary">.env</code> file lives:
            </p>
            <div className="relative">
              <div className="absolute -inset-1 bg-gradient-to-r from-primary/20 to-transparent rounded blur opacity-25" />
              <div className="relative bg-surface-container-highest p-6 rounded-lg border-l-2 border-primary">
                <div className="flex justify-between items-center mb-4">
                  <span className="text-xs text-outline font-mono">app/build.gradle</span>
                  <Copy size={16} className="text-outline cursor-pointer hover:text-primary transition-colors" />
                </div>
                <pre className="font-mono text-sm leading-relaxed">
                  <span className="text-tertiary">{"plugins"}</span>
                  {" {\n"}
                  {"    "}
                  <span className="text-primary">{"alias"}</span>
                  {"(libs.plugins.dotEnv)\n}"}
                </pre>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── Section 2: Configuration ──────────────────────────────── */}
      <section className="mb-20" id="configuration">
        <header className="mb-8">
          <h2 className="text-4xl font-headline font-bold text-on-surface mb-4 tracking-tight">
            Configuration
          </h2>
          <p className="text-on-surface-variant">
            Fine-tune the plugin behavior to match your project architecture.
          </p>
        </header>

        <div className="glass-panel p-8 rounded-xl ghost-border">
          <h3 className="text-xl font-headline font-semibold text-primary mb-4">
            Available Options
          </h3>
          <p className="text-on-surface-variant mb-6">
            Configure the plugin in your module&apos;s{" "}
            <code className="font-mono bg-surface-container px-1 rounded">build.gradle</code>.
            All properties are optional — the plugin works out of the box with no configuration.
          </p>

          <div className="bg-surface-container-highest p-6 rounded-lg border-l-2 border-primary mb-6">
            <pre className="font-mono text-sm leading-relaxed overflow-x-auto">
              <span className="text-outline">{"// build.gradle (module)"}</span>
              {"\n"}
              <span className="text-tertiary">{"dotenv"}</span>
              {" {\n"}
              {"    "}
              <span className="text-outline">{"// dynamically choose .env file based on build type"}</span>
              {"\n    "}
              <span className="text-on-surface">{"def isReleaseBuild = gradle.startParameter.taskNames.any \{"}</span>
              {"\n        "}
              <span className="text-on-surface">{"it.contains("}
              </span>
              <span className="text-secondary">{"\"bundleRelease\""}</span>
              <span className="text-on-surface">{")\n        || it.contains("}</span>
              <span className="text-secondary">{"\"assembleRelease\""}</span>
              <span className="text-on-surface">{")\n    \}"}</span>
              {"\n\n    "}
              <span className="text-primary">{"namespace"}</span>
              {" = "}
              <span className="text-secondary">{`"com.example.myproject"`}</span>
              {"\n    "}
              <span className="text-primary">{"envFilepath"}</span>
              {" = isReleaseBuild ? "}
              <span className="text-secondary">{`".env_prod"`}</span>
              {" : "}
              <span className="text-secondary">{`".env"`}</span>
              {"\n    "}
              <span className="text-primary">{"obfuscate"}</span>
              {" = ["}
              <span className="text-secondary">{`"SECRET_KEY"`}</span>
              {", "}
              <span className="text-secondary">{`"API_KEY"`}</span>
              {"]\n}"}
            </pre>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
            {[
              { prop: "namespace", desc: "Fully-qualified class name for the generated file. Defaults to dotenv.<module-name>." },
              { prop: "envFilepath", desc: "Path to the .env file, relative to the module directory. Defaults to \".env\"." },
              { prop: "obfuscate", desc: "List of String field names to obfuscate. Non-String fields will fail the build." },
            ].map(({ prop, desc }) => (
              <div key={prop} className="p-4 bg-surface-container-low rounded-lg">
                <code className="font-mono text-primary text-sm">{prop}</code>
                <p className="text-xs text-on-surface-variant mt-2 leading-relaxed">{desc}</p>
              </div>
            ))}
          </div>

          <div className="flex items-start gap-4 p-4 bg-primary/5 border-l-4 border-primary rounded">
            <Info size={18} className="text-primary mt-0.5 shrink-0" />
            <p className="text-sm text-on-surface-variant">
              The <code className="font-mono text-primary">envFilepath</code> property supports dynamic
              Gradle expressions, making it easy to swap between development and production secrets at build
              time.
            </p>
          </div>
        </div>
      </section>

      {/* ── Section 3: Supported Formats ─────────────────────────── */}
      <section className="mb-20" id="formats">
        <header className="mb-8">
          <h2 className="text-4xl font-headline font-bold text-on-surface mb-4 tracking-tight">
            Supported Formats
          </h2>
          <p className="text-on-surface-variant">
            Automatic type inference — no annotations required.
          </p>
        </header>

        <div className="overflow-hidden rounded-xl ghost-border">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-surface-container-low">
                {["Format", "Generated Java Type", "Example .env entry"].map((h) => (
                  <th
                    key={h}
                    className="p-4 font-headline font-medium text-primary border-b border-outline-variant/20"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="text-sm">
              {[
                { format: "Simple string",           type: "public static final String",   example: "API_KEY=abc123" },
                { format: "Double-quoted string",    type: "public static final String",   example: 'URL="https://foo.com"' },
                { format: "Boolean",                 type: "public static final boolean",  example: "IS_PROD=true" },
                { format: "Long integer",            type: "public static final long",     example: "TIMEOUT=1234" },
                { format: "Double / float",          type: "public static final double",   example: "PI=3.1415" },
                { format: "JSON-style list",         type: "public static final String[]", example: 'PLATFORMS=["android","desktop"]' },
                { format: "Comma-separated list",    type: "public static final String[]", example: "LANGUAGES=en,fr,es" },
              ].map(({ format, type, example }) => (
                <tr key={format} className="hover:bg-surface-container-high transition-colors">
                  <td className="p-4 border-b border-outline-variant/10 text-on-surface-variant">{format}</td>
                  <td className="p-4 border-b border-outline-variant/10 font-mono text-secondary text-xs">{type}</td>
                  <td className="p-4 border-b border-outline-variant/10 font-mono text-on-surface">{example}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex items-start gap-4 p-4 mt-4 bg-secondary/5 border-l-4 border-secondary rounded">
          <Info size={18} className="text-secondary mt-0.5 shrink-0" />
          <p className="text-sm text-on-surface-variant">
            Double-quoted strings have the quotes automatically stripped. Only{" "}
            <code className="font-mono text-primary">String</code> fields may be added to the{" "}
            <code className="font-mono text-primary">obfuscate</code> list — other types will fail the build.
          </p>
        </div>
      </section>

      {/* ── Section 4: Obfuscation / Security ────────────────────── */}
      <section className="mb-20" id="security">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
          <div>
            <h2 className="text-4xl font-headline font-bold text-on-surface mb-6 tracking-tight">
              Obfuscation
            </h2>
            <p className="text-on-surface-variant mb-4 leading-relaxed">
              Prevent secret leaking through static analysis. Obfuscated fields are decoded transparently
              — consumer code is unchanged:
            </p>
            <div className="bg-surface-container-highest px-5 py-4 rounded-lg font-mono text-sm mb-6 border-l-2 border-secondary">
              <span className="text-outline">{"// same as any other field"}</span>
              {"\n"}
              <span className="text-on-surface">{"String key = "}</span>
              <span className="text-primary">{"DotEnv"}</span>
              <span className="text-on-surface">{"."}</span>
              <span className="text-secondary">{"SECRET_KEY"}</span>
              <span className="text-on-surface">{";"}</span>
            </div>
            <p className="text-on-surface-variant mb-6 text-sm leading-relaxed">
              Under the hood, a 5-layer build-time VM is generated. Each build produces a unique helper
              class — the secret never appears as a string literal anywhere in the compiled output.
            </p>
            <ul className="space-y-4">
              {[
                "Custom FNV-like hash key derivation (random salt + prime per build)",
                "Non-linear JUMP bytecode execution — linear scans produce garbage",
                "Rolling state decryption — byte N depends on all prior bytes",
                "MBA-scrambled switch dispatch — decompilers see opaque math",
                "Hardened opaque predicates + 6 dead code injection patterns",
              ].map((item) => (
                <li key={item} className="flex items-start gap-3 text-on-surface-variant text-sm">
                  <ShieldCheck size={16} className="text-secondary shrink-0 mt-0.5" />
                  {item}
                </li>
              ))}
            </ul>
          </div>

          <div className="bg-surface-container-highest p-8 rounded-xl border border-primary/20 shadow-2xl">
            <p className="text-sm font-mono text-primary mb-4">{"// Enable in build.gradle"}</p>
            <pre className="font-mono text-sm leading-relaxed">
              <span className="text-tertiary">{"dotenv"}</span>
              {" {\n"}
              {"    "}
              <span className="text-primary">{"obfuscate"}</span>
              {" = ["}
              <span className="text-secondary">{`"SECRET_KEY"`}</span>
              {", "}
              <span className="text-secondary">{`"API_KEY"`}</span>
              {"]\n}"}
            </pre>
            <div className="mt-8 pt-8 border-t border-outline-variant/20">
              <p className="text-xs text-outline mb-3">
                What gets compiled — a unique VM helper class (name changes every build):
              </p>
              <code className="text-xs font-mono text-secondary/80 break-all leading-relaxed">
                {"public static final String SECRET_KEY = _a3f2b1c.get();"}
              </code>
              <p className="text-xs text-outline mt-3">
                The real value is only reconstructed in memory at runtime by the VM.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* ── Section 5: Troubleshooting ───────────────────────────── */}
      <section className="mb-20" id="examples">
        <header className="mb-8">
          <h2 className="text-4xl font-headline font-bold text-on-surface mb-4 tracking-tight">
            Troubleshooting
          </h2>
          <p className="text-on-surface-variant">Common hurdles and how to resolve them quickly.</p>
        </header>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {[
            {
              icon: TriangleAlert,
              color: "text-error",
              title: "File Not Found",
              body: (
                <>
                  The <code className="font-mono">.env</code> file must be placed in the{" "}
                  <strong className="text-on-surface">same module directory</strong> where the plugin is
                  applied (e.g. <code className="font-mono text-secondary">app/.env</code>). Use{" "}
                  <code className="font-mono text-primary">envFilepath</code> to specify a custom filename
                  within that module.
                </>
              ),
            },
            {
              icon: RefreshCw,
              color: "text-tertiary",
              title: "Changes Not Reflecting",
              body: (
                <>
                  Gradle may cache build outputs. Run{" "}
                  <code className="font-mono text-primary">./gradlew clean generateDotEnv</code> to force
                  re-generation of the configuration class.
                </>
              ),
            },
            {
              icon: ListChecks,
              color: "text-secondary",
              title: "Type Mismatch",
              body: (
                <>
                  If a numeric value is parsed as a String, ensure it doesn&apos;t contain hidden characters
                  or trailing spaces in your <code className="font-mono">.env</code> file.
                </>
              ),
            },
            {
              icon: Unplug,
              color: "text-primary",
              title: "Namespace Collision",
              body: (
                <>
                  Avoid naming your <code className="font-mono">namespace</code> the same as existing project
                  classes to prevent compilation errors during build tasks.
                </>
              ),
            },
          ].map(({ icon: Icon, color, title, body }) => (
            <div
              key={title}
              className="p-6 rounded-lg bg-surface-container-low hover:bg-surface-container transition-colors"
            >
              <div className="flex items-center gap-3 mb-3">
                <Icon size={20} className={color} />
                <h4 className="font-headline font-semibold text-on-surface">{title}</h4>
              </div>
              <p className="text-sm text-on-surface-variant leading-relaxed">{body}</p>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
