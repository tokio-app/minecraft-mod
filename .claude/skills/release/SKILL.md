---
name: release
description: "Publish a version of the Tokio Minecraft mod. Bumps the version, previews the notes, commits and tags, then, once the user pushes, watches the Release workflow until the GitHub release has its jar. Use only when the user asks for a release."
argument-hint: "[version]"
disable-model-invocation: true
---

# Release

A release is what players download as soon as the workflow publishes it. Confirm the version, and never push: the user does.

## Where things stand

- Version: !`sed -n 's/^version=//p' gradle.properties`
- Last tag: !`git describe --tags --abbrev=0`
- Branch: !`git status --short --branch`

## Steps

1. Check the branch: `main`, clean and level with `origin/main` (`git fetch origin`, then `git status --short --branch`). If anything is off, stop and tell the user.
2. Pick the version: the argument if there is one, otherwise propose one from the Conventional Commits since the last tag (`git log --oneline <last tag>..HEAD`): while the version is `0.x`, a `feat!:` or other breaking change bumps the minor, anything else the patch. Confirm it with the user.
3. Run the checks with JDK 25 (in Git Bash, `export JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.4.101-hotspot"`): `./gradlew build` passes, and `build/libs/tokio-<version>.jar` holds only `com/ialfaro/tokio/`, `assets/tokio/`, `fabric.mod.json`, `LICENSE_tokio` and the manifest (`unzip -l`).
4. Show the notes: `bash scripts/release-notes.sh`. A wrong one can be fixed in the release on GitHub afterwards, and a rerun keeps the edit.
5. Change only `version` in `gradle.properties`, and commit it as `chore(release): <version>`, subject only.
6. Tag it: `git tag v<version>`, lightweight. The workflow fails when the tag doesn't match `gradle.properties`.
7. Tell the user to push the commit and the tag: `git push origin main`, then `git push origin v<version>`.
8. Watch the Release workflow (`.github/workflows/release.yml`: build and tests, then release). Give the user its page, `https://github.com/tokio-app/minecraft-mod/actions/workflows/release.yml`. With `gh`, use `gh run watch`; without it, poll `https://api.github.com/repos/tokio-app/minecraft-mod/actions/runs?event=push&branch=v<version>` once a minute in the background until its `status` is `completed`.
9. If it failed, give the user the run's `html_url` and the step that failed. A rerun on GitHub only replaces the jar; if the fix needs a commit, it needs a new version.
10. Check the release at `https://api.github.com/repos/tokio-app/minecraft-mod/releases/tags/v<version>`: published, not a draft, with one file, `tokio-<version>.jar`.

## Done when

The release is published with its jar, and the user has its URL: `https://github.com/tokio-app/minecraft-mod/releases/tag/v<version>`.
