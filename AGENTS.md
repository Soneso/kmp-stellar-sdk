# AGENTS.md

This file is for everyone who changes the Kotlin Multiplatform Stellar SDK, people and agents alike. It holds what the tree does not show: the commands, what "done" means, which files are generated, the conventions that differ from Kotlin and Gradle defaults, and the limits of a change. When this file and a file it names disagree, the named file wins, and the pull request that changes the fact updates this file. Users of the SDK read `README.md` and `docs/`; agents that build on the SDK read the skill under `skills/`.

## Commands

Setup is described in `CONTRIBUTING.md`. The Kotlin, Android, and JVM target versions are in `build.gradle.kts` and `stellar-sdk/build.gradle.kts`, the Gradle version in `gradle/wrapper/gradle-wrapper.properties`; the CI matrix in the tests workflow names the tested JDKs.

```sh
./gradlew :stellar-sdk:jvmTest -PexcludeIntegrationTests --no-daemon
./gradlew :stellar-sdk:jvmTest -PexcludeIntegrationTests --no-daemon --tests 'com.soneso.stellar.sdk.unitTests.KeyPairTest'
./gradlew :stellar-sdk:koverHtmlReportJvmOnly :stellar-sdk:koverXmlReportJvmOnly -PexcludeIntegrationTests --no-daemon
python3 tools/coverage/coverage_summary.py --min 90
./gradlew :stellar-sdk:jsNodeTest -PexcludeIntegrationTests --no-daemon
./gradlew :stellar-sdk:macosArm64Test -PexcludeIntegrationTests --no-daemon
./gradlew :stellar-sdk:testDebugUnitTest -PexcludeIntegrationTests --no-daemon
./gradlew :stellar-sdk:verifyTestNamespaces --no-daemon
```

- Pass `-PexcludeIntegrationTests` to every test task; without it the integration tests run against testnet. `check`, `build` and `test` without it run them too, and `check` also runs the browser, iOS simulator, macOS x64, and Android tasks and Android Lint, more than 160 tasks.
- The JVM unit suite (about 10,600 tests) takes 35 to 70 seconds, the JS suite 60 to 90 seconds, and the Android suite two to four minutes. The macOS suite takes about 30 seconds with a warm Kotlin/Native cache and about three minutes when a source change recompiles and links the test binary. The JVM and JS suites run with the network blocked once the dependencies are cached; they need loopback sockets.
- The build cache can answer a test task from cache without running a test. `--rerun` forces execution when you need the evidence.
- The Android tasks, `check` and the documentation build need the Android SDK location: `ANDROID_HOME` or `sdk.dir` in the gitignored `local.properties`. The iOS simulator tests need `-PexcludeIntegrationTests` because the simulator rejects the testnet certificate chain.
- `verifyTestNamespaces` fails a test outside the `unitTests` or `integrationTests` packages; CI does not run it.
- The coverage script counts a line as covered only when all of its branches are covered; Kover measures the JVM target only.
- No separate Kotlin linter or formatter is configured; `kotlin.code.style=official` in `gradle.properties` sets the style, and Android Lint runs through `check`.
- The API documentation builds with `./gradlew :stellar-sdk:dokkaGenerateHtml --no-daemon` in about three minutes into `stellar-sdk/build/dokka/html/`; the Pages workflow copies that output to the gitignored `docs/api/latest/` and publishes it from `main`. The package descriptions Dokka reads are in `stellar-sdk/dokka/packages.md`.
- `./gradlew :stellar-sdk:assemble -x test -x allTests` builds every target without tests; it needs Xcode and the Android SDK and takes minutes.
- The demos `demo/` and `smart-account-demo/` are Gradle modules in `settings.gradle.kts` that CI does not build; a change to an API they use is compiled against them locally.

## Definition of done

Before reporting a change as done:

1. The JVM unit suite and `verifyTestNamespaces` pass. The JS and macOS suites pass when the change touches platform code or an `expect` declaration; the Android suite when it touches `androidMain` or `androidUnitTest`.
2. Area gates. After a generator, override, or XDR pin change: `make -C tools/xdrgen-kt test` passes and `make -C tools/xdrgen-kt update` leaves a clean tree. After a change that touches XDR-JSON names or either SEP-51 pin: `ruby tools/sep-51-oracle/emitted_names.rb`, and `Sep51RollbackRehearsalTest` passes or its frozen constants were changed on purpose. After a matrix generator change: `python3 -m unittest discover -s tools/matrix-generator/tests`.
3. A test that would fail without the change exists. A bug fix starts with the failing test.
4. `CHANGELOG.md` has an entry under `[Unreleased]` in the Keep a Changelog section the change belongs to (Added, Changed, Deprecated, Removed, Fixed, Security), in present tense, one entry per user-visible change.
5. No generated file was edited by hand. The user documentation under `docs/` and the skill under `skills/` describe the released SDK; they change on the `release-docs` branch with the release. `docs/documentation-strategy.md` is a process document and changes in the pull request that changes a fact it states.

