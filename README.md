# micro-agent-teams

A collaboration substrate that turns AI agents into long-lived team members
working alongside humans. Design doc: TBD.

This README covers running the current milestone locally: login end-to-end
through the real stack (frontend → nginx → backend/cheese-auth → Postgres).

## Architecture

```
                    ┌──────────────┐
   browser ───────► │    nginx     │  cheese-prod-client.119net.ghg.org.cn
                    └──────┬───────┘
              ┌────────────┼────────────┐
              │            │            │
         /  (root)     /api/*        /nt/*
              │            │            │
              ▼            ▼            ▼
        ┌───────────┐ ┌──────────┐ ┌──────────┐
        │ frontend  │ │cheese-auth│ │    nt    │
        │React+Vite │ │ (NestJS) │ │ (Kotlin/ │
        │  :5173    │ │  :8091   │ │  Spring) │
        └───────────┘ └────┬─────┘ │  :8199   │
                            │       └────┬─────┘
                            ▼            ▼
                      ┌──────────────────────┐
                      │      Postgres         │
                      │ schema "public": auth  │
                      │ schema "mat": nt's own │
                      └──────────────────────┘
```

- **frontend/** — React + Vite. Talks to the backend only through relative
  paths (`/api/...`, `/nt/...`), never an absolute URL — see
  [CORS and how the pieces connect](#cors-and-how-the-pieces-connect).
- **backend/** ("nt") — Kotlin/Spring Boot. Owns the product's own tables
  (schema `mat`) and validates JWTs `cheese-auth` issues; does not itself
  handle registration/login.
- **cheese-auth** — a separate repository
  (`micro-agent-teams/cheese-auth`, a fork), NestJS. Owns registration,
  login, password reset, avatars — schema `public` in the same Postgres
  instance. Cloned as a sibling to this repo (see below), not part of this
  monorepo.
- **nginx** — the single public entry point; routes by path prefix.

## Prerequisites

- JDK 21
- Node.js (for the frontend and for `cheese-auth`, which is Node/NestJS)
- Docker + Docker Compose (runs `cheese-auth` and its Postgres)
- nginx

## 1. Clone cheese-auth alongside this repo

```sh
cd ..   # a directory next to this repo, not inside it
git clone https://github.com/micro-agent-teams/cheese-auth.git
cd cheese-auth
cp sample.env .env
```

Edit `.env`:

- `PORT=8091`
- `JWT_SECRET="<a real secret>"` — **must be the same value** `backend`'s
  `application.jwt-secret` uses (see step 2); this is the only thing that
  actually links the two services' authentication together.
- `CORS_ORIGINS`, `FRONTEND_BASE_URL` → your public origin, e.g.
  `http://cheese-prod-client.119net.ghg.org.cn`
- `COOKIE_BASE_URL=/`
- SMTP: `EMAIL_SMTP_HOST`, `EMAIL_SMTP_PORT`, `EMAIL_SMTP_USERNAME`,
  `EMAIL_SMTP_PASSWORD`, `EMAIL_DEFAULT_FROM` — real credentials are
  required for registration emails to actually send. Set
  `EMAIL_SMTP_SSL_ENABLE=false` for a plaintext/STARTTLS port like 25,
  `true` for an implicit-TLS port like 465.

### mat/public schema separation

`cheese-auth`'s own Postgres (started by its `docker-compose.yml`) only ever
touches schema `public` — that's enforced by `PRISMA_DATABASE_URL`'s
`?schema=public` in its `.env`. `backend` needs its own schema, `mat`, in the
**same** database (nt's `User`/`UserProfile`/`Avatar` entities are explicitly
shared, read-mostly mirrors of `cheese-auth`'s own `public.user` /
`public.user_profile` / `public.avatar` tables — everything else nt owns
lives in `mat`). Two things make this work:

1. `backend/src/main/resources/application.properties` sets
   `spring.jpa.properties.hibernate.default_schema=mat`, so any entity
   without an explicit schema defaults into `mat`.
2. The three shared entities (`User`, `UserProfile`, `Avatar`) override that
   default with an explicit `schema = "public"` on their own `@Table`
   annotation — don't remove those overrides.

`cheese-auth`'s `docker-compose.yml` doesn't expose its database port to the
host, and `backend` needs to reach it directly. Add an override so nt (and
you, for debugging) can connect, and so the `mat` schema exists before nt's
first startup:

```sh
# still inside the cheese-auth checkout
cat > docker-compose.override.yml <<'YAML'
services:
  cheese-auth:
    build: .
  database:
    ports:
      - "5433:5432"
    volumes:
      - ./.local-init:/docker-entrypoint-initdb.d
YAML
mkdir -p .local-init
echo 'CREATE SCHEMA IF NOT EXISTS mat;' > .local-init/001-mat-schema.sql

docker compose -p mat-auth up -d --build
```

`build: .` builds from this checkout rather than pulling the (possibly
stale) published image — important since `micro-agent-teams/cheese-auth`
may carry fixes not yet in a published tag.

Confirm it's up: `curl http://localhost:8091/status` → `{"code":200,...}`.

**Gotcha:** the container's entrypoint runs `prisma db push` exactly once,
gated by a flag file. That flag lives on the `cheese_backend_uploads` volume
(not the container's own layer) specifically so recreating the container
doesn't silently re-run `db push` and reset the schema — but if you ever
reset the **database** volume without also resetting the **uploads** volume,
the stale flag will skip initialization against the now-empty database.
Delete the flag (`docker run --rm -v <project>_cheese_backend_uploads:/app/uploads busybox rm -f /app/uploads/.flag_init`)
if you ever see `table "public.avatar" does not exist` at cheese-auth
startup.

## 2. Build and run backend (nt)

```sh
cd backend
./mvnw install -DskipTests
```

This regenerates `design/DB/CREATE.sql` from the current JPA entities (an
`antrun` step bound to `mvn package`) and produces
`target/cheese-0.9.2.jar`. `-DskipTests` is currently necessary: a handful
of tests still expect infrastructure (Elasticsearch/Redis) this stripped
skeleton no longer sets up.

Run it, overriding config for this environment (values baked into
`application.properties` at build time don't take effect after the jar is
built — either rebuild or override on the command line, as below):

```sh
java -jar target/cheese-0.9.2.jar --server.port=8199 \
  --spring.datasource.url=jdbc:postgresql://localhost:5433/mydb \
  --spring.datasource.username=username \
  --spring.datasource.password=mypassword \
  --application.legacy-url=http://localhost:8091 \
  --application.jwt-secret=<same JWT_SECRET as cheese-auth's .env> \
  --application.cors-origin=http://cheese-prod-client.119net.ghg.org.cn
```

`--application.legacy-url` is nt's own **server-to-server** call to
cheese-auth — always the direct address (`http://localhost:8091`), never
the public nginx path; it's unrelated to how a browser reaches either
service.

Verify: `GET /ping` (see `backend/src/main/kotlin/org/rucca/cheese/ping/`)
is the one endpoint kept from the original business modules purely as an
authenticated-chain smoke test. `curl http://localhost:8199/ping` with no
token → 401; with `Authorization: Bearer <a real JWT from cheese-auth>` →
`{"code":200,"message":"pong from user <id>"}`.

## 3. Run the frontend

```sh
cd frontend
npm install
npm run dev   # :5173
```

The dev server's own `vite.config.ts` already proxies `/api/*` →
`http://127.0.0.1:8091` (stripping `/api`) and `/nt/*` →
`http://127.0.0.1:8199` (stripping `/nt`), mirroring production nginx —
override with the `AUTH_BACKEND_URL` / `NT_BACKEND_URL` env vars if your
backends run elsewhere.

## 4. nginx (the public entry point)

```nginx
server {
    listen 80;
    server_name cheese-prod-client.119net.ghg.org.cn;

    location /api/ {
        proxy_pass http://127.0.0.1:8091/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /nt/ {
        proxy_pass http://127.0.0.1:8199/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        proxy_pass http://127.0.0.1:5173;      # swap for a static root once there's a prod build
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;                 # vite's HMR websocket
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

Enable it (Debian/Ubuntu path shown) and reload:

```sh
sudo cp this-config.conf /etc/nginx/sites-available/micro-agent-teams.conf
sudo ln -s /etc/nginx/sites-available/micro-agent-teams.conf /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default   # avoid it competing for :80
sudo nginx -t && sudo systemctl reload nginx
```

## CORS and how the pieces connect

**There is effectively no cross-origin request in this setup.** The browser
only ever talks to one origin — `http://cheese-prod-client.119net.ghg.org.cn`
— because nginx puts frontend, `cheese-auth`, and `nt` all behind that same
origin at different path prefixes. The frontend calls relative paths only
(`/api/...`, `/nt/...`), never an absolute backend URL, in both dev (vite's
proxy) and prod (nginx) — this isn't just tidiness, it's required:

- `cheese-auth` issues a **refresh token as an `httpOnly` cookie**
  (`REFRESH_TOKEN`, scoped by `COOKIE_BASE_URL`). Browsers only attach
  cookies same-origin (or cross-origin with an explicit
  `credentials: 'include'` fetch **and** a matching `Access-Control-Allow-*`
  response) — same-origin via the path-prefix routing above is what makes
  this work without any CORS negotiation at all.
- `cheese-auth`'s `CORS_*` env vars (`CORS_ORIGINS`, `CORS_METHODS`,
  `CORS_HEADERS`, `CORS_CREDENTIALS=true`) exist for the case where a client
  *does* call it cross-origin (a different frontend, a mobile app, local
  dev pointed straight at `:8091` instead of through the proxy) — set
  `CORS_ORIGINS` to that caller's real origin, not `*`, since
  `CORS_CREDENTIALS=true` requires an explicit origin per the fetch spec.
  Any frontend fetch call that isn't same-origin must also pass
  `credentials: 'include'` or the cookie won't be sent/stored regardless of
  the CORS headers.

**Auth token flow:**

- `cheese-auth` mints both tokens on login/register: a short-lived **access
  token** (JWT, returned in the response body — the frontend keeps this in
  memory only, not `localStorage`, to limit XSS blast radius) and a
  longer-lived **refresh token** (the `httpOnly` cookie above, invisible to
  frontend JS by design).
- The frontend sends the access token as `Authorization: Bearer <token>` to
  both `cheese-auth` (`/api/...`) and `nt` (`/nt/...`) — **both backends
  trust the same token** because they share `JWT_SECRET`. `nt` never talks
  to `cheese-auth` to validate a request's token; it verifies the JWT
  signature itself.
- `nt`'s `--application.legacy-url` is a separate, narrower thing: nt's own
  server-to-server calls to `cheese-auth` for data it doesn't own (e.g.
  resolving a user record) — always cheese-auth's direct address, never the
  public path.
- When the in-memory access token expires, the frontend calls
  `cheese-auth`'s refresh endpoint with `credentials: 'include'` — the
  browser attaches the `httpOnly` cookie automatically (JS never reads or
  sets it directly) — and gets a new access token back, transparently to
  the user.

## Common pitfalls

(Distilled from getting this running the first time — see the git history
of this file for the fuller original notes if these get stale.)

- **Config baked into the fat jar.** `backend`'s `application.properties`
  is read at *build* time. Editing it after `./mvnw install` has no effect
  on the already-built jar. Either rebuild, or override on the command
  line with `--spring.xxx` / `--application.xxx` flags (faster for
  iteration).
- **`prisma db push` re-running unexpectedly.** See the mat/public section
  above — the fix is the flag file living on a volume, but if you ever
  reset one of `cheese-auth`'s two volumes without the other, you can hit
  this again.
- **Stale published `cheese-auth` image.** `docker-compose.yml`'s default
  `image:` field can lag behind this fork's actual source (a feature you
  just added to `.env` handling may not exist in the last-published tag).
  Build from source (`build: .` in the override) rather than trusting the
  tag, at least until CI is reliably publishing on every push to `dev`.
- **`EMAIL_SMTP_SSL_ENABLE` must be a real boolean.** A bug already fixed
  upstream in this fork: passing the raw env string to nodemailer's
  `secure` option made it truthy for *any* non-empty value, including the
  literal string `"false"` — silently forcing TLS on a plaintext port and
  breaking real email delivery with an opaque SSL error. If you're
  reverting to an older `cheese-auth` checkout, watch for a regression here.
- **Email verification codes require a real inbox** unless you insert a
  code directly for testing:
  ```sql
  INSERT INTO user_register_request (email, code, created_at)
  VALUES ('you@ruc.edu.cn', '123456', CURRENT_TIMESTAMP);
  ```
  Only `@ruc.edu.cn` addresses currently pass `cheese-auth`'s
  `EmailRuleService` suffix check.
- **`nt`'s auth model isn't `nt`'s to reinvent per-module carelessly.**
  `RoleBasedAuthLogicService`/`RolePermissionService` and the
  `AuthorizationService`/`AuthorizationAspect`/`@Guard` framework in
  `backend/src/main/kotlin/org/rucca/cheese/auth` are intentionally kept —
  every `@RestController` method needs either `@Guard(action, resourceType)`
  or `@NoAuth()`, and every `resourceType` a `@Guard` uses needs a matching
  `Permission` entry in `RolePermissionService`'s role definition, or that
  endpoint 403s for everyone. `/ping`'s entry there is the minimal example
  to copy when adding a real one.
