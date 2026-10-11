# Local tester builds (never committed)

The tester machinery — the part that can share a tester's name and period
data — lives only on this machine, on the `beta-local` branch, which is
never pushed. `main` contains the clean app with zero transmission
capability. The guardrails:

- `.git/info/exclude` hides the local-only paths from git status/add.
- `remote.origin.push` is pinned to `main`, so a bare `git push` from any
  branch can only ever send `main`. (Check: `git config --get
  remote.origin.push`.)
- `beta-local` has no upstream, so a bare `git push` on it just errors out.
- `.git/hooks/pre-push` refuses non-main pushes as a second layer wherever
  git executes hooks. Note: Git for Windows silently skips hook files
  without a POSIX executable bit, which PowerShell cannot set — so on this
  machine the refspec above is the real guard; the hook is verified working
  and protects checkouts on other systems.

## What's local-only

- `composeApp/src/beta/` — sharing backend, bootstrap, beta
  `google-services.json`, beta manifest.
- `domain/telemetry/`, `TelemetryStep.kt`, `TelemetrySettingsBlock.kt`.
- The sharing wiring inside shared files (restored from backup onto
  `beta-local`; see below).

## Building for the 2 tester devices

1. `git checkout beta-local`
2. Drop the real tester `google-services.json` into `composeApp/src/beta/`
   (the placeholder on disk compiles but sends nowhere — never commit
   either file).
3. `./gradlew :composeApp:assembleBetaDebug` (or `assembleBetaRelease`).
4. Sideload the APK from `composeApp/build/outputs/apk/beta/`.
5. `git checkout main` when done.

## Rules

- Push only with `git push origin main`.
- Never run `git add -A` / `git commit -a` on `main` without checking
  `git status` first.
- Never run `git clean` — it would delete the ignored local-only files.
- Never push the `beta-local` branch. Ever.
- A code backup of the tester tree also lives in `Documents\Ritu-backup-*.zip`
  in case the local branch is ever lost.
