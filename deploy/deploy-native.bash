#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
inventory="${1:?Usage: bash deploy/deploy-native.bash /absolute/path/to/inventory.ini}"
release_id="manual-$(date -u +%Y%m%dT%H%M%SZ)"
build_scratch="$(mktemp -d "${TMPDIR:-/tmp}/gitlapse-release.XXXXXX")"
trap 'rm -rf "$build_scratch"' EXIT
export ANSIBLE_LOCAL_TEMP="${ANSIBLE_LOCAL_TEMP:-$build_scratch/ansible}"
export ANSIBLE_SSH_CONTROL_PATH_DIR="${ANSIBLE_SSH_CONTROL_PATH_DIR:-$build_scratch/cp}"

# Upload source only. Both application builds and backend tests run on the VM.
tar -czf "$build_scratch/backend-source.tar.gz" -C backend pom.xml src
tar --exclude=node_modules --exclude=dist --exclude='*.tsbuildinfo' --exclude='.env*' \
  -czf "$build_scratch/frontend-source.tar.gz" -C frontend .
export GITLAPSE_RELEASE_ID="$release_id" GITLAPSE_BUILD_SCRATCH="$build_scratch"
python3 - <<'PY'
import json, os, subprocess
from pathlib import Path
scratch = Path(os.environ['GITLAPSE_BUILD_SCRATCH'])
branch = subprocess.check_output(['git', 'branch', '--show-current'], text=True).strip()
commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
dirty = subprocess.check_output(['git', 'status', '--porcelain', '--', 'backend/pom.xml', 'backend/src', 'frontend'], text=True).strip()
variables = {
    'release_id': os.environ['GITLAPSE_RELEASE_ID'],
    'backend_source_archive': str(scratch / 'backend-source.tar.gz'),
    'frontend_source_archive': str(scratch / 'frontend-source.tar.gz'),
    'source_branch': branch + (' (working copy)' if dirty else ''),
    'source_commit': None if dirty else commit,
    'health_base_url': 'https://gitlapse.dev',
}
(scratch / 'release-vars.json').write_text(json.dumps(variables))
PY
ansible-playbook -i "$inventory" deploy/ansible/native.yml
ansible-playbook -i "$inventory" deploy/ansible/native-release.yml -e "@$build_scratch/release-vars.json"
