# ai-investigator

A Babashka CLI tool that runs Claude, Gemini, and Codex in parallel to investigate problems, then synthesizes their findings with a Claude evaluator.

## How it works

1. **Parallel Investigation**: Three AI coding assistants analyze your problem simultaneously in read-only mode:
   - **Claude** (`--permission-mode plan`)
   - **Gemini** (`-s` sandbox mode)
   - **Codex** (`--sandbox read-only`)

2. **Synthesis**: A Claude evaluator reviews all findings (anonymized as Alice, Bob, Carol to avoid bias) and produces:
   - **Primary Path**: Best recommended approach with rationale
   - **Backup Path**: Alternative if the primary approach fails
   - **Implementation Plan**: Step-by-step actionable instructions

## Installation

### Prerequisites

- [Babashka](https://github.com/babashka/babashka) installed
- [bbin](https://github.com/babashka/bbin) installed (for global install)
- CLI tools available on PATH: `claude`, `gemini`, `codex`

### Install via bbin

```bash
# From GitHub
bbin install https://github.com/bhauman/ai-investigator.git

# Or from local clone
bbin install . --as ai-investigator
```

## Usage

```bash
# Basic usage
ai-investigator "Why is the API returning 500 errors on /users?"

# Verbose mode - shows investigation progress
ai-investigator -v "Analyze the authentication flow"

# Investigations only - skip the evaluation step
ai-investigator -i "Search for memory leaks"

# From stdin
echo "Fix the login bug" | ai-investigator

# Heredoc for multi-line prompts
ai-investigator <<EOF
The user signup flow is broken.
After submitting the form, users see a blank page.
Check the frontend validation and API endpoint.
EOF

# Custom timeouts
ai-investigator -t 300000 --eval-timeout 120000 "Debug the cache issue"
```

### Options

| Option | Description | Default |
|--------|-------------|---------|
| `-t, --timeout MS` | Investigation timeout (milliseconds) | 600000 (10 min) |
| `-e, --eval-timeout MS` | Evaluation timeout (milliseconds) | 600000 (10 min) |
| `-v, --verbose` | Print progress messages | false |
| `-i, --investigations-only` | Run investigations only, skip evaluation | false |
| `-h, --help` | Show help message | - |

## Architecture

```
src/triage/
├── core.clj          # CLI entry point, argument parsing
├── investigators.clj # Parallel LLM invocation via pmap
└── evaluator.clj     # Claude evaluator that synthesizes results
```

## Development

```bash
# Run directly with Babashka
bb -m triage.core "your prompt"

# With options
bb -m triage.core -v "your prompt"
```

## License

MIT
