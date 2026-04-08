import Link from "next/link";
import { Star, Heart } from "lucide-react";

export default function HeroSection() {
  return (
    <section className="relative min-h-[819px] flex flex-col items-center justify-center text-center px-6 overflow-hidden">
      {/* Ambient background blobs */}
      <div className="absolute inset-0 -z-10 opacity-20 pointer-events-none">
        <div className="absolute top-1/4 left-1/4 w-96 h-96 bg-primary blur-[120px] rounded-full" />
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 bg-secondary blur-[120px] rounded-full" />
      </div>

      <div className="max-w-4xl space-y-8">
        <h1 className="text-5xl md:text-7xl font-headline font-bold tracking-tight text-on-surface">
          dotEnv Gradle Plugin
        </h1>

        <p className="text-xl md:text-2xl font-headline font-light text-primary tracking-wide">
          Precision-Engineered Environment Management.
        </p>

        <p className="text-on-surface-variant max-w-2xl mx-auto leading-relaxed">
          Generate typed constants from <code className="font-mono text-primary">.env</code> files for Android, Java, Kotlin/JVM, Spring Boot, and Kotlin Multiplatform projects.
          Secure, modular, and built for Gradle.
        </p>

        {/* Supported platform chips */}
        <div className="flex flex-wrap items-center justify-center gap-2 pt-2">
          {["Android", "Java", "Kotlin/JVM", "Spring Boot", "KMP"].map((p) => (
            <span
              key={p}
              className="px-3 py-1 text-xs font-mono rounded-full bg-primary/10 text-primary border border-primary/20"
            >
              {p}
            </span>
          ))}
        </div>

        <div className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-4">
          <Link
            href="/docs"
            className="w-full sm:w-auto bg-primary text-on-primary px-8 py-4 font-bold text-lg rounded-md hover:brightness-110 transition-all duration-300 font-headline"
          >
            Get Started
          </Link>
          <a
            href="https://buymeacoffee.com/szaman97"
            target="_blank"
            rel="noopener noreferrer"
            className="w-full sm:w-auto bg-tertiary-fixed text-on-tertiary-fixed px-8 py-4 font-bold text-lg rounded-md flex items-center justify-center gap-2 hover:brightness-110 transition-all font-headline"
          >
            <Heart size={20} fill="currentColor" />
            Buy Me a Coffee
          </a>
        </div>
      </div>

      {/* GitHub proof bar */}
      <div className="mt-24 pt-12 border-t border-outline-variant/20 w-full max-w-5xl flex flex-col md:flex-row items-center justify-between gap-8 opacity-70">
        <div className="text-sm font-label uppercase tracking-[0.2em] text-on-surface-variant">
          Trusted by modularized projects
        </div>
        <div className="flex items-center gap-6">
          <a
            href="https://github.com/fcat97/dotEnv"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-2 px-4 py-2 bg-surface-container-high rounded-full border border-outline-variant/10 hover:border-primary/30 transition-colors"
          >
            <Star size={16} className="text-primary" fill="currentColor" />
            <span className="font-mono text-sm">Stars on GitHub</span>
          </a>
        </div>
      </div>
    </section>
  );
}
