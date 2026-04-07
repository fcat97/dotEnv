import { Heart } from "lucide-react";

export default function CoffeeSection() {
  return (
    <section className="py-24 px-6">
      <div className="max-w-4xl mx-auto glass-panel p-12 rounded-2xl text-center space-y-8 border-l-4 border-tertiary-fixed">

        <div className="flex justify-center">
          <div className="w-16 h-16 bg-tertiary-fixed/20 rounded-full flex items-center justify-center">
            <Heart size={32} className="text-tertiary-fixed" fill="currentColor" />
          </div>
        </div>

        <div className="space-y-4">
          <h2 className="text-3xl font-headline font-bold text-on-surface">
            Support the Developer
          </h2>
          <p className="text-on-surface-variant max-w-xl mx-auto">
            This plugin is maintained for free by fcat97. If dotEnv saves you
            time and secures your projects, consider buying a coffee to keep the
            build lights on.
          </p>
        </div>

        <a
          href="https://buymeacoffee.com/szaman97"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-block bg-tertiary-fixed text-on-tertiary-fixed px-10 py-5 font-bold text-xl rounded-md shadow-2xl shadow-tertiary-fixed/20 hover:scale-105 transition-all font-headline"
        >
          Fuel the Project
        </a>

      </div>
    </section>
  );
}
