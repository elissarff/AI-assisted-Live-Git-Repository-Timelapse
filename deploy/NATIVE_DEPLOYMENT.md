# GitLapse Oracle VM deployment

GitLapse runs continuously on the Oracle Ubuntu ARM64 VM with Nginx, OpenJDK 25,
PostgreSQL, and a systemd backend service. GitHub Actions deploys only pushes to
the `deploy` branch. Ansible uploads that exact commit, builds both applications
on the VM, runs backend tests, and restarts the backend after a successful build.
The older Docker Compose and Caddy files are not used by this workflow.

## Production branch and automatic deployment

Push or merge a ready commit into `deploy`, then push that branch to GitHub:

```bash
git switch deploy
git merge dev
git push origin deploy
```

The workflow `.github/workflows/deploy.yml` checks out the pushed commit, uploads
only application source, provisions server settings, runs `npm ci` and the
production frontend build on the VM, then runs Maven `clean verify` on the VM.
It saves the artifacts in a new release directory, backs up PostgreSQL, switches
the active release, restarts the backend, and verifies the public website/API.
The old app remains running during builds. The backend restart causes a brief
interruption. Builds or tests failing before activation leave the old release
running. Failed activation checks restore the previous application release.

Both the workflow trigger and the deployment job require `refs/heads/deploy`;
pushes to `dev`, `main`, and other branches do not deploy. GitHub concurrency
serializes production runs without cancelling an active deployment.

GitHub Actions needs one secret, `ORACLE_SSH_PRIVATE_KEY`, in the repository's
`production` environment (or as a repository Actions secret). Use the dedicated
GitLapse deployment key; the personal passphrase-protected Oracle key stays local.
The dedicated public key has SSH forwarding and PTY disabled on the VM. This key
can run Ansible as `ubuntu` with sudo, so repository administrators and authors
allowed to push `deploy` must be trusted with deployment access.

The verified VM SSH host key is pinned in `deploy/ansible/known_hosts`. If the VM
is replaced, verify its new host key independently before changing that file.
Do not replace this with an unverified `ssh-keyscan` during a workflow run.

`https://gitlapse.dev/deployment.json` records the current release ID, source
branch, full commit, deployment time, and GitHub Actions run URL. The workflow
checks that the live commit matches the pushed SHA. Application secrets, database
data, and Git clones stay on the VM; they are not copied from development or
replaced during a release. GitHub Actions is not the process keeping the site up:
systemd keeps it running even when no workflow is active or the Mac is off.

## Current setup

- SSH account: `ubuntu@168.138.91.25`.
- Frontend: `/opt/gitlapse/current/frontend`, served by Nginx.
- Backend: `/opt/gitlapse/current/backend.jar`, managed by `gitlapse.service`.
- Backend listens on `127.0.0.1:8080`; PostgreSQL listens on `127.0.0.1:5432`.
- Persistent clones: `/var/lib/gitlapse/repositories`.
- Secrets: `/etc/gitlapse/backend.env`, readable by root, loaded by systemd.
- Database password: generated once on the server in `/etc/gitlapse/db-password`.
- Backups: daily PostgreSQL custom-format dumps in `/var/backups/gitlapse`, with
  seven days of local retention. A separate off-VM backup is still needed.
- Host firewall allows 22, 80, and 443, preserving Oracle image rules.
- Automatic security updates are enabled. Reboots are handled explicitly.
- Frontend builds use the checksum-verified official Node.js 24.18.1 ARM64 runtime.

The Java 25 requirement follows from the installed RefactoringMiner 3.1.6
dependency: its Java 23 class files cannot be read by Java 21. Both the VM
build and backend runtime use Java 25.

Verified on 23 November 2026: the frontend production build and backend Maven
verification passed (38 tests, no failures, one skipped). Flyway migrations V1
through V10 succeeded. A backup restored successfully into a temporary database.
After installing Ubuntu updates and rebooting into kernel `7.0.0-1017-oracle`,
Nginx, PostgreSQL, and GitLapse started automatically; `/home` and `/health`
responded successfully through Nginx. The Oracle-side network rules were subsequently opened, and `gitlapse.dev` was registered with Cloudflare Registrar.

## Repeat deployment from the Mac

Unlock the SSH key in the local SSH agent first. Verify the VM host key through
a trusted channel and keep SSH host-key checking enabled.

Create a private inventory outside version control:

