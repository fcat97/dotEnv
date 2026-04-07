import { Copy } from "lucide-react";

export default function CodePreviewSection() {
  return (
    <section className="py-24 px-6 overflow-hidden">
      <div className="max-w-7xl mx-auto">
        <div className="flex flex-col lg:flex-row gap-8 items-stretch">

          {/* Left: .env source */}
          <div className="flex-1 flex flex-col">
            <div className="bg-surface-container-highest px-6 py-3 flex items-center justify-between rounded-t-xl border-l-2 border-primary">
              <span className="font-mono text-xs text-on-surface-variant">.env</span>
              <Copy size={16} className="text-on-surface-variant cursor-pointer hover:text-primary transition-colors" />
            </div>
            <div className="bg-surface-container-high p-8 flex-1 rounded-b-xl">
              <pre className="font-mono text-sm leading-relaxed text-secondary-dim whitespace-pre-wrap">
                <span className="opacity-50">{`# API Configuration`}</span>
                {`
API_URL=https://api.v2.dotenv.gradle
AUTH_TOKEN=sk_live_51M0...
MAX_RETRIES=3
DEBUG_ENABLED=true`}
              </pre>
            </div>
          </div>

          {/* Right: Generated Java */}
          <div className="flex-[1.5] flex flex-col">
            <div className="bg-surface-container-highest px-6 py-3 flex items-center justify-between rounded-t-xl border-l-2 border-secondary">
              <span className="font-mono text-xs text-on-surface-variant">
                DotEnv.java (Generated)
              </span>
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-secondary shadow-[0_0_8px_#69f6b8]" />
                <span className="text-[10px] uppercase tracking-widest font-bold text-secondary font-mono">
                  Build Success
                </span>
              </div>
            </div>
            <div className="bg-surface-container-high p-8 flex-1 rounded-b-xl overflow-x-auto">
              <pre className="font-mono text-sm leading-relaxed">
                <span className="text-primary">package</span>
                <span className="text-on-surface-variant"> com.example.config;</span>
                {"\n\n"}
                <span className="text-primary">public final class</span>
                {" "}
                <span className="text-secondary">DotEnv</span>
                <span className="text-on-surface-variant">{" {"}</span>
                {"\n"}
                {"    "}
                <span className="text-primary">public static final</span>
                <span className="text-on-surface-variant"> String API_URL = </span>
                <span className="text-tertiary">"https://api.v2.dotenv.gradle"</span>
                <span className="text-on-surface-variant">;</span>
                {"\n"}
                {"    "}
                <span className="text-primary">public static final</span>
                <span className="text-on-surface-variant"> String AUTH_TOKEN = </span>
                <span className="text-tertiary">"sk_live_51M0..."</span>
                <span className="text-on-surface-variant">;</span>
                {"\n"}
                {"    "}
                <span className="text-primary">public static final int</span>
                <span className="text-on-surface-variant"> MAX_RETRIES = </span>
                <span className="text-tertiary">3</span>
                <span className="text-on-surface-variant">;</span>
                {"\n"}
                {"    "}
                <span className="text-primary">public static final boolean</span>
                <span className="text-on-surface-variant"> DEBUG_ENABLED = </span>
                <span className="text-tertiary">true</span>
                <span className="text-on-surface-variant">;</span>
                {"\n"}
                <span className="text-on-surface-variant">{"}"}</span>
              </pre>
            </div>
          </div>

        </div>
      </div>
    </section>
  );
}
