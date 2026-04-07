import NavBar from "@/components/NavBar";
import HeroSection from "@/components/HeroSection";
import FeaturesSection from "@/components/FeaturesSection";
import CodePreviewSection from "@/components/CodePreviewSection";
import SecuritySection from "@/components/SecuritySection";
import CoffeeSection from "@/components/CoffeeSection";
import Footer from "@/components/Footer";

export default function Home() {
  return (
    <>
      <NavBar />
      <main className="pt-24">
        <HeroSection />
        <FeaturesSection />
        <CodePreviewSection />
        <SecuritySection />
        <CoffeeSection />
      </main>
      <Footer />
    </>
  );
}
