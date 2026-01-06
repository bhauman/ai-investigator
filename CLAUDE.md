# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ai-investigator is a Babashka CLI tool that runs Claude, Gemini, and Codex in parallel to investigate problems, then synthesizes results with a Claude evaluator.

## Commands

```bash
# Run directly with Babashka
bb -m triage.core "your prompt"
bb -m triage.core --help

# Or use installed binary
ai-investigator "your prompt"
ai-investigator -v "verbose mode"
ai-investigator -i "investigations only, skip evaluation"

# Install via bbin
bbin install . --as ai-investigator
```

## Architecture

```
src/triage/
├── core.clj          # CLI entry point, argument parsing (clojure.tools.cli)
├── investigators.clj # Parallel LLM invocation via pmap
└── evaluator.clj     # Claude evaluator that synthesizes results
```

**Flow:**
1. `core.clj` parses CLI args, gets prompt from args or stdin
2. `investigators.clj` runs 3 LLMs in parallel via `pmap`:
   - Claude: `--permission-mode plan` (read-only)
   - Gemini: `-s` (sandbox mode)
   - Codex: `--sandbox read-only`
3. `evaluator.clj` feeds all outputs to Claude with a synthesis prompt
4. Output: Primary path, backup path, verification steps, implementation plan

**Key patterns:**
- All LLM calls use `babashka.process/shell` with `:in ""` to prevent stdin inheritance
- Each investigator returns `{:investigator :output :error :exit-code :duration-ms}`
- Default timeout is 10 minutes (600000 ms)
