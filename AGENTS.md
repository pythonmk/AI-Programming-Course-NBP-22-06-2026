# Repository Guidelines

## Project

This is a **course project** for the "AI dla programistów — od pomysłu do MVP" training by JSystems — a **dedicated (closed) course for NBP (Narodowy Bank Polski)**, 12 participants, starting **2026-06-22**. The app is a multimodal AI assistant built live during the course. The domain, tech stack, and architecture are decided by the group through a structured process: research → PRD → ADR → implementation with agents.

This is only the **base starting repository** for the course; concrete decisions are made live with the group.

**Project stack (decided in `docs/ADR/`):**
- **Backend:** Java 21 + Spring Boot 3.5.x (Spring MVC + `SseEmitter`, virtual threads), `openai-java` `4.41.0` calling LLMs via **OpenRouter** (Chat Completions API; model `anthropic/claude-sonnet-4.6`), Thumbnailator for image compression. Maven via the Maven Wrapper. Under `app/backend`.
- **Frontend:** Angular 22 + Angular Material 22 + ngx-markdown 22 (standalone, signals, zoneless); SSE consumed via `fetch`+`ReadableStream`. Under `app/frontend`.
- **Persistence:** in-memory session store in the MVP (SQLite planned).

Participants may still work in any language for their own exercises (Java, Python, C#, Go, Rust, etc.), but the course MVP follows the stack above.

All user-facing text in **Polish**.

**Key docs** (created during the course — load only when in doubt):
- `docs/PRD-Product-Requirements-Document.md` — product requirements and acceptance criteria
- `docs/ADR/` — Architecture Decision Records
- `docs/design-guidelines.md` — design system and tokens

---

## Repository Layout

```
app/                 Application built during the course (start: empty scaffold)
assets/              Design tokens, logo, favicon
docs/                PRD, ADR, design system
course-materials/    Notes, scripts, examples, research
```

---

## Agent Workflow

### Before Starting Any Task
1. Read the relevant PRD and ADR files for the affected area.
2. Define the expected behavior from the specification before writing or changing any code.

### TDD Rules
For every feature and bug fix:
1. Start from the specification, not the existing implementation.
2. Write or extend tests **before** production code.
3. Run the new tests and confirm they fail for the expected reason.
4. Implement the minimum code needed to make them pass.
5. Run the full verification suite for the changed scope.
6. Refactor only while tests stay green.

If the area has no suitable test infrastructure yet, add it as part of the task — do not silently skip tests.

### Verification (required before every commit)

Run the commands appropriate for the changed scope.

Backend (`app/backend`):
```bash
./mvnw test                  # JUnit 5 / Mockito — all pass
./mvnw -DskipTests package   # build succeeds
```

Frontend (`app/frontend`):
```bash
npx ng test --watch=false    # Karma/Jasmine — all pass
npx ng build                 # production build succeeds
```

Verify only the scope relevant to your change. If the change affects runtime behavior, confirm the app starts correctly.

**Test Strategy:**
| Type | Mocks | Who |
|---|---|---|
| Unit | All deps | be/fe-dev |
| Integration | Only external LLM API | be-dev |
| E2E | NOTHING (real stack) | qa-engineer |

**Verification:** Always start the app before committing. Tests passing ≠ app working.

**Env Vars:** See `.env.example` (OPENROUTER_API_KEY or OPENAI_API_KEY required)

### Commit Rules
- Commit only after verification passes and the changed scope is in a working state.
- Keep commits focused: one logical change per commit.
- Format: `Area: short summary` (e.g. `Backend:`, `Frontend:`, `Docs:`)
- Do **not** push to remote unless the user explicitly asks.

### Completion Criteria
A task is complete only when:
- Implementation matches the relevant PRD, ADR, and design guidance
- Tests were written first and pass honestly
- Verification for the changed scope passed with no errors or warnings
- The commit message is focused and the repository is in a consistent, reviewable state

---

## Context7 MCP Library IDs

Common libraries (resolve via `resolve-library-id` if the ID changes):

| Library | Context7 ID |
|---|---|
| Spring Boot | `/spring-projects/spring-boot` |
| openai-java | `/openai/openai-java` |
| Jackson | `/fasterxml/jackson` |
| Thumbnailator | `/coobird/thumbnailator` |
| Angular | `/angular/angular` |
| Angular Material / CDK | `/angular/components` |
| ngx-markdown | `/jfcere/ngx-markdown` |
