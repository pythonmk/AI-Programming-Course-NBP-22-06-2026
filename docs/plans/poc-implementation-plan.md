# Implementation Plan — Hardware Service Decision Copilot (PoC)

> Orchestrated build. The orchestrator writes no code — all work is delegated to
> `be-developer` (owns `app/backend/**`), `fe-developer` (owns `app/frontend/**`), and
> `qa-engineer` (owns `e2e/**`). Disjoint directories ⇒ no file conflicts. Built on branch
> `feat/poc-build` (isolated worktree), merged to `main` at the end.

## Confirmed decisions
- Execution: autonomous end-to-end.
- Git: commit after every TDD step on `feat/poc-build` (no push); merge to `main` at the end.
- LLM: stubbed gateway for all automated tests + one real OpenRouter smoke (key in root `.env`).
- Scope: full PRD — every AC and TAC.
- Toolchain: full network + JDK 21/25 + Node 24.

## Frozen API contract (shared by BE + FE)
Base `/api`. Error envelope `{ error: { code, messagePl, fieldErrors?:[{field,messagePl}] } }`.
- `POST /api/cases` multipart(requestType, equipmentCategory, equipmentName, purchaseDate, reason?, image JPEG/PNG ≤5MB) → 201 `{sessionId, decisionCategory, firstMessageMarkdown, caseSummary{requestType,equipmentCategory,equipmentName,decisionCategory}}`; 400 validation; 502/503 LLM.
- `POST /api/sessions/{id}/messages` json `{message}` → 200 text/event-stream: `data:<token>`, optional `data:{"decisionCategory":...}`, terminal `data:[DONE]`; 404; 400 empty; 502/503.
- `GET /api/sessions/{id}` → 200 session view; 404.
- `GET /api/meta/form-options` → `{requestTypes[],equipmentCategories[]}` each `{value,labelPl}`.
- `GET /api/health` → `{status:"UP"}`.
- Enums: RequestType=COMPLAINT|RETURN; EquipmentCategory=LAPTOP,DESKTOP,MONITOR,PERIPHERALS,PC_COMPONENTS,NETWORKING,ACCESSORIES,OTHER; DecisionCategory=APPROVE|REJECT|ESCALATE (Polish labels per design/PRD).

## Phases (each step = 1 TDD cycle + 1 commit)
- **P0 scaffolding:** B0 Spring Boot scaffold + /api/health ∥ F0 Angular scaffold (zoneless/signals/Material/ngx-markdown/proxy).
- **P1:** B1 domain model · B2 meta endpoint + error envelope ∥ F1 NBP design system + header · F2 AppStateStore + models.
- **P2:** B3 EligibilityService · B4 session store TTL · B5 PolicyProvider · B6 ImageCompressor ∥ F3 CaseApiService · F4 IntakeFormComponent.
- **P3:** B7 LLM integration (clients/PromptBuilder/OutputParser) · B8 analyze+decide · B9 CaseService · B10 POST /api/cases ∥ F5 ChatStreamService · F6 ChatComponent.
- **P4:** B11 ChatService streaming · B12 chat SSE endpoint + GET session ∥ F7 routing/wiring.
- **P5 (QA):** Q0 Playwright + LLM stub · Q1 complaint→Approve · Q2 return Approve/Reject-out-of-window · Q3 escalate + no Reject→Approve · Q4 validation/no-LLM · Q5 SSE render + responsive 360px + Polish.
- **P6:** Q6 one real OpenRouter smoke · Final: orchestrator boots stack, runs all suites, reports; merge to main.

## Critical path
B0→B1→{B3,B4,B5,B6}→B7→B8→B9→B10→(B11→B12)→Q0→Q1–Q5→Q6. FE track overlaps entirely; QA gates the end.
