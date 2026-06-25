# AI w Programowaniu: Od Pomysłu do MVP
### Szkolenie JSystems — kurs dedykowany dla NBP (Narodowy Bank Polski), 22.06.2026

---

**Prowadzący:** [Łukasz Matuszewski](https://devpowers.com/) | [JSystems](https://jsystems.pl)

**Opis szkolenia:** [AI dla Programistów — Od Pomysłu do MVP](https://jsystems.pl/szkolenia-ai;ai_dla_programistow_od_pomyslu_do_mvp.szczegoly)

> **Kurs dedykowany:** to zamknięte szkolenie przygotowane dla zespołu **NBP (Narodowy Bank Polski)** — 12 uczestników, start **22.06.2026**.
>
> To jest jedynie **bazowe repozytorium startowe** kursu. Domena, tech stack i architektura zostaną ustalone live z grupą.

---

## O repozytorium

To repozytorium zawiera materiały do 5-dniowego kursu **AI w Programowaniu** prowadzonego przez JSystems, w wersji dedykowanej dla **NBP**. Kurs skupia się na workflow pracy z agentami AI (Claude Code, OpenAI Codex CLI), a nie na jednym konkretnym narzędziu.

Uczestnicy mogą pracować w swoim preferowanym języku programowania (Java, Python, C#, Go, Rust i inne). Stack projektu MVP został ustalony w ADR (`docs/ADR/`): **backend w Javie 21 / Spring Boot** (LLM przez OpenRouter z użyciem `openai-java`) oraz **frontend w Angular 22 + Angular Material**.

### Projekt kursu

Multimodalna aplikacja AI — na przykład agent weryfikujący usterki, zwroty i reklamacje sprzętu elektronicznego. Konkretny projekt i tech stack ustalane są live z grupą po przez proces: research → PRD → ADR → implementacja z agentami.

---

## Materiały kursu

Główne notatki i zasoby znajdziesz w folderze `/course-materials`:

- 📓 [**Course Notes — AI in Programming**](course-materials/Course%20Notes%20-%20AI%20in%20Programming.md) — główne notatki: trendy, narzędzia, benchmarki, metodologie agentic coding, best practices.
- 📅 [**Agenda kursu**](course-materials/course-agenda.md) — program 5-dniowego szkolenia.
- 📜 Skrypty z poszczególnych dni (`course-materials/03-2026/day-*-full-script.md`)
- 🔬 Materiały badawcze (`course-materials/Research/`)
- 💡 Przykłady promptów (`course-materials/Prompt examples/`)
- 🎓 Technika Ralph Wiggum Bash Loop (`course-materials/how-to-ralph-wiggum/`)

---

## Struktura repozytorium

```
app/                 Aplikacja budowana podczas kursu (start: pusty scaffold)
assets/              Design tokens, logo, favicon (dodawane w trakcie kursu)
docs/                PRD, ADR, design system (tworzone podczas kursu)
course-materials/    Notatki, skrypty, przykłady, badania
examples/            Przykładowe konfiguracje agentów (Java/Spring Boot)
```

---

## Technologie

Stack projektu MVP został wybrany w ADR (`docs/ADR/`):

- **Backend:** Java 21, Spring Boot 3.5.x (Spring MVC + SSE), `openai-java` przez OpenRouter (model `anthropic/claude-sonnet-4.6`), Thumbnailator
- **Frontend:** Angular 22, Angular Material 22, ngx-markdown
- **Sesje:** magazyn w pamięci (SQLite planowane na później)

Uczestnicy mogą realizować własne ćwiczenia w dowolnym stacku (Java, Python, C#, Go, Rust i inne). Przykładowe konfiguracje agentów Java/Spring Boot: `course-materials/agent-configs/`.

---

## Narzędzia AI

Główny agent używany na kursu: **Claude Code** lub **OpenAI Codex CLI** (wybór zależy od preferencji grupy). Omawiane koncepcje są transferowalne na Gemini CLI, OpenCode, Cursor, Zed, Junie, Copilot i inne.

---

## Konfiguracja środowiska

Szczegóły w [`.env.example`](.env.example).

Wymagane:
- Klucz API (OpenAI, OpenRouter, lub inny provider)
- Agent CLI (Claude Code lub Codex)
- Git

---

## Kontakt

- **JSystems:** [jsystems.pl](https://jsystems.pl)
- **Prowadzący:** [Łukasz Matuszewski](https://devpowers.com/)
