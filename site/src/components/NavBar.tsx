"use client";

import Link from "next/link";
import { Star, Heart } from "lucide-react";

export default function NavBar() {
  return (
    <header className="fixed top-0 w-full z-50 bg-background/80 backdrop-blur-md flex justify-between items-center px-8 py-4">
      <Link href="/" className="text-xl font-bold text-primary font-headline tracking-tight hover:opacity-80 transition-opacity">
        dotEnv Gradle
      </Link>

      <nav className="hidden md:flex items-center gap-8">
        <Link
          href="/docs"
          className="text-primary border-b-2 border-primary pb-1 font-headline tracking-tight text-sm"
        >
          Documentation
        </Link>

        <div className="flex items-center gap-4">
          <a
            href="https://github.com/fcat97/dotEnv"
            target="_blank"
            rel="noopener noreferrer"
            className="hover:bg-surface-container transition-all duration-300 p-2 rounded-lg text-on-surface-variant hover:text-primary"
          >
            <Star size={20} />
          </a>
          <a
            href="https://buymeacoffee.com/szaman97"
            target="_blank"
            rel="noopener noreferrer"
            className="bg-tertiary-fixed text-on-tertiary-fixed px-4 py-2 font-bold rounded-md hover:scale-95 transition-transform duration-150 font-headline text-sm flex items-center gap-2"
          >
            <Heart size={14} fill="currentColor" />
            Buy Me a Coffee
          </a>
        </div>
      </nav>
    </header>
  );
}
