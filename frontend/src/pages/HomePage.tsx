import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import ConnectGitHubButton from "../components/ConnectGitHubButton";
import { registerRepository } from "../api/repositories";

export default function HomePage() {
  const navigate = useNavigate();
  const [url, setUrl] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submitPublicRepository(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (loading) return;
    setError(null);
    const match = url.trim().match(/^https:\/\/github\.com\/([A-Za-z0-9_.-]+)\/([A-Za-z0-9_.-]+?)(?:\.git)?\/?$/i);
    if (!match || match[1] === "." || match[1] === ".." || match[2] === "." || match[2] === "..") {
      setError("Enter a public GitHub repository URL, such as https://github.com/owner/repo");
      return;
    }
    setLoading(true);
    try {
      const remoteUrl = `https://github.com/${match[1]}/${match[2]}.git`;
      const repository = await registerRepository({ remoteUrl, monitoringType: "POLLING", visibility: "PUBLIC" });
      navigate(`/repositories/${repository.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not import repository. Make sure it is public.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="page">
      <section className="card">
        <h1>Repository Timelapse</h1>
        <p>Explore the history of any public GitHub repository, even if you don't own it.</p>
        <form onSubmit={submitPublicRepository}>
          <label htmlFor="public-repo-url">Public GitHub repository URL</label>
          <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", marginTop: "0.5rem" }}>
            <input id="public-repo-url" type="url" required value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="https://github.com/owner/repo" disabled={loading}
              style={{ flex: "1 1 260px", minWidth: 0 }} />
            <button type="submit" disabled={loading}>{loading ? "Importing repository..." : "View timeline"}</button>
          </div>
        </form>
        {loading && <p role="status">Cloning and processing repository history. Large repositories may take a while.</p>}
        {error && <p className="error" role="alert">{error}</p>}
        <hr style={{ margin: "1.5rem 0" }} />
        <p>Or connect GitHub to browse repositories you have access to, including private repositories.</p>
        <ConnectGitHubButton />
      </section>
    </main>
  );
}
