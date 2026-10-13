import { GITHUB_APP_INSTALL_URL } from "../config";

export default function ConnectGitHubButton() {
  const connectGitHub = () => {
    window.location.assign(GITHUB_APP_INSTALL_URL);
  };

  return (
    <button type="button" onClick={connectGitHub}>
      Connect GitHub
    </button>
  );
}
