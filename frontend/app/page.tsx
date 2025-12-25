export default function HomePage() {
  return (
    <main style={{ padding: "48px", maxWidth: 960, margin: "0 auto" }}>
      <h1>Docst</h1>
      <p>
        Phase 1 MVP scaffolding is online. Connect repositories, browse documents, and review
        versions through the API.
      </p>
      <section style={{ marginTop: "24px" }}>
        <h2>Next Steps</h2>
        <ul>
          <li>Hook up repository sync progress and document tree UI.</li>
          <li>Integrate keyword search UI with API endpoints.</li>
          <li>Add diff viewer for document versions.</li>
        </ul>
      </section>
    </main>
  );
}
