# AFTERLIGHT Signal

AFTERLIGHT Signal is the NeoForge 1.21.1 companion mod for the AFTERLIGHT modpack. It provides the Signal Reliquary title screen, the physical ECHO item, the guided ECHO quest interface, and server-authoritative item recovery.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.248
- Java 21
- FTB Quests 2101.1.30
- FTB Library 2101.1.35
- FTB Teams 2101.1.10
- Architectury 13.0.11 at runtime

## Usage and Recovery

The server issues one ECHO item after a player's first login when the inventory has a free slot. Hold the issued ECHO in either hand and use it to request the ECHO screen from the server. The screen reads its route from `config/afterlight/echo_route.json`. If that file is absent or invalid, the client opens the signal-unavailable screen instead of guessing a route.

Run `/echo recover` as the affected player to issue a replacement. Recovery requires a free inventory slot and increments the player's ECHO generation. Every older ECHO for that player becomes superseded and cannot open the screen. A foreign player's ECHO is rejected. Operators with permission level 2 can inspect the stored bond with `/echo inspect <player>`.

The Signal Reliquary replaces the vanilla title screen by default. Set `replaceTitleScreen=false` in the generated AFTERLIGHT client configuration to restore the vanilla screen. Native multiplayer bans and disabled-online state remain enforced by the replacement screen.

### Pack Version Identity

Public Delivery Task 2 must ship `config/afterlight/pack_version.txt` as a Packwiz-managed UTF-8 file. Its single trimmed line must exactly match `pack.toml`'s `version` value for that pack release. The companion reads only this pack-owned runtime file for the title's `PACK VERSION` field and displays `UNAVAILABLE` when the file is absent, blank, malformed, or unreadable.

## Developer Build

This repository deliberately has no Gradle wrapper. Install Gradle 9.2.1 and use Temurin 21.0.12. Ordinary developer builds may use dirty tracked files and regular untracked source while iterating:

```sh
gradle clean test runGameTestServer build -PafterlightLockContext=macos --no-daemon
```

The lock context is mandatory and selects the committed dependency graph for the execution platform. These examples use `macos`. On Linux, replace `macos` with `linux`. Unsupported or omitted contexts fail before dependency resolution.

Regenerate `gradle/dependency-locks/linux.lockfile` only on Linux. Never regenerate it on macOS. Platform-native runtime membership is part of the authenticated lock contract.

The build still rejects symlinked and hardlinked source inputs because Gradle must never consume an aliased source tree. This source gate requires POSIX permissions and the `unix:nlink` file attribute, so developer and release builds are supported on macOS and Linux POSIX filesystems. Native Windows filesystems are not supported by the source gate. Use Linux CI or WSL backed by a POSIX filesystem instead.

### Dedicated Relay Migration Acceptance

Run the restart-aware custom-dimension acceptance with:

```sh
./tools/run-dedicated-migration-acceptance.sh
```

The focused GameTest keeps migration mechanics fast and deterministic inside the vanilla GameTest level. GameTestServer does not load custom dimensions, so proof of the real `afterlight:far_relay` route belongs in this isolated dedicated acceptance. The runner creates a fresh pre-v2 world, stops it, restarts the same world, invokes the public production Gate route from the Overworld, validates migration and idempotence, and invokes the production return route. Both processes use one fresh challenge and authenticated phase markers. Set `AFTERLIGHT_LOCK_CONTEXT=linux` only when running the dedicated acceptance on Linux.

## Release Build

Release artifacts use the exact source-bound command:

```sh
gradle clean test runGameTestServer build verifyReleaseJar -PafterlightRelease=true -PafterlightLockContext=macos --no-daemon --no-build-cache --rerun-tasks
```

The release property must be exactly `true`. Before any release Java or resource task runs, the gate rejects dirty tracked source, release-relevant untracked source, symlinks, hardlink aliases, unsupported Git entries, source digest mismatches, private-key and token markers in any regular file, and U+2014 in any valid UTF-8 regular file. It computes a domain-separated SHA-256 over each included file's Git mode, object type, path, length, and content. The committed digest is computed independently from Git objects.

After verification, the gate materializes an owner-only, read-only staging tree at `.gradle/release-source` directly from validated HEAD Git blobs. It binds that snapshot to the exact commit and committed source digest captured when Gradle configured the build, then records both values in `.gradle/release-source/.afterlight-release-stage-manifest`. Release main and test Java compilation and resource processing consume only that staged snapshot, and generated provenance reads its identity from the authenticated manifest. The gate rejects a HEAD change before or after staging. A post-build audit revalidates current HEAD, staged bytes, the manifest identity, and the mutable working tree before the release contract can pass. Build, cache, log, and runtime output directories are excluded, so generated artifacts and the staging tree never enter the source digest. Ordinary developer builds continue to use working source paths and remain usable with dirty regular files.

The JAR contract verifies the exact reviewed inventory and metadata, fixed timestamps, stable entry order, source provenance, all-entry secret and punctuation audits, parsed class-reference isolation from client-only namespaces, and byte-identical independent archive construction. CI checks out the exact source SHA twice into separate directories, assigns separate Gradle user homes, runs the full clean release command independently in each checkout, and then compares `afterlight-signal-0.2.0+1.21.1.jar` byte for byte.

## Gate Recovery

- Dirty or untracked source: inspect `git status --short --untracked-files=all`, then commit, stash, restore, or remove the reported path.
- Symlink: replace the link with a regular file containing the intended bytes, then commit it.
- Hardlink: copy the file through a new temporary file so the tracked path has its own inode, then rerun the gate.
- Digest mismatch: clear any hidden index flags with `git update-index --no-assume-unchanged <path>` or `git update-index --no-skip-worktree <path>`, restore the path, and rerun from a clean tree.
- Secret marker or U+2014: remove the reported content rather than suppressing the audit.
- Unsupported platform metadata: rerun on macOS or Linux with a POSIX filesystem. Native Windows cannot satisfy the hardlink and mode checks.
- Staging failure: verify that `.gradle` and `.gradle/release-source` are real repository-local directories, remove `.gradle/release-source` if necessary, and rerun the exact release command from a clean committed tree. Never point either path at a symbolic link.
- Missing or changed provenance: do not edit the stage manifest or generated files under `build/`. Restore the reviewed commit as HEAD, remove `.gradle/release-source`, and rerun the exact release command from a clean committed tree.

The release artifact is `build/libs/afterlight-signal-0.2.0+1.21.1.jar`. Generate integration checksums only after all gates pass:

```sh
shasum -a 256 build/libs/afterlight-signal-0.2.0+1.21.1.jar
shasum -a 512 build/libs/afterlight-signal-0.2.0+1.21.1.jar
```

Tags and published assets are immutable. Never reuse a release tag or replace a published JAR.