Every pull request runs `.github/workflows/tests.yml`: the JVM tests on every JDK of the matrix with the coverage gate on the newest, the JS Node tests, the macOS tests, the XDR generator job, and the `agents-md-paths` job. That job runs `.github/scripts/check_agents_md_paths.py`, which fails when a path this file names outside a fenced block is missing and not gitignored, or is a glob that matches no path. A change that moves a file therefore also changes the line here. The XDR generator job runs the generator tests, checks the emitted names and regenerates both output trees; a difference in the two generated directories fails it. The branch rules require the JVM test job of the newest JDK and the Codecov project status; `codecov.yml` holds the targets and thresholds.

## Generated code and data

Generated files are never edited by hand. Change the generator or its override files and regenerate.

- **XDR types.** The files in `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/xdr/` and `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/xdr/json/` whose header says they were generated. Hand-written beside them: `XdrJson.kt`, `XdrReader.kt`, `XdrWriter.kt` and `XdrExtensions.kt` in the first directory, `XdrJsonExecutableTagTest.kt` and `XdrJsonHelperTest.kt` in the second. The stellar-xdr commit is pinned in `tools/xdrgen-kt/xdr-source.cfg` and recorded again as `sdk_xdr_commit` in `tools/sep-51-oracle/oracle-pin.json`; no check compares the two, so they move together by hand. The generator runs on the host with Ruby, Bundler, and curl and downloads the definitions into the gitignored `tools/xdrgen-kt/xdr/`. The JSON name rules are `tools/xdrgen-kt/lib/xdrgen/generators/kotlin_json_names.rb`, the hand-maintained overrides `tools/xdrgen-kt/lib/xdrgen/generators/kotlin_json_overrides.rb`; a generation fails when an override or a byte-backed string registration was not consumed. After an XDR update, check whether a new enum member or union arm needs an override entry. Run `make -C tools/xdrgen-kt update-snapshots` only after an intended output change, and read the snapshot diff. Before changing the generator or the pin, read `tools/xdrgen-kt/README.md`; it holds the targets and the override format.
- **SEP-51 conformance.** `tools/sep-51-oracle/oracle-pin.json` pins the reference CLI, the reference commits, and the SEP-51 version; the skill's SEP-51 reference quotes that version. `tools/sep-51-oracle/name-map.json` and `tools/sep-51-oracle/type_map.json` are generated by `ruby tools/sep-51-oracle/name_map.rb --diff`. The corpus `tools/sep-51-corpus/corpus.json` is generated from `tools/sep-51-corpus/seeds.py` by `python3 tools/sep-51-corpus/generate_corpus.py` and embedded into the generated JSON tests by `make -C tools/xdrgen-kt emit-json-tests`. Hand-written SEP-51 tests live under `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/unitTests/sep/sep51/`; `Sep51CorpusTest.kt` there states the enum rule and the name-table rule, and a struct the reference cannot resolve gets a hand-written round-trip test. The reference-backed checks run weekly in `.github/workflows/sep-51-corpus-drift.yml`. Before moving a pin or changing a JSON name rule, read `tools/sep-51-oracle/README.md`; it holds the modes and exit codes of the name scripts. Before adding a seed or regenerating the corpus, read `tools/sep-51-corpus/README.md`; it holds the seed rules.
- **Compatibility matrices.** The files under `compatibility/` describe the released SDK. `python3 tools/matrix-generator/run_analysis.py` runs every pipeline; it needs network and reads the version from `gradle.properties`, and a GitHub token in `GITHUB_TOKEN` raises the API rate limit. Every run rewrites the `Generated` line of every file, so a run that only verifies a change restores `compatibility/` before the commit. The matrices change in four cases. An SDK behavior change ships them with its release. A generator fix commits them in its pull request, after the maintainer has read every changed cell. A new protocol version adds its rows when it is live. A mixed diff is the maintainer's decision. Before changing a generator or adding a SEP, read `tools/matrix-generator/README.md`; it holds the pipelines and their inputs.
- **Agent skill.** `skills/kmp-stellar-sdk/references/api_reference.md` is generated by `python3 tools/skill-generator/generate_api_reference.py`. Its output on `main` includes unreleased API, so it is regenerated at release only, together with the skill version and the zip; a verification run restores the committed file. Before changing the reference generator, read `tools/skill-generator/README.md`; it holds the scan rules.
- **Test fixtures.** The six binding files under `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/contract/bindings/` were generated by the external contract bindings generator and are not edited; their package is a namespace exception. `stellar-sdk/src/commonTest/resources/wasm/p28_owner_fixture.wasm` is built from `tools/p28-owner-fixture/` and replicated in four SDK repositories; before changing it, read `tools/p28-owner-fixture/README.md`; it holds the build and the replication rule.

