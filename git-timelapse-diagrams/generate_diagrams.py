#!/usr/bin/env python3
"""
Generate Mermaid diagrams for the COMP 490
Live Git Repository Timelapse and AI-Assisted Software Evolution Dashboard.

Run:
    python generate_diagrams.py

Optional PNG/SVG rendering:
    npm install -g @mermaid-js/mermaid-cli
    python generate_diagrams.py --render
"""
from pathlib import Path
import argparse
import subprocess
import shutil

OUT = Path(__file__).parent / "diagrams"

DIAGRAMS = {
"01_system_architecture.mmd": r"""
flowchart LR
    U[User / Developer] -->|Connect GitHub / Explore| FE[React Frontend]
    FE -->|REST HTTPS| API[Spring Boot REST API]
    API --> RS[Repository Service]
    RS --> GS[Git Service / JGit]
    GS <--> G[(GitHub Repository)]
    G -->|Push event| WH[GitHub App Webhook]
    WH --> WSC[Webhook Controller]
    WSC --> WSS[Webhook Service]
    WSS --> RS

    RS --> MET[Metrics / Evolution Analysis]
    RS --> AI[LLM Analysis Service]
    AI --> EMB[Embedding Service]
    AI <--> LLM[LLM Provider]
    EMB <--> LLM
    RS <--> DB[(PostgreSQL + pgvector)]
    AI <--> DB
    EMB <--> DB

    RS -->|WebSocket / SSE| FE
    FE --> TL[Timelapse + Metrics + Search UI]
""",

"02_end_to_end_workflow.mmd": r"""
flowchart TD
    A[User installs GitHub App] --> B[Select authorized repositories]
    B --> C[Backend stores installation + repository metadata]
    C --> D[Obtain short-lived installation token]
    D --> E[JGit bare clone / fetch]
    E --> F[Extract commits, files, diffs, authors, +/-]
    F --> G[Build frontend-ready DTOs]
    G --> H[Calculate evolution metrics]
    H --> I[Generate LLM commit analysis]
    I --> J[Generate/store embeddings in pgvector]
    J --> K[REST API serves historical state]
    K --> L[React renders timelapse/dashboard]
    M[Developer pushes commit] --> N[GitHub sends signed webhook]
    N --> O{Signature valid?}
    O -- No --> P[Reject]
    O -- Yes --> Q[Identify repository]
    Q --> R[JGit fetch]
    R --> S[Compare lastProcessedSha to new HEAD]
    S --> T[Process only new commits]
    T --> H
    T --> U[Update lastProcessedSha]
    U --> V[Publish WebSocket/SSE event]
    V --> L
""",

"03_backend_components.mmd": r"""
flowchart TB
    subgraph Controllers
      RC[RepositoryController]
      GWC[GitHubWebhookController]
      GCC[GitHubConnectionController]
    end
    subgraph Services
      RS[RepositoryService]
      GS[GitService / JGit]
      GWS[GitHubWebhookService]
      GAS[GitHubAppService]
      GIS[GitHubInstallationService]
      GTS[GitHubTokenService]
      AS[LLM Analysis Service]
      ES[Embedding Service]
      MS[Metrics Service]
    end
    subgraph Persistence
      RJR[RepositoryJpaRepository]
      GIR[GitHubInstallationJpaRepository]
      DB[(PostgreSQL + pgvector)]
      BARE[(Bare Git Repositories)]
    end

    RC --> RS
    GWC --> GWS --> RS
    GCC --> GIS
    RS --> GS
    RS --> MS
    RS --> AS
    AS --> ES
    GIS --> GAS
    GAS --> GTS
    GTS --> GS
    GS <--> BARE
    RS --> RJR --> DB
    GIS --> GIR --> DB
    AS --> DB
    ES --> DB
""",

"04_frontend_components.mmd": r"""
flowchart TB
    APP[React App]
    APP --> SELECT[Repository Selector]
    APP --> STATUS[Analysis / Live Status]
    APP --> PLAYER[Timelapse Player]
    APP --> DETAILS[Commit Detail View]
    APP --> FILTERS[Filters Panel]
    APP --> METRICS[Metrics Dashboard]
    APP --> SEARCH[Semantic Evolution Search]
    APP --> EVOLUTION[File / Component Evolution]

    PLAYER --> SCRUB[Timeline Scrubber]
    PLAYER --> CONTROLS[Play Pause Prev Next Speed]
    DETAILS --> FILES[Changed Files + Additions/Deletions]
    DETAILS --> AIS[AI Summary / Purpose / Category]
    METRICS --> CHURN[Code Churn / Activity]
    METRICS --> HOT[Hotspots / Frequent Files]
    SEARCH --> RESULTS[Chronological Search Results]

    API[REST API Client] --> APP
    LIVE[WebSocket / SSE Client] --> APP
""",

"05_new_commit_sequence.mmd": r"""
sequenceDiagram
    actor Dev as Developer
    participant GH as GitHub
    participant WH as Spring Webhook
    participant RS as RepositoryService
    participant GT as GitHubTokenService
    participant JG as JGit
    participant AI as LLM/Embedding
    participant DB as PostgreSQL
    participant FE as React

    Dev->>GH: git push
    GH->>WH: signed push webhook
    WH->>WH: verify signature
    WH->>RS: synchronize(repository)
    RS->>GT: get installation token
    GT-->>RS: short-lived token
    RS->>JG: fetch(credentials)
    JG-->>RS: updated Git objects
    RS->>RS: commits between old SHA and new HEAD
    loop each new commit
        RS->>AI: commit metadata + selected diff
        AI-->>RS: summary/category/components/significance
        RS->>DB: cache analysis + embeddings
    end
    RS->>DB: update lastProcessedSha
    RS-->>FE: WebSocket/SSE update
    FE->>FE: append commit to timelapse
""",

"06_database_er.mmd": r"""
erDiagram
    GITHUB_INSTALLATIONS ||--o{ REPOSITORIES : authorizes
    REPOSITORIES ||--o{ ANALYSIS_PROFILES : has
    REPOSITORIES ||--o{ COMMIT_EMBEDDINGS : has
    REPOSITORIES ||--o{ COMMIT_ANALYSIS : has
    ANALYSIS_PROFILES ||--o{ COMMIT_EMBEDDINGS : generates
    ANALYSIS_PROFILES ||--o{ COMMIT_ANALYSIS : generates

    GITHUB_INSTALLATIONS {
      bigint id PK
      bigint github_installation_id
      bigint github_account_id
      text github_account_login
      timestamptz created_at
    }
    REPOSITORIES {
      bigint id PK
      text repo_key UK
      bigint provider_repository_id
      bigint installation_id FK
      text remote_url
      text default_branch
      text local_git_directory
      text last_processed_sha
      timestamptz created_at
      timestamptz last_opened_at
    }
    ANALYSIS_PROFILES {
      bigint id PK
      bigint repository_id FK
      text embedding_model
      int embedding_dimensions
      text llm_model
      text prompt_version
      timestamptz created_at
    }
    COMMIT_EMBEDDINGS {
      bigint id PK
      bigint repository_id FK
      bigint analysis_profile_id FK
      text commit_sha
      text document_type
      text file_path
      text content_hash
      vector embedding
      timestamptz created_at
    }
    COMMIT_ANALYSIS {
      bigint id PK
      bigint repository_id FK
      bigint analysis_profile_id FK
      text commit_sha
      text summary
      text purpose
      text category
      jsonb affected_components
      text significance
      text source_hash
      timestamptz created_at
    }
""",

"07_deployment.mmd": r"""
flowchart TB
    B[Browser / React SPA] -->|HTTPS| RP[Reverse Proxy]
    RP --> API[Spring Boot Backend]
    API --> DB[(PostgreSQL + pgvector)]
    API --> GIT[(Local Bare Git Repositories)]
    API -->|REST| LLM[LLM / Embedding Provider]
    API -->|GitHub REST API| GH[GitHub]
    GH -->|Signed Webhook| API
    API -->|WebSocket / SSE| B
""",

"08_github_installation_flow.mmd": r"""
sequenceDiagram
    actor U as User
    participant FE as React
    participant GH as GitHub
    participant BE as Spring Backend
    participant DB as PostgreSQL

    U->>FE: Connect GitHub
    FE->>GH: Open GitHub App installation
    GH->>U: Choose repositories
    U->>GH: Install
    GH->>BE: Redirect to setup URL
    BE->>GH: Validate installation / list repositories
    BE->>DB: Store installation + repository IDs
    BE-->>FE: Connected repositories
    FE-->>U: Open Timelapse
""",

"09_semantic_search_flow.mmd": r"""
flowchart LR
    Q[Natural-language query<br/>How did authentication evolve?]
    Q --> QE[Generate query embedding]
    QE --> PG[(pgvector similarity search)]
    PG --> R[Relevant commit/file-change records]
    R --> G[Retrieve authoritative Git evidence]
    G --> L[LLM synthesis]
    L --> O[Chronological evolution explanation]
    O --> UI[React search-results timeline]
    UI --> J[Jump to matching commit]
""",

"10_frontend_state_flow.mmd": r"""
stateDiagram-v2
    [*] --> NoRepository
    NoRepository --> Analyzing: Open repository
    Analyzing --> Ready: Initial analysis complete
    Analyzing --> Failed: Processing error
    Failed --> Analyzing: Retry
    Ready --> Playing: Play
    Playing --> Paused: Pause
    Paused --> Playing: Play
    Playing --> Ready: End reached
    Ready --> InspectingCommit: Select commit
    InspectingCommit --> Ready: Close details
    Ready --> Searching: Semantic search
    Searching --> InspectingCommit: Select result
    Ready --> Syncing: New webhook event
    Syncing --> Ready: Timeline updated
""",

"11_incremental_processing.mmd": r"""
flowchart TD
    A[Webhook or manual sync] --> B[JGit fetch]
    B --> C[Read stored lastProcessedSha]
    C --> D[Read current remote HEAD]
    D --> E{Same SHA?}
    E -- Yes --> F[No new work]
    E -- No --> G[Find commits between old SHA and HEAD]
    G --> H[Extract Git-derived evidence]
    H --> I{Cached AI record valid?}
    I -- Yes --> J[Reuse analysis]
    I -- No --> K[Generate analysis / embeddings]
    K --> L[Persist expensive AI-derived data]
    J --> M[Publish commit DTO]
    L --> M
    M --> N[Advance lastProcessedSha]
    N --> O[Notify React via WebSocket/SSE]
""",

"12_class_diagram.mmd": r"""
classDiagram
    class RepositoryController
    class GitHubWebhookController
    class GitHubConnectionController
    class RepositoryService {
      +openRepository()
      +sync()
      +getTimeline()
      +getCommit()
    }
    class GitService {
      +cloneBare()
      +fetch()
      +getCommitsBetween()
      +getDiff()
    }
    class GitHubWebhookService {
      +verifySignature()
      +handlePush()
    }
    class GitHubTokenService {
      +createAppJwt()
      +getInstallationToken()
    }
    class LLMAnalysisService {
      +analyzeCommit()
      +summarizePeriod()
    }
    class EmbeddingService {
      +embedCommit()
      +semanticSearch()
    }

    RepositoryController --> RepositoryService
    GitHubWebhookController --> GitHubWebhookService
    GitHubConnectionController --> GitHubTokenService
    GitHubWebhookService --> RepositoryService
    RepositoryService --> GitService
    RepositoryService --> LLMAnalysisService
    LLMAnalysisService --> EmbeddingService
    GitService ..> GitHubTokenService
"""
}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--render", action="store_true",
                        help="Render .mmd files to SVG using Mermaid CLI (mmdc).")
    args = parser.parse_args()

    OUT.mkdir(exist_ok=True)
    for filename, body in DIAGRAMS.items():
        (OUT / filename).write_text(body.strip() + "\n", encoding="utf-8")

    print(f"Generated {len(DIAGRAMS)} Mermaid source files in: {OUT}")

    if args.render:
        mmdc = shutil.which("mmdc")
        if not mmdc:
            raise SystemExit(
                "mmdc not found. Install it with:\n"
                "  npm install -g @mermaid-js/mermaid-cli"
            )
        for src in sorted(OUT.glob("*.mmd")):
            dest = src.with_suffix(".svg")
            subprocess.run([mmdc, "-i", str(src), "-o", str(dest),
                            "-b", "transparent"], check=True)
            print("Rendered:", dest)

if __name__ == "__main__":
    main()
