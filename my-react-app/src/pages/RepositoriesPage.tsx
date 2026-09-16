import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { getInstallationRepositories } from "../api/github";
import { registerRepository } from "../api/repositories";
import type { GitHubRepository } from "../types/github";

export default function RepositoriesPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [repositories, setRepositories] = useState<GitHubRepository[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loadingRepoId, setLoadingRepoId] = useState<number | null>(null);

  const installationId = Number(
    searchParams.get("installationId") ??
      sessionStorage.getItem("githubInstallationId")
  );

  useEffect(() => {
    if (!Number.isSafeInteger(installationId)) {
      setError("No GitHub installation is selected.");
      return;
    }

    getInstallationRepositories(installationId)
      .then(setRepositories)
      .catch((err) =>
        setError(
          err instanceof Error ? err.message : "Could not load repositories."
        )
      );
  }, [installationId]);

  const importRepository = async (repo: GitHubRepository) => {
    setLoadingRepoId(repo.providerRepositoryId);
    setError(null);

    try {
      const registered = await registerRepository({
        remoteUrl: repo.cloneUrl,
        fullName: repo.fullName,
        providerRepositoryId: repo.providerRepositoryId,
        monitoringType: "GITHUB_APP",
        visibility: repo.privateRepository ? "PRIVATE" : "PUBLIC",
        installationId,
      });

      navigate(`/repositories/${registered.repoKey}`);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Could not register repository."
      );
    } finally {
      setLoadingRepoId(null);
    }
  };

  return (
    <main className="page">
      <section className="card">
        <h1>Choose a repository</h1>
        {error && <p className="error">{error}</p>}

        {!error && repositories.length === 0 && <p>Loading repositories...</p>}

        <div className="repo-list">
          {repositories.map((repo) => (
            <article className="repo-row" key={repo.providerRepositoryId}>
              <div>
                <strong>{repo.fullName}</strong>
                <div className="muted">
                  {repo.privateRepository ? "Private" : "Public"} - {repo.defaultBranch}
                </div>
              </div>

              <button
                disabled={loadingRepoId === repo.providerRepositoryId}
                onClick={() => importRepository(repo)}
              >
                {loadingRepoId === repo.providerRepositoryId
                  ? "Importing..."
                  : "Import"}
              </button>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
