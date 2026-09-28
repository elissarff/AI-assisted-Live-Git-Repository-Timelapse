import { Navigate, Route, Routes } from "react-router-dom";
import HomePage from "./pages/HomePage";
import GitHubSetupPage from "./pages/GitHubSetupPage";
import RepositoriesPage from "./pages/RepositoriesPage";
import RepositoryPage from "./pages/RepositoryPage";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/home" replace />} />
      <Route path="/home" element={<HomePage />} />
      <Route path="/github/setup" element={<GitHubSetupPage />} />
      <Route path="/repositories" element={<RepositoriesPage />} />
      <Route path="/repositories/:repoKey" element={<RepositoryPage />} />
      <Route path="*" element={<Navigate to="/home" replace />} />
    </Routes>
  );
}
