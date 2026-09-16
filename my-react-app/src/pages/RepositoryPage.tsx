import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getRepository } from "../api/repositories";
import type { RegisteredRepository } from "../types/repository";

export default function RepositoryPage() {
  const { repoKey } = useParams();
  const [repository, setRepository] =
    useState<RegisteredRepository | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!repoKey) {
      setError("Missing repository key.");
      return;
    }

    getRepository(repoKey)
      .then(setRepository)
      .catch((err) =>
        setError(
          err instanceof Error ? err.message : "Could not load repository."
        )
      );
  }, [repoKey]);

  return (
    <main className="page">
      <section className="card">
        <Link to="/home">Back home</Link>
        <h1>{repository?.fullName ?? repository?.name ?? "Repository"}</h1>

        {error && <p className="error">{error}</p>}
        {!error && !repository && <p>Loading repository...</p>}

        {repository && (
          <dl className="details">
            <dt>Repository key</dt>
            <dd>{repository.repoKey}</dd>
            <dt>Default branch</dt>
            <dd>{repository.defaultBranch ?? "-"}</dd>
            <dt>Visibility</dt>
            <dd>{repository.visibility ?? "-"}</dd>
            <dt>Monitoring</dt>
            <dd>{repository.monitoringType ?? "-"}</dd>
            <dt>Last processed SHA</dt>
            <dd>{repository.lastProcessedSha ?? repository.headSha ?? "-"}</dd>
          </dl>
        )}
      </section>
    </main>
  );
}
