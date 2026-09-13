# Production deployment preparation

These files prepare production; they do not deploy it. The apex DNS still uses the
local tunnel. Do not run the server commands or change DNS until the deployment
step is authorized. Local `compose.yaml` is unchanged.

## Images and CI

After a push to main, the existing backend and frontend CI jobs must both pass.
The new publish-images job builds linux/amd64 images on GitHub runners and publishes
`ghcr.io/alexungu04genoff/ringlab-backend:FULL_COMMIT_SHA` and
`ghcr.io/alexungu04genoff/ringlab-frontend:FULL_COMMIT_SHA`. No VPS compilation or
automatic SSH deployment is configured. The separate manual workflow below is the
only GitHub deployment entry point. A failed second image publication means
that revision is not ready: deploy only after the entire publish job succeeds.
Record both image digests for reproducible rollbacks; do not deploy `latest`.

Backend Dockerfile builds only main sources using Maven/Java 21, then copies the
Quarkus fast-jar directory into a Java 21 JRE image. Tests are skipped during image
packaging because CI has already run them. It runs as UID/GID 10001 with the prod
profile and a heap ceiling of 65% of its 1536 MiB container limit. Runtime secrets
are never build arguments. The allowlist Docker context excludes test keys and
local build output. Base tags receive updates on rebuild; record final digests.

Frontend Dockerfile uses Node 22, `npm ci`, and `npm run build`, then copies only
dist plus Caddyfile into Caddy. Set the GitHub **repository variable**
`GOOGLE_CLIENT_ID` to the existing OAuth client ID before publishing. It is public
browser configuration, not a client secret. The same value goes into the VPS
`GOOGLE_CLIENT_ID`; CI passes it as `VITE_GOOGLE_CLIENT_ID` at build time. Changing
it requires rebuilding the frontend. Never supply an OAuth client secret.

For an authorized local image build (not required on the VPS):

```sh
docker build -t ringlab-backend:review backend
docker build --build-arg VITE_GOOGLE_CLIENT_ID=YOUR_EXISTING_PUBLIC_CLIENT_ID -t ringlab-frontend:review frontend
```

## Network and request routing

Cloudflare -> VPS TCP 80/443 -> Caddy -> backend:8080 -> postgres:5432.
Only Caddy publishes host ports. Caddy serves `/srv`, falls back to index.html for
SPA routes, and forwards `/api` and `/api/*` unchanged. Missing `/assets/*` files
return 404. No Vite server runs in production. Caddy persists certificates in
`ringlab-production_caddy_data`. HTTP/3 is disabled; no UDP port is required.

Backend and Caddy share the private Docker bridge `proxy`. Only Caddy has the fixed
address 172.30.50.2; backend is pinned to 172.30.50.3 so Docker cannot assign it
Caddy's address during startup. PostgreSQL and backend share a separate internal `database`
network; Caddy cannot connect directly to PostgreSQL. Backend has outbound access
through the proxy bridge for Google, Brevo, and Steam. Check for subnet conflicts
before deploying; if changed, update both the fixed Caddy IP and TRUSTED_PROXY_ADDRESS.
Never join untrusted containers to these networks or expose Docker's control socket.
Docker published ports bypass some UFW rules: publishing only 80/443 is deliberate.

Rate limiting keeps the existing loopback trust for local tunnels. Production adds
one exact trusted peer via `TRUSTED_PROXY_ADDRESS`, guarded by
`TRUST_CLOUDFLARE_CLIENT_IP=true`. It does not trust the whole Docker subnet, DNS
hostnames, or CIDR strings. Invalid peer/header values fail closed to the peer IP.
Quarkus proxy-address rewriting is explicitly disabled so its immediate peer check
uses the socket peer. Caddy accepts CF-Connecting-IP only from Cloudflare's published
IPv4/IPv6 ranges and **overwrites** that upstream header with its parsed client IP.
A direct origin caller's forged header is therefore replaced with the actual peer.
An arbitrary backend peer's forged header is ignored. Host/Docker administrators and
Caddy itself are trusted; a compromised proxy is outside this boundary.

