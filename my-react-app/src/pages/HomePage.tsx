import ConnectGitHubButton from "../components/ConnectGitHubButton";

export default function HomePage() {
  return (
    <main className="page">
      <section className="card">
        <h1>Repository Timelapse</h1>
        <p>
          Connect GitHub, choose a repository, and load it into the timelapse
          dashboard.
        </p>
        <ConnectGitHubButton />
      </section>
    </main>
  );
}
