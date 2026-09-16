# Git Repository Timelapse — Diagram Pack

This pack generates Mermaid source files for the proposed full-stack system.

## Included diagrams

1. High-level system architecture
2. End-to-end workflow
3. Backend component diagram
4. Frontend component diagram
5. New-commit sequence diagram
6. Database ER diagram
7. Deployment diagram
8. GitHub App installation flow
9. Semantic evolution search / RAG flow
10. Frontend state diagram
11. Incremental commit-processing flow
12. Backend class diagram

## Run

Requires Python 3. No Python packages are required just to generate `.mmd` files.

```bash
python generate_diagrams.py
```

The Mermaid files appear in `diagrams/`.

## Render SVG files locally

Install Mermaid CLI:

```bash
npm install -g @mermaid-js/mermaid-cli
```

Then:

```bash
python generate_diagrams.py --render
```

Or render one diagram:

```bash
mmdc -i diagrams/01_system_architecture.mmd -o diagrams/01_system_architecture.svg
```

## Preview without installing Mermaid CLI

Open a `.mmd` file in a Mermaid-compatible Markdown/editor extension, or paste its contents into the Mermaid Live Editor.
