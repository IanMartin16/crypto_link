import ApiKeyBar from "@/components/ApiKeyBar";
import PricesPanel from "@/components/PricesPanel";
import TrendsPanel from "@/components/TrendsPanel";

export default function DashboardPage() {
  return (
    <main style={{ padding: 24, fontFamily: "system-ui" }}>
      <h1 style={{ margin: 0 }}>CryptoLink V2 Dashboard</h1>
      <p style={{ marginTop: 8, opacity: 0.8 }}>
        Conectando CryptoLink + Social_link.
      </p>

      <ApiKeyBar />
      <PricesPanel />
      <TrendsPanel />
    </main>
  );
}