## Conventions and gotchas

- The source sets are wired by hand (`kotlin.mpp.applyDefaultHierarchyTemplate=false` in `gradle.properties`): `iosMain` and `macosMain` are intermediate sets without directories, and `androidMain` depends on `jvmMain` on purpose, so Gradle prints "Invalid Dependency on Default Compilation Source Set" on every run. The smart-account WebAuthn and storage adapters live in `androidMain`, `jsMain` and `nativeMain`; a new platform needs all of them.
- Tests mirror the production packages under `unitTests` or `integrationTests`, with the class suffixes `Test` and `IntegrationTest`, because `verifyTestNamespaces` and the integration-test exclusion filter on those packages; `util` and the generated bindings are the namespace exceptions. Unit tests use Ktor's `MockEngine` and never reach a network; coroutine tests use `runTest`. Raise a missing mock seam as a finding; use no `@Ignore`, because a skipped test hides a regression. Test resources live under `stellar-sdk/src/commonTest/resources/` and are read through `TestResourceUtil`; native tests find them through `PROJECT_DIR`, which Gradle sets.
- Integration tests fund accounts through Friendbot and wait with `realDelay()` from `TestUtils.kt`, because `runTest` skips `delay()` in virtual time; a test that needs a minimum protocol calls `networkProtocolAtLeast()` and returns early with a printed reason. Before writing one, read `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/integrationTests/INTEGRATION_TESTS_README.md`; it holds the run commands and the account rules.
- Network and ledger I/O and the crypto entry points are `suspend` functions; secrets are handled as `CharArray`.
- Public declarations carry KDoc with bracketed symbol links and `@property` tags that describe current behavior and its reason, never history; the API documentation is generated from them.
- New code uses the typed API; raw XDR appears only where no typed path exists, because the typed layer carries the validation and the documentation users read.
- The root `build.gradle.kts` lists `mavenLocal()` before Maven Central, so an artifact from `publishToMavenLocal` shadows the published one for the demos.
- The root scripts `build-xcframework.sh` and `build-libsodium-xcframework.sh` write into a `distribution` directory at the root, which is only partly gitignored; leave it out of commits.

## Boundaries

- Releases are the maintainer's. Outside a release preparation the maintainer directs, an agent never changes a version place: `version` and `demo.version` in `gradle.properties`, the root `build.gradle.kts`, `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/Util.kt`, the dependency snippets in `README.md`, `docs/getting-started.md`, `docs/quick-start.md`, `docs/platforms/`, `demo/README.md`, `demo/shared/build.gradle.kts` and `skills/kmp-stellar-sdk/references/smart_accounts.md`. The same holds for the skill version fields in `skills/kmp-stellar-sdk/SKILL.md`, `skills/.claude-plugin/plugin.json` and `.claude-plugin/marketplace.json`, the zip, a tag, or a GitHub release.
- Never run a publish task (`publishToSonatype` and its siblings) outside a release the maintainer directs; a partial upload cannot be retracted. `releases/RELEASE_NOTES_*.md` are the maintainer's files.
- No credential lives in the repository, and the workflows use no credential of the maintainer (`secrets.GITHUB_TOKEN` is the run's own token). Ask before adding a dependency, a workflow, a Gradle module, or a new generated artifact; each is a maintenance commitment the maintainer carries after the pull request.

## Changes

- Branch from `main` with a short kebab-case name that names the change; open the pull request against `main`.
- The commit subject is one imperative sentence of at most 72 characters without a type prefix; the body states the motivation and what the change does, so the log reads without the diff. The pull request body follows the same form and names what was verified.
- A pull request carries code, tests, and the CHANGELOG entry.

## Where details live

- Before your first change, read `CONTRIBUTING.md`; it holds setup, how to report a bug or propose a feature, how a change is submitted and what the review checks.
- Before changing crypto, the async API, or the mutability of a public type, read `docs/architecture.md`; it holds the platform crypto design and the decision records.
- Before writing or changing a page under `docs/`, read `docs/documentation-strategy.md`; it holds the page template, the example rules, and the release policy for documentation. Before running an unfamiliar test task, read `docs/testing.md`; it holds the task names per target.
- Before changing a demo, read `demo/README.md` or `smart-account-demo/README.md`; the agent-signer modules are described in `smart-account-demo/documentation/agent-flow.md`.
- Before changing a tool under `tools/`, read its README; `tools/README.md` indexes the tools and holds the usage of those without their own README.
