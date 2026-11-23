import AiExplanation from "./AiExplanation";
export default function AiFileMenu({repoKey,sha,path}:{repoKey:string;sha:string;path:string}) {
  return <details className="ai-file-menu"><summary className="ai-action">✦ Explain ▾</summary><div>
    <AiExplanation repoKey={repoKey} sha={sha} path={path} type="FILE_SNAPSHOT" label="File" />
    <AiExplanation repoKey={repoKey} sha={sha} path={path} type="FILE_CHANGE" label="Changes" />
    <AiExplanation repoKey={repoKey} sha={sha} path={path} type="FILE_EVOLUTION" label="Evolution" />
  </div></details>;
}
