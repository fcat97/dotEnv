const links = [
  { label: "Built by fcat97", href: "https://fcat97.github.io/" },
  { label: "Twitter", href: "#" },
  { label: "GitHub", href: "https://github.com/fcat97/dotEnv" },
  { label: "LinkedIn", href: "https://www.linkedin.com/in/sz97/" },
];

export default function Footer() {
  const year = new Date().getFullYear();

  return (
    <footer className="w-full py-12 bg-background border-t border-on-surface-variant/10">
      <div className="max-w-7xl mx-auto px-8 flex flex-col md:flex-row justify-between items-center gap-8">

        <div className="flex flex-col items-center md:items-start gap-2">
          <div className="text-sm font-medium text-primary font-headline">
            dotEnv Gradle Plugin
          </div>
          <div className="text-xs text-on-surface-variant">
            © {year} dotEnv Gradle Plugin
          </div>
        </div>

        <div className="flex flex-wrap justify-center gap-8">
          {links.map(({ label, href }) => (
            <a
              key={label}
              href={href}
              target={href !== "#" ? "_blank" : undefined}
              rel={href !== "#" ? "noopener noreferrer" : undefined}
              className="text-xs text-on-surface-variant hover:text-secondary transition-colors"
            >
              {label}
            </a>
          ))}
        </div>

      </div>
    </footer>
  );
}
