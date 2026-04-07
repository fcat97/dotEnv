import { ShieldCheck, CheckCircle2, Shield } from "lucide-react";

export default function SecuritySection() {
  return (
    <section className="py-24 px-6 bg-surface-container relative overflow-hidden">
      <div className="max-w-7xl mx-auto grid grid-cols-1 md:grid-cols-2 gap-16 items-center">

        {/* Left: text */}
        <div className="space-y-6">
          <div className="inline-flex items-center gap-2 text-secondary font-headline font-bold text-sm tracking-widest uppercase bg-secondary/5 px-4 py-1 rounded-full border border-secondary/20">
            <ShieldCheck size={16} />
            Security First
          </div>

          <h2 className="text-4xl font-headline font-bold leading-tight text-on-surface">
            Build-Time Obfuscation
          </h2>

          <p className="text-on-surface-variant leading-relaxed text-lg">
            Protect your secrets by obfuscating string fields directly in your
            bytecode. dotEnv automatically applies multi-layer transformations
            to sensitive keys, making them unreadable to standard decompilers.
          </p>

          <div className="pt-4 flex flex-col gap-4">
            {[
              "No raw strings in your final binary",
              "Dynamic decryption keys generated per-build",
            ].map((point) => (
              <div key={point} className="flex items-start gap-3">
                <CheckCircle2
                  size={20}
                  className="text-secondary mt-0.5 shrink-0"
                />
                <span className="text-on-surface">{point}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Right: visual panel */}
        <div className="relative group">
          <div className="absolute -inset-1 bg-gradient-to-r from-primary to-secondary rounded-2xl blur opacity-25 group-hover:opacity-40 transition duration-1000 group-hover:duration-200" />
          <div className="relative bg-surface-container-highest p-4 rounded-xl ghost-border overflow-hidden">
            <div className="aspect-video bg-surface-container-low rounded-lg flex items-center justify-center">
              <Shield
                size={96}
                className="text-secondary opacity-80"
                fill="currentColor"
              />
            </div>
          </div>
        </div>

      </div>
    </section>
  );
}
