import { LayoutGrid, Braces, Zap } from "lucide-react";

const features = [
  {
    icon: LayoutGrid,
    title: "Module-Level Control",
    description:
      "Manage different .env files per Gradle module with scoped access and individual validation rules.",
  },
  {
    icon: Braces,
    title: "Typed Constants",
    description:
      "No manual parsing of strings. Access your variables as static final primitives with full IDE autocomplete.",
  },
  {
    icon: Zap,
    title: "Zero Runtime Dependencies",
    description:
      "Logic is executed at build-time. Your final APK or JAR remains lean and free of unnecessary overhead.",
  },
];

export default function FeaturesSection() {
  return (
    <section className="py-24 px-6 bg-surface-container-low">
      <div className="max-w-7xl mx-auto">
        <h2 className="text-3xl font-headline font-bold mb-16 text-center text-on-surface">
          Why use dotEnv over local.properties?
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {features.map(({ icon: Icon, title, description }) => (
            <div
              key={title}
              className="glass-panel p-8 rounded-xl ghost-border space-y-4 hover:-translate-y-1 transition-transform duration-300"
            >
              <div className="w-12 h-12 bg-primary/10 rounded-lg flex items-center justify-center">
                <Icon size={20} className="text-primary" />
              </div>
              <h3 className="text-xl font-headline font-bold text-on-surface">
                {title}
              </h3>
              <p className="text-on-surface-variant text-sm leading-relaxed">
                {description}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
