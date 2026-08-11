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

## Developer Build

This repository deliberately has no Gradle wrapper. Install Gradle 9.2.1 and use Temurin 21.0.12. Ordinary developer builds may use dirty tracked files and regular untracked source while iterating:

```sh
gradle clean test runGameTestServer build --no-daemon
```

The build still rejects symlinked and hardlinked source inputs because Gradle must never consume an aliased source tree.

## Release Build

Release artifacts use the exact source-bound command:

```sh
gradle clean test runGameTestServer build verifyReleaseJar -PafterlightRelease=true --no-daemon --no-build-cache --rerun-tasks
```

The release property must be exactly `true`. The gate rejects dirty tracked source, release-relevant untracked source, symlinks, hardlink aliases, unsupported Git entries, source digest mismatches, secret markers, and U+2014. It computes a domain-separated SHA-256 over each included file's Git mode, object type, path, length, and content. The committed digest is computed independently from Git objects. Build, cache, log, and runtime output directories are excluded, so generated artifacts never enter the source digest.

The JAR contract verifies the exact reviewed inventory and metadata, fixed timestamps, stable entry order, source provenance, secret and punctuation audits, common-entry client isolation, and byte-identical independent archive construction. CI runs the full clean release command twice at the same source SHA and compares `afterlight-signal-0.1.0+1.21.1.jar` byte for byte.

## Gate Recovery

- Dirty or untracked source: inspect `git status --short --untracked-files=all`, then commit, stash, restore, or remove the reported path.
- Symlink: replace the link with a regular file containing the intended bytes, then commit it.
- Hardlink: copy the file through a new temporary file so the tracked path has its own inode, then rerun the gate.
- Digest mismatch: clear any hidden index flags with `git update-index --no-assume-unchanged <path>` or `git update-index --no-skip-worktree <path>`, restore the path, and rerun from a clean tree.
- Secret marker or U+2014: remove the reported content rather than suppressing the audit.
- Missing or changed provenance: run the exact release command from a clean committed tree. Do not edit generated files under `build/`.

The release artifact is `build/libs/afterlight-signal-0.1.0+1.21.1.jar`. Generate integration checksums only after all gates pass:

```sh
shasum -a 256 build/libs/afterlight-signal-0.1.0+1.21.1.jar
shasum -a 512 build/libs/afterlight-signal-0.1.0+1.21.1.jar
```

Tags and published assets are immutable. Never reuse a release tag or replace a published JAR.