Sources: [Caddy trusted proxies](https://caddyserver.com/docs/caddyfile/options#trusted-proxies),
[proxy header replacement](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#headers),
[Cloudflare ranges](https://www.cloudflare.com/ips/). Review the bundled ranges
before deployment and when Cloudflare changes them. Keep Cloudflare Pseudo IPv4
overwrite mode off and avoid Workers that alter visitor-IP headers on this route.
Direct origin traffic still bypasses Cloudflare edge protections; a later origin
firewall restriction must account for certificate validation and SSH access.

## Manual GitHub deployment

Local `git commit` has no deployment/network effect from this configuration (no
Git hooks are added). A push runs CI. Successful main CI may publish SHA-tagged
images, but neither CI nor image publication invokes production deployment.
`deploy-production.yml` has **only workflow_dispatch**, with no push, workflow_run,
schedule, or reusable-workflow trigger. It accepts dispatches from main only.

Local verification of this addition: deployment script and all five multiline
workflow shell blocks passed `bash -n`; mocked success, image-pull failure, and
origin-health failure scenarios passed, preserving runtime files and ensuring
pull failure never starts containers. Static checks confirmed manual-only triggers,
strict SSH host checking, and non-cancelling concurrency. `git diff --check` passed.
These checks did not contact GitHub/GHCR/Hetzner or run a deployment. The workflow
itself still needs its first authorized GitHub run after access and production
prerequisites are configured; no application suites were rerun for this addition.

After these files are reviewed and manually pushed to the default branch:

1. Open GitHub -> Actions -> **Deploy production manually** -> **Run workflow**.
2. Select branch **main** for the workflow code.
3. Enter the full lowercase 40-character commit SHA in **revision**. This may be an
   older SHA; the branch selector does not choose the application revision.
4. Click Run workflow and complete any production-environment approval.

The workflow checks for a successful main push run of ci.yml for that exact SHA,
then requires both GHCR images. Missing images or denied package access fail before
SSH. SHA tags identify the revision, but registry tags can technically be moved:
the workflow resolves them to content digests and deploys `image@sha256:...`, never
main/latest. Keep old images and successful CI run records available for rollback;
do not delete or deliberately republish old release tags.

Create GitHub Environment **production** manually. Restrict deployment branches to
main and optionally enable required reviewers/prevent self-review where supported
by your GitHub plan. Workflow dispatch is required even without approval rules.
See [GitHub workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
for manual triggers and environment configuration.

Create these **production environment secrets** manually:

| Secret | Value |
| --- | --- |
| PRODUCTION_HOST | VPS IPv4 or hostname, currently 204.168.215.4; SSH port 22 |
| PRODUCTION_SSH_USER | deploy, or a dedicated deployment user |
| PRODUCTION_SSH_PRIVATE_KEY | Dedicated Actions SSH key; add its public key to that user's authorized_keys on the VPS |
| PRODUCTION_SSH_KNOWN_HOSTS | Verified OpenSSH known_hosts line for the exact host above; obtain/verify the host key through a trusted channel |

The workflow does not run unverified ssh-keyscan or disable host-key verification.
No secrets or environment settings are created by this repository change. The SSH
user must be able to use noninteractive `sudo -n bash` to run the deployment script;
this is privileged server access, so protect the key and production environment.
The job writes the key only to the ephemeral runner and removes it afterward.

No extra GHCR **GitHub secret** is needed: the job's short-lived GITHUB_TOKEN has
packages:read. Give this repository Actions read access to both GHCR packages if
they do not inherit it. For **private** packages, the VPS additionally needs a
read:packages credential entered once directly via `sudo docker login ghcr.io`;
public images need no VPS registry credential. The workflow does not transmit
GITHUB_TOKEN to the VPS. DB passwords, SMTP credentials, JWT keys and other runtime
settings remain solely in /opt/ringlab/production.env and secrets/jwt.

Prerequisites: initial server/Compose/secrets setup is already complete, PostgreSQL
is healthy, both images can be pulled, and origin HTTPS already works. This manual
release workflow does not provision a server, install Compose, upload a Compose
file, set up certificates or move DNS. The reviewed Compose file stays on the VPS;
future infrastructure changes require a separate reviewed update there.

The workflow streams scripts/deploy-production.sh over verified SSH. The script
locks deployments, validates configuration, pulls **both** selected digests before
changing containers, then updates only backend/Caddy without recreating PostgreSQL.
It leaves production.env/JWT files intact and writes image references to
deployment.env, retaining previous-deployment.env. It checks HTTPS homepage/API
directly on 127.0.0.1 with the production hostname, bypassing Cloudflare/the tunnel.
Success records last-successful-deployment.env and the commit/digests in the Actions
summary. These are smoke checks, not full functional acceptance tests.

If an update/check fails after containers change, the workflow fails visibly;
production may be running the requested revision. Inspect the VPS before deciding
on rollback. No automatic rollback is attempted because migrations may have run.

Rollback: run the **same manual workflow** with a previous known-good SHA. It pulls
existing images, never builds source on the VPS. Confirm schema compatibility and
take a backup first; Flyway does not downgrade schema. Use recorded previous digest
references for manual recovery if tags or CI records were removed. For later
server-side Compose maintenance after a workflow deployment, include the release
file so the old image values in production.env cannot select an outdated release:

```sh
sudo docker compose --env-file production.env --env-file deployment.env -f compose.production.yaml ps
# An explicit later maintenance/recovery action, not a command run by this task:
sudo docker compose --env-file production.env --env-file deployment.env -f compose.production.yaml up -d --no-deps backend caddy
```

## Runtime configuration, keys and data

Copy production.env.example to production.env on the VPS. No real production
values belong in GitHub secrets or the repository. Compose requires DB_USER,
DB_PASSWORD, GOOGLE_CLIENT_ID and the two image references. Supply all SMTP_* values
before testing registration. The backend env file uses Compose's raw format
(Compose >= 2.30), so SMTP credentials need no quotes or escaping. Do not add inline
comments to values. Use an alphanumeric DB user and a random hex password because
the DB values also pass through Compose interpolation. Never reuse local credentials.

Compose fixes DB_URL to the dedicated database and JWT_PUBLIC_KEY/JWT_PRIVATE_KEY
to read-only file locations. It also fixes PUBLIC_BASE_URL and FRONTEND_ORIGIN to
https://ringlabgarage.com and MAIL_FROM to noreply@ringlabgarage.com. Rate limits
retain application.properties defaults; optional RATE_LIMIT_* values can go in
production.env. No signing key or fallback production secret is in an image.

Brevo on Hetzner: use smtp-relay.brevo.com, port 587, SMTP_TLS=false and
QUARKUS_MAILER_START_TLS=REQUIRED, plus the Brevo SMTP login and an active SMTP key.
SMTP_TLS=false disables implicit TLS, not encryption: STARTTLS is mandatory and
certificate validation remains enabled. Hetzner blocks outbound 465 by default.
Enter credentials directly in the protected server file. If Brevo SMTP IP blocking
is enabled, authorize only the VPS's actual outgoing IP (currently 204.168.215.4).
The authenticated domain/sender and existing SPF/DKIM/DMARC are untouched.
Keep mail mocking disabled in production.

PostgreSQL 17 initializes `ringlab_production` once in
`ringlab-production_postgres_data`, mounted at `/var/lib/postgresql/data` in its
container. On the standard Docker installation the host path is
`/var/lib/docker/volumes/ringlab-production_postgres_data/_data`; confirm with
`docker volume inspect`. Changing environment credentials does not rotate an
already initialized database password. Flyway runs at normal backend startup;
Hibernate validates. Do not manually seed, reset, edit schema, or use `down -v`.
The app uses the dedicated Postgres bootstrap role for this small deployment;
that role is a superuser inside this isolated database instance.

## Later VPS commands

Run only after authorization, logged in as deploy. Transfer the reviewed
compose.production.yaml and production.env.example to /opt/ringlab (for example
using scp); no source checkout is needed there. Use `sudo docker` unless deploy
has separately been granted Docker access.

```sh
sudo install -d -o deploy -g deploy -m 700 /opt/ringlab
cd /opt/ringlab
umask 077
# First installation only; do not overwrite an existing environment or key pair.
test ! -e production.env && cp production.env.example production.env
chmod 600 production.env
nano production.env
# Generate the DB password directly into the editor/file, never into chat.
# One possible local terminal generator: openssl rand -hex 32
sudo install -d -o 10001 -g 10001 -m 700 secrets/jwt
sudo test ! -e secrets/jwt/private.pem && sudo test ! -e secrets/jwt/public.pem && \
  sudo sh -c 'umask 077; openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt/private.pem && openssl pkey -in secrets/jwt/private.pem -pubout -out secrets/jwt/public.pem && chown 10001:10001 secrets/jwt/*.pem'
# Private GHCR packages: authenticate interactively with a read:packages token.
# Public packages do not need login. Never put tokens in command arguments.
sudo docker login ghcr.io
sudo docker compose --env-file production.env -f compose.production.yaml config --quiet
sudo docker compose --env-file production.env -f compose.production.yaml pull
```

Before DNS moves, automatic public certificate issuance cannot succeed against the
old tunnel. First run an HTTP-only preview on the origin; this is NOT the final
HTTPS configuration and must not receive real login credentials over public HTTP:

```sh
CADDY_SITE=http://ringlabgarage.com sudo --preserve-env=CADDY_SITE docker compose --env-file production.env -f compose.production.yaml up -d
sudo docker compose --env-file production.env -f compose.production.yaml ps
curl --fail -H 'Host: ringlabgarage.com' http://127.0.0.1/
curl --fail -H 'Host: ringlabgarage.com' http://127.0.0.1/api/racers
curl --fail -H 'Host: ringlabgarage.com' http://127.0.0.1/api/builds
```

Review Flyway/startup logs privately (do not publish credentials or tokens).
An HTTP 200 alone is not full production verification. For browser testing before
cutover, use a separately planned trusted certificate/hosts override or secure
preview hostname; this task does not create one.

At the separately approved cutover, preserve/move the local tunnel to the dev
hostname, record existing DNS for rollback, point the apex at 204.168.215.4,
then recreate Caddy without the HTTP override:

```sh
sudo docker compose --env-file production.env -f compose.production.yaml up -d caddy
```

Caddy obtains and renews HTTPS certificates using public ACME. Ensure ACME HTTP
validation can reach Caddy through Cloudflare; review edge HTTPS redirects/rules.
Use Cloudflare Full (strict), never Flexible. Verify certificate issuance and
HTTPS before calling cutover successful. An alternative pre-provisioned origin
certificate needs a reviewed Caddy TLS mount/config change before cutover.

## Release, rollback and remaining checks

Preparation checks on 2026-09-14:

- `mvn -f backend/pom.xml -Dtest=RateLimitTest test`: 11 passed, no failures/errors.
- Compose validation against the example file passed. The in-memory replacement
  below selects the template for env_file without creating any real secret file:

  ```powershell
  $reviewCompose = (Get-Content compose.production.yaml -Raw).Replace('./production.env', './production.env.example')
  $reviewCompose | docker compose --project-directory E:\RingLab --env-file production.env.example -f - config --quiet
  ```

- `git diff --check`: passed (Git reports Windows LF/CRLF conversion warnings).
- `docker build -t ringlab-backend:review backend` and
  `docker build --build-arg VITE_GOOGLE_CLIENT_ID=deployment-check.apps.googleusercontent.com -t ringlab-frontend:review frontend`:
  both blocked during base-image download with `tls: bad record MAC`, before
  application compilation. No retry or larger test suite was used as a workaround.
- No Caddy runtime test, full frontend build, full backend suite, live stack test,
  or production test was completed in this task. Earlier revision verification
  remains separate from this change. No local services were restarted.

After fixing the Docker download problem, manually run the two image builds above
(use the actual public Google ID for a deployable frontend), then:

```sh
docker run --rm --network none ringlab-frontend:review caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
```

Run RateLimitTest from IntelliJ or with the Maven command above to review the Java
change. Existing CI performs broader backend/frontend verification before image
publication. End-to-end proxy spoofing/rate-limit checks still need the isolated
deployment; the unit test covers the backend trust boundary only.

Before each later release, take a database backup, record both old image digests,
and replace the two image references with one CI-verified revision (prefer digests).
Run `pull`, then `up -d` with the same --env-file and -f arguments above. Startup
order waits for Postgres, but backend readiness must be checked through the API.
There is brief downtime; this is a single VPS without automatic failover.

Rollback: restore the previous two image references and repeat pull/up. Flyway
does not downgrade schema. Check migration compatibility first; incompatible
migrations require restoring a pre-release backup with downtime and possible loss
of newer writes. Never automatically roll back just the DB or run down -v.

Example consistent logical backup (protect the output and copy encrypted off-VPS):

```sh
umask 077
sudo docker compose --env-file production.env -f compose.production.yaml exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "ringlab-$(date +%Y%m%d-%H%M%S).dump"
```

Schedule daily encrypted off-server backups and test restoration into an isolated
database before relying on them. Back up JWT keys and production.env securely too.
No backup service or paid add-on is enabled by these files. The manual deployment
workflow becomes available only after the user commits/pushes and configures access.

Still required: review/commit/push manually; set the public Google repository
variable; complete CI/image publication; enter VPS credentials; check existing
Google OAuth authorized JavaScript origin https://ringlabgarage.com (reuse client);
deploy preview; plan certificate/DNS transition preserving dev; verify homepage,
catalog, registration, delivered verification link, verified local login, Google
login, build browsing/CRUD, voting, comments, per-client 429 responses and spoofed
header rejection, and Steam failure fallback. No live result is claimed here.
After production is stable, configure the production environment/SSH secrets and
use the manual workflow above. Image publication never starts that workflow.