```ini
[oracle]
node-01 ansible_host=168.138.91.25 ansible_user=ubuntu ansible_python_interpreter=/usr/bin/python3
```

Run from the project root:

```bash
bash deploy/deploy-native.bash /absolute/path/to/inventory.ini
```

The script uploads backend `pom.xml` and `src` plus frontend source. Both builds
and backend tests run on the VM. Local `.env` files, dependencies, build output,
SSH keys, and GitHub private keys are excluded. For dirty application working
copies, the deployment manifest leaves `source_commit` null rather than claiming
an exact commit. Normal GitHub Actions releases always have an exact source SHA.
Failed post-deployment health checks restore the previous release if one exists.
The first release has no earlier release to restore.

The native playbook now defaults to the purchased domain `gitlapse.dev`.
Override it with `-e public_hostname=...` when needed. A command-line extra
variable overrides that default; an inventory variable does not override a play variable.

## Domain and HTTPS configuration

Current production status: `https://gitlapse.dev` is live. Cloudflare proxying is
enabled for the root A record, SSL/TLS is Full (strict), and the origin uses a
Let’s Encrypt certificate. Public frontend and backend health checks passed
through Cloudflare. Certificate expiry is 22 February 2027; the automatic renewal
simulation passed. The backend allowed origin is `https://gitlapse.dev`.

The following records the setup procedure for future rebuilds:

1. Domain registration is complete: `gitlapse.dev` through Cloudflare Registrar.
2. Confirm the Oracle public IP is reserved or arrange a stable public IP.
3. Add stateful Oracle ingress TCP rules from `0.0.0.0/0` to destination ports
   80 and 443, with source ports All. Do not expose 8080 or 5432.
4. Create DNS `A @ 168.138.91.25`. If using Cloudflare, start with DNS only so
   visitors connect directly to the Oracle VM.
5. Set the Nginx hostname through the native playbook, verify public DNS and HTTP,
   then obtain the certificate:

```bash
sudo certbot certonly --webroot -w /var/www/letsencrypt -d gitlapse.dev
```

6. Rerun `native.yml` with `-e public_hostname=gitlapse.dev`. The template detects
   the certificate, configures HTTPS and HTTP redirects, and retains the ACME
   challenge path for renewals.
7. The playbook sets `FRONTEND_ORIGIN=https://gitlapse.dev` when the certificate
   exists and restarts the deployed backend if the origin changes. Run
   `sudo certbot renew --dry-run --no-random-sleep-on-renew` and verify the site.
8. The provisioning playbook installs a Certbot deploy hook to validate and reload
   Nginx after certificate renewal; confirm `certbot.timer` remains enabled.

After the origin certificate and HTTPS work, enable the Cloudflare proxy (orange
cloud) and use SSL/TLS Full (strict) mode. Both the visitor-to-Cloudflare and
Cloudflare-to-Oracle connections will use HTTPS. The `.dev` TLD is HSTS preloaded;
browsers require working HTTPS before the site will load.

Do not request certificates until domain ownership and DNS are confirmed.
For release health checks after HTTPS is enabled, pass
`-e health_base_url=https://gitlapse.dev` to `native-release.yml`.

## Optional integrations

GitHub App and Gemini credentials have not been copied from the development
environment. Configure these in `/etc/gitlapse/backend.env` when ready. The
frontend currently retains its existing development GitHub App installation URL;
update that production build setting if a separate production GitHub App is used.
Update the GitHub App webhook and setup URLs for the production domain and match
the VM webhook secret. Do not post credentials in chat or commit them.

## Operations

```bash
sudo systemctl status gitlapse nginx postgresql
sudo journalctl -u gitlapse -n 100 --no-pager
curl -fsS http://127.0.0.1:8080/health
sudo nginx -t
sudo systemctl restart gitlapse
sudo /etc/cron.daily/gitlapse-backup
```

Run `native-maintenance.yml` during a maintenance window to apply package upgrades,
reboot when Ubuntu requires it, and check service recovery.

For rollback, use `sudo ln -sfn /opt/gitlapse/releases/PREVIOUS_RELEASE /opt/gitlapse/current`
and restart `gitlapse`. Review database migration compatibility before rollback;
application rollback does not undo database migrations.

Restore backups into a separate test database first. Never overwrite a production
database as a routine check. The initial deployment verified a full dump/restore
in a temporary database and confirmed all ten migration records were restored.
