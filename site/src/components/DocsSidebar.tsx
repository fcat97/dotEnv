"use client";

import { useEffect, useState } from "react";
import { Rocket, Settings, ShieldCheck, FileText, Layers, Table2 } from "lucide-react";

const navItems = [
  { id: "getting-started", label: "Getting Started", icon: Rocket },
  { id: "configuration",   label: "Configuration",  icon: Settings },
  { id: "platforms",       label: "Platforms",       icon: Layers },
  { id: "formats",         label: "Supported Formats", icon: Table2 },
  { id: "security",        label: "Security",        icon: ShieldCheck },
  { id: "examples",        label: "Troubleshooting", icon: FileText },
];

export default function DocsSidebar() {
  const [active, setActive] = useState("getting-started");

  useEffect(() => {
    const sections = navItems.map(({ id }) => document.getElementById(id)).filter(Boolean) as HTMLElement[];

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) setActive(entry.target.id);
        });
      },
      { rootMargin: "-30% 0px -60% 0px" }
    );

    sections.forEach((s) => observer.observe(s));
    return () => observer.disconnect();
  }, []);

  return (
    <aside className="h-[calc(100vh-4rem)] w-64 fixed left-0 top-16 bg-background flex flex-col py-8 hidden md:flex">
      <div className="px-6 mb-6">
        <h3 className="text-lg font-bold text-primary font-headline">Documentation</h3>
        <p className="text-xs text-on-surface-variant font-mono mt-1">
          <a
            href="https://github.com/fcat97/dotEnv/releases/"
            target="_blank"
            rel="noopener noreferrer"
            className="hover:text-primary transition-colors"
          >
            v0.11.0
          </a>
        </p>
      </div>

      <nav className="flex flex-col">
        {navItems.map(({ id, label, icon: Icon }) => {
          const isActive = active === id;
          return (
            <a
              key={id}
              href={`#${id}`}
              onClick={() => setActive(id)}
              className={`flex items-center gap-3 py-2.5 pl-4 text-sm transition-all duration-200
                ${isActive
                  ? "text-primary font-semibold border-l-2 border-primary"
                  : "text-on-surface-variant hover:bg-surface-container-low hover:text-primary border-l-2 border-transparent"
                }`}
            >
              <Icon size={18} />
              {label}
            </a>
          );
        })}
      </nav>
    </aside>
  );
}
