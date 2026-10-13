import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { connectInstallation } from "../api/github";

export default function GitHubSetupPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const installationIdText = searchParams.get("installation_id");

    if (!installationIdText) {
      setError("GitHub did not provide an installation_id.");
      return;
    }

    const installationId = Number(installationIdText);

    if (!Number.isSafeInteger(installationId)) {
      setError("GitHub returned an invalid installation_id.");
      return;
    }

    async function finishSetup() {
      try {
        await connectInstallation(installationId);

        sessionStorage.setItem(
          "githubInstallationId",
          installationId.toString()
        );

        navigate(`/repositories?installationId=${installationId}`, {
          replace: true,
        });
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "GitHub setup failed."
        );
      }
    }

    finishSetup();
  }, [navigate, searchParams]);

  return (
    <main className="page">
      <section className="card">
        <h1>Connecting GitHub</h1>
        {error ? (
          <>
            <p className="error">{error}</p>
            <button onClick={() => navigate("/home")}>Back home</button>
          </>
        ) : (
          <p>Registering your GitHub App installation...</p>
        )}
      </section>
    </main>
  );
}
