# Weekly upstream sync

This repository is a fork of [`microsoft/copilot-for-eclipse`](https://github.com/microsoft/copilot-for-eclipse) customized for MuleSoft Anypoint Studio.

## Branches

| Branch | Role |
|--------|------|
| `main` | Tracks upstream `microsoft/copilot-for-eclipse` `main` |
| `sync` | Anypoint Studio line (`anypoint-studio/main`) plus merged upstream changes from `main` |
| `anypoint-studio/main` | Default release / product branch |

## Automation

GitHub Actions workflow [`.github/workflows/weekly-upstream-sync.yml`](../.github/workflows/weekly-upstream-sync.yml) runs **every Monday at 09:00 UTC** (and on demand via **Actions → Weekly Upstream Sync → Run workflow**).

### Flow

1. **Pull parent → `main`**  
   Fetches `microsoft/copilot-for-eclipse` `main` and merges into this repo’s `main`, then pushes.

2. **Merge `main` → `sync`**  
   Attempts a clean merge. If it succeeds, pushes `sync`.

3. **Cursor resolves conflicts**  
   If the merge has conflicts (or you force Cursor via workflow input), the workflow launches a [Cursor Cloud Agent](https://cursor.com/docs/cloud-agent/api/endpoints) with `workOnCurrentBranch: true` on `sync` to resolve conflicts, commit, push, and open a PR.

### Required secret

Add repository secret **`CURSOR_API_KEY`** (Cursor Dashboard → API Keys). Without it, clean merges still work; conflict resolution via Cursor will fail until the secret is set.

### Conflict policy (used by Cursor)

- Keep Anypoint Studio / MuleSoft customizations (branding, MCP/Anypoint, `@console` / `@transform`, workspace context, anypoint bundles, release `build.yml`).
- Take upstream improvements from `main` when they do not remove those customizations.
- Combine both sides when the same area changed.
- Never leave conflict markers.
