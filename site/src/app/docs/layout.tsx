import NavBar from "@/components/NavBar";
import DocsSidebar from "@/components/DocsSidebar";
import Footer from "@/components/Footer";

export default function DocsLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <NavBar />
      <div className="flex min-h-screen pt-16">
        <DocsSidebar />
        <div className="flex-1 md:ml-64 flex flex-col min-h-screen">
          <main className="flex-1 px-8 md:px-16 py-12 max-w-5xl w-full">
            {children}
          </main>
          <Footer />
        </div>
      </div>
    </>
  );
}
