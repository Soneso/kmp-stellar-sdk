# Contributing to the KMP Stellar SDK

Thank you for helping to improve the SDK. This guide covers bug reports, feature proposals, setup and the pull request process. The commands, the definition of done and the repository rules are in `AGENTS.md`.

## Reporting a bug

Open an issue and state what you did, what happened, what you expected, the SDK version, the target (JVM, Android, JS, iOS, macOS) and the toolchain versions. A failing test or a short snippet that reproduces the behavior shortens the fix.

## Proposing a feature

Open an issue and describe the use case and the proposed behavior. For a SEP or a protocol feature, name the specification. Discuss before writing a large change, so the design is settled before the code.

## Setting up

- A JDK of the CI matrix in `.github/workflows/tests.yml`; the Gradle wrapper downloads Gradle itself. The Commands section of `AGENTS.md` has the test tasks per target.
- The Android SDK, with its location in `ANDROID_HOME` or in `sdk.dir` of a `local.properties` file, for the Android tasks and the documentation build.
- On macOS: Xcode for the Apple targets and Homebrew's libsodium for the macOS tests. Node is downloaded by the JS tasks.
- For the XDR generator: Ruby with Bundler and curl. For the SEP-51 tools: Python 3 and Rust's cargo for the pinned reference CLI. For the compatibility matrices: Python 3 and network; a GitHub token raises the API rate limit. `tools/README.md` indexes the tools and names the requirements of each.

Run the JVM unit suite as `AGENTS.md` describes before you change anything, so you know the baseline is green on your machine.

## Submitting a change

1. Fork the repository and branch from `main` with a short kebab-case name that names the change.
2. Make the change with its tests. A bug fix starts with a test that fails before the fix. A change to platform code gets its tests on that platform.
3. Add an entry under `[Unreleased]` in `CHANGELOG.md`, in the section the change belongs to.
4. Run the gates listed under "Definition of done" in `AGENTS.md`.
5. Commit with an imperative subject and a body that states the motivation and what the change does. Open the pull request against `main` and describe what you verified.

The user documentation under `docs/` and the skill under `skills/` describe the released SDK and are updated on the `release-docs` branch with the release; a pull request does not change them. Describe the needed documentation change in the pull request, and the maintainer adds it on that branch. `docs/documentation-strategy.md`, `AGENTS.md` and this file are process documents: change them in the pull request that changes a fact they state.

## What the review checks

- The change does what the pull request says, and nothing else.
- The tests fail without the change and pass with it; unit tests mock HTTP.
- The conventions in `AGENTS.md`: typed API, `suspend` crypto and I/O, KDoc on public declarations, the test namespaces, the source-set layout.
- The CHANGELOG entry; no hand edits to generated files; no change to the user documentation or the skill.

CI runs the JVM tests on every JDK of the matrix and the coverage gate on the newest, the JS and macOS tests and the XDR generator checks. The JVM test job of the newest JDK and the Codecov project status must pass.

## License

By contributing, you agree that your contributions are licensed under the license in `LICENSE`.
