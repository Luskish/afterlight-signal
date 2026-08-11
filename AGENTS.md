# AFTERLIGHT Signal Agent Guardrails

These rules apply to every agent working in this repository.

## Writing

- Never use a literal em dash in source, documentation, comments, commit messages, or user-facing reports.

## Skills

- Check for applicable skills before every task.
- Use the project Minecraft modding and NeoForge skills for Java or loader work.
- Follow the test-driven-development skill for every feature, bug fix, or behavior change.

## Development

- Write a failing test first, confirm the expected RED result, then implement the minimum production code required for GREEN.
- Run the task's exact verification commands before making completion claims.
- Keep every dependency and tool version pinned exactly. Do not weaken version pins or dependency ranges.
- Do not add features assigned to later tasks.

## Repository Safety

- Never commit JARs, secrets, tokens, build outputs, IDE files, or Gradle caches.
- Every commit must include `Co-Authored-By: Codex <noreply@openai.com>`.
- Releases are immutable. Never overwrite, replace, or reuse a published release or tag.
