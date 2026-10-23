# Decisions

Short architecture decision records. Each one states the context, the decision, and what it costs.

## ADR-001 — Keep `applicationId io.freemytv`

**Context.** The fork was already installed on three TVs as `io.freemytv`. Renaming the id would
install a second launcher, leave the old one holding the HOME role, and require a second risky switch.

**Decision.** The public product is called Mashrabiya; the application id stays `io.freemytv`.

**Consequence.** `adb install -r` updates in place and the key keeps working. The package name looks
legacy; changing it later needs a documented migration, not a rename.

## ADR-002 — Keep the flat Java package

**Context.** 54 files and ~12k lines live in `app/src/io/freemytv/`. Splitting them into `ui/`,
`data/`, `mode/` would touch every file and every import.

**Decision.** Keep the flat package. Organise the repository (tooling, docs, profiles, tests) and
document the architecture instead.

**Consequence.** Zero regression risk now. The architecture map is the navigation aid; a split can
happen later as its own change with its own tests.

## ADR-003 — No in-app updater

**Context.** A self-updater would need `REQUEST_INSTALL_PACKAGES`, a new network host, a sixth
permission, and a signing story on the user's TV.

**Decision.** Updates are agent/source-driven (`./tv update`). GitHub Releases are convenience
binaries, not a silent update channel.

**Consequence.** The 5-permission and 7-host invariants stay intact. Updating is a deliberate act.

## ADR-004 — Delete the upstream website

**Context.** `docs/` contained upstream's marketing site for freemytv.io and its benchmarks.

**Decision.** Remove it and use `docs/` for this project's documentation. Keep the religious source
manifest under `docs/religious/`.

**Consequence.** No false claims and no second site to maintain. Attribution lives in `NOTICE` and
the git history.

## ADR-005 — Switching the home screen is guided, never silent

**Context.** The switch disables three system packages. The app needs the TV's loopback ADB, and the
user must trust the computer.

**Decision.** `./tv switch` opens the app's settings, tells the user what to tap, and polls until the
HOME role changes or times out. There is no `--force`.

**Consequence.** One human tap per TV. In exchange, no agent can silently take over a stranger's
home screen, and the app's own Original path remains the source of truth.

## ADR-006 — Source build first

**Context.** Shipping a pre-signed APK means users trust a binary; building locally means they need
a toolchain.

**Decision.** The documented path is `git clone` + `./tv build`. Releases are optional convenience
builds, clearly labelled, signed by the project key.

**Consequence.** No key distribution and no third-party binary trust. The agent installs the
toolchain once; `./tv doctor` makes that deterministic.
