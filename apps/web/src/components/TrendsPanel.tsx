import { fetchTrends } from "@/lib/socialLink";

export default async function TrendsPanel() {
  try {
    const trends = await fetchTrends(["BTC", "ETH"]);

    return (
      <section style={{ marginTop: 16, padding: 16, border: "1px solid #ddd", borderRadius: 12 }}>
        <h2 style={{ margin: 0 }}>Trends (Social_link)</h2>
        <p style={{ marginTop: 8, opacity: 0.8 }}>
          Última actualización: <code>{trends.ts}</code>
        </p>

        <ul>
          {trends.data.map((t) => (
            <li key={t.symbol}>
              <b>{t.symbol}</b> → {t.trend} (score {t.score}) — {t.reason}
            </li>
          ))}
        </ul>
      </section>
    );
  } catch (e: any) {
    return (
      <section style={{ marginTop: 16, padding: 16, border: "1px solid #f00", borderRadius: 12 }}>
        <h2 style={{ margin: 0 }}>Trends (Social_link)</h2>
        <p style={{ marginTop: 8 }}>
          Error conectando a Social_link: <b>{e?.message ?? "unknown"}</b>
        </p>
        <p style={{ marginTop: 8, opacity: 0.8 }}>
          Revisa que Social_link esté corriendo en <code>http://localhost:8000</code>
        </p>
      </section>
    );
  }
}
