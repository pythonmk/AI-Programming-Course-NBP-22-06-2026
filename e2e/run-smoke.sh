#!/bin/bash
# Run the real OpenRouter smoke test.
# Requires OPENROUTER_API_KEY to be set in the environment or ../.env file.
#
# Usage:
#   ./run-smoke.sh
#
# The test is skipped (not failed) if OPENROUTER_API_KEY is not set.
set -euo pipefail

# Load .env from repo root if present (silently ignore if missing)
set -a
source ../.env 2>/dev/null || true
set +a

cd ../app/backend && ./mvnw test -Dgroups=smoke
