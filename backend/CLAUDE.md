# Backend conventions (cheese-backend-nt lineage)

This backend is forked from **cheese-backend-nt** (Kotlin / Spring Boot 3.4 / Java 21 /
Maven). Many conventions below are *not* the most "standard" Spring practice — they are
what nt does and what fits our setup. **Follow them for consistency**; the reference repo
lives at `~/work/ref-study/cheese-backend-nt`.

Cardinal rule: **if the integration tests are green, the covered code is correct.** We do
not debug by "start the jar and curl". See _Testing_.

---

## 1. OpenAPI-first codegen

- Change `design/API/MAT-API.yml` **first**, then build regenerates the API interfaces
  (`org.rucca.cheese.api.*Api`) and DTOs (`org.rucca.cheese.model.*DTO`). **Never hand-write
  or hand-edit generated interfaces / DTOs.**
- The generator writes **into `src/main/kotlin/.../api` and `.../model`** (not `target/`) and
  **does not delete stale files**. When you remove a path/schema from the YAML, manually
  `rm` the orphaned generated `.kt` file(s).
- Same trap one level down: `target/classes` keeps `.class` files for sources you moved or
  deleted, and the compiler happily resolves against them — a moved package produces
  impossible errors like "actual type is `team.membership.TeamMemberRole` but
  `team.TeamMemberRole` was expected". **After moving/renaming/deleting anything, `./mvnw
  clean`.** (This has bitten us twice: once here, once as a stale `TeamServiceTest.class`
  failing tests whose source no longer existed.)
- Controllers implement the generated `XxxApi` interface. Match the generated method
  signature exactly (compile tells you if it drifted — e.g. `pageSize: Int` not `Int?` when
  the param has a default).
- **One module = one path prefix = one controller.** A module's package name is the singular
  first path segment (`/chat` → `chat/ChatController`, `/team` → `team/TeamController`), and
  that controller's methods are **only** implementations of its generated `XxxApi` — nothing
  else. The generator derives the Api class from the **first path segment, not the tag**, so
  the path drives everything: put an operation under `/team/...` and it lands in `TeamApi`.
- A module may split its *implementation* into subpackages by feature — `team/membership`
  (team + members) and `team/documents` (the git tree) each own their entities and services —
  but the single `TeamController` in `team/` stays the only entry point.
- Hand-written `@RestController`s are **not** an accepted escape hatch: everything expressible
  as HTTP goes in the YAML. Only what OpenAPI genuinely cannot describe (the connector's
  WebSocket endpoints) is registered outside it, and as handlers, not controllers.

## 2. Entities & persistence

- **Class name has no `Entity` suffix**: file `TeamEntity.kt` defines `class Team`; the
  repository interface lives **in the same file**. (nt style.)
- Extend `BaseEntity` — it provides `id` (SEQUENCE), `createdAt`, `updatedAt`, `deletedAt`.
  Use `IdType` (= `Long`) in signatures, not raw `Long`.
- Soft delete: put `@SQLRestriction("deleted_at IS NULL")` on the entity. Then repo methods
  **must NOT** carry `...AndDeletedAtIsNull` suffixes — the restriction filters globally.
  Single lookups return `Optional<>`.
- Fields are nullable-with-default even for NOT NULL columns (`var name: String? = null`),
  the JPA-no-arg way; assert `!!` in mappers.
- **Enums are stored as strings**: `@Enumerated(EnumType.STRING)` + a DB `CHECK` constraint.
  Never omit it — the JPA default is ORDINAL (`0/1/2`), which violates the string CHECK and
  500s on the first insert. (This bit us.) If you reintroduce a "kind"/"type" column, make
  it a string enum too — no raw `integer` / free-string.
- **Schemas — `mat` vs `public`**: our own tables live in `mat`
  (`spring.jpa.properties.hibernate.default_schema=mat`). The three tables shared with
  cheese-auth (`User`, `UserProfile`, `Avatar`) are pinned to `public` and treated as
  read-only in prod. A shared entity needs **both** `@Table(schema = "public")` **and**
  `@SequenceGenerator(schema = "public", ...)` — the sequence does **not** inherit the
  table's schema, and forgetting it makes Hibernate look for `mat.user_id_seq`.

## 3. Service / controller / DTO

- **Services return DTOs; controllers are thin.** Do not map entities→DTOs inside controllers.
- DTO mapping is a **top-level extension function in the service file**:
  `fun Team.toTeamDTO() = TeamDTO(...)`. Enum conversions: `fun TeamMemberRole.convert() = ...`.
- **Auth registration goes in the controller's `@PostConstruct`** (`ownerIds.register(...)` +
  `customAuthLogics.register(...)`), not a separate initializer bean.
- **Errors: throw `BaseError` subclasses** (`NotFoundError(type, id)`, `BadRequestError(msg)`).
  The global handler maps `BaseError` → its status; **everything else → 500**. Do **not** use
  `@ResponseStatus` on a plain exception (the catch-all `@ExceptionHandler(Exception)`
  intercepts first and it becomes 500). A git-layer not-found should therefore extend
  `BaseError(NOT_FOUND, ...)`.
- **Success responses are raw DTOs** (no `{data}` envelope). Errors serialize as
  `{code, message, error}` via `BaseError`.
- Every Kotlin file starts with the `/* Description: ... Author(s): ... */` header block.
- Code and comments are **English only**. Formatting is enforced by spotless (ktfmt); the
  build applies it.

## 4. Authorization (Casbin-style)

- Guard every controller method with `@Guard(action, resourceType)`, and annotate the id
  path param with `@ResourceId`.
- **Resource types are module-prefixed**: `team`, `team_document`, `chat_thread`,
  `chat_message`. Everything under the team module starts with `team`.
- `RolePermissionService` holds the role→permission matrix (role `standard-user`).
- **Give distinct actions distinct names** so one permission can't accidentally satisfy
  another. `listTeams` uses `enumerate` (open to any user) while `getTeam` uses `read`
  (member-gated) — sharing `read` let a stranger read any team (auth bypass). Same pattern
  for chat (`enumerate` vs `read`).
- Custom predicates (`is_team_member`, `is_team_admin`, `is_thread_member`, …) are registered
  by name. The built-in **`owned`** predicate resolves ownership via the `ownerIds` provider —
  **the `ownerIds` resourceType must exactly match the `@Guard` resourceType.** Registering
  the thread owner under `"thread"` while guarding `"chat_thread"` silently 403'd the owner.

## 5. Pagination — reuse nt's `PageHelper` / `PageDTO`

- Do **not** write your own pager. Use `common/helper/PageHelper` and the generated `PageDTO`
  (schema `Page` in the YAML).
- Cursor pagination: query params **`page_start`** (id of the first item) + **`page_size`**
  (default 20). Standardize on these names (we migrated chat's old `after` to them).
- List endpoints return a **`{items, page}` wrapper** (`ListTeamsResponse`,
  `ListThreadsResponse`, `ListMessagesResponse`), not a bare array.
- `PageHelper.pageFromAll(all, pageStart, pageSize, { it.id!! }, null)` is the
  load-all-then-slice variant — fine for our scale.

## 6. Documents = git, no DB table

- There is **no document table**. Each team owns a bare git repo at
  `${application.git-repo-base}/{teamId}.git` (`GitService`).
- Documents are a **feature of the team module**, not a module of their own: the API is
  `/team/{id}/document` (so it generates into `TeamApi` and is served by `TeamController`),
  and the implementation lives in `team/documents/`.
- Prefer **one consolidated endpoint with query flags** over many endpoints.
  `/team/{id}/document` GET serves root/tree/file/history/diff via `path` / `recursive` /
  `content` / `history` / `diff`; PUT/PATCH/DELETE do write/move/delete. Future views = new
  flags, not new endpoints.
- **Reads pull blobs straight from the bare repo** (no clone). **Writes** clone a throwaway
  worktree, commit, push back.
- Paths are **logical git paths**: repo-relative, `/`-separated, no leading `/`, no `..`.
  Validate at the controller (→ 400 `BadRequestError`) and again in `GitService` (defense in
  depth). Reads are inherently traversal-safe because they use the git object model.

## 7. Testing — integration only

- **No mock-based unit tests.** Stubbing repos proves nothing; it only produces fake coverage.
  Deleted `TeamServiceTest` (mockk) for exactly this reason.
- Write **integration tests in nt's style**: `@SpringBootTest @AutoConfigureMockMvc
  @TestInstance(PER_CLASS) @TestMethodOrder(OrderAnnotation)`, constructor-inject `MockMvc` +
  `UserCreatorService`, `@BeforeAll` creates real users and logs in **through the real
  cheese-auth service**, ordered tests share state, assert with `jsonPath`. See
  `src/test/kotlin/org/rucca/cheese/api/{TeamTest,DocumentTest,ThreadTest}.kt`.
- **Depending on external services (Postgres, cheese-auth) is the point of integration
  testing, not a smell.** `UserCreatorService` inserts a real user row and logs in via
  `application.legacy-url`.
- `GitServiceTest` is the one "unit-ish" test that stays — it uses **real JGit** on temp
  dirs, no mocks, so it's integration-style already.
- Every new endpoint / behavior / bug fix gets an integration assertion. These tests have
  caught real bugs mocks never would: the enumerate/read auth bypass, the dropped
  `@Enumerated`, the 404/400-vs-500 error contract, the `owned` resourceType mismatch.
- Tests must be **hermetic**: inject config via `SPRING_APPLICATION_JSON`, and point
  `application.git-repo-base` at a fresh temp dir per run (`mktemp -d`) so git state never
  leaks across runs.

Run them (needs Postgres:5433 + cheese-auth:8091 up):

```bash
REPO_BASE=$(mktemp -d)
export SPRING_APPLICATION_JSON='{
  "spring.datasource.url":"jdbc:postgresql://localhost:5433/mydb",
  "spring.datasource.username":"username",
  "spring.datasource.password":"<db-pw>",
  "spring.jpa.hibernate.ddl-auto":"update",
  "application.jwt-secret":"<shared-with-cheese-auth>",
  "application.legacy-url":"http://localhost:8091",
  "application.git-repo-base":"'"$REPO_BASE"'"
}'
./mvnw test -Dtest=GitServiceTest,TeamTest,DocumentTest,ThreadTest
```

## 8. Schema changes → drop the test DB

- `ddl-auto=update` **never drops** columns or constraints, so a stale test DB drifts from the
  entities and hides/creates bugs. After changing any entity, reset:
  `DROP SCHEMA mat CASCADE; CREATE SCHEMA mat;` and let Hibernate recreate it.
- Documents live on disk keyed by team id, which **resets when you drop the DB** — so also use
  a fresh `application.git-repo-base` (the tests already do via `mktemp -d`). Otherwise a new
  team reuses an old id whose git repo still exists and inherits stale files.

## 9. Secrets & runtime config

- `application.properties` holds **only generic local-dev/CI defaults**. Real values — DB
  password, the jwt-secret that must match cheese-auth, `application.legacy-url` — are injected
  at runtime via CLI `--spring.*` args or `SPRING_APPLICATION_JSON`. **Never commit real
  secrets** (we've had a real leak; don't repeat it).
- Building the jar (`mvnw install`) runs an antrun step that boots the app to regenerate
  `design/DB/CREATE.sql`, so it needs a reachable DB configured.

## 10. Commits (solo repo)

- **Commit only code you have personally verified** (green integration tests). Do not let
  bad-then-deleted churn land in history — with one developer, keep the log clean.
- Use `feat(module): …` style messages.
