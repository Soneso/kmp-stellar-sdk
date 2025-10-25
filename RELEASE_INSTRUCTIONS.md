# Release Instructions

This document provides step-by-step instructions for releasing a new version of the Stellar KMP SDK to Maven Central.

## Prerequisites

Before starting a release, ensure you have:

### 1. Maven Central Access
- **Central Portal Account**: Access to https://central.sonatype.com/
- **User Token**: Generated from Central Portal (Account → Generate User Token)
- **Credentials File**: `~/.gradle/gradle.properties` with:
  ```properties
  ossrhUsername=YOUR_USER_TOKEN_USERNAME
  ossrhPassword=YOUR_USER_TOKEN_PASSWORD
  ```

### 2. GPG Signing Key
- **Key Type**: RSA 4096-bit (Gradle requires RSA, not Ed25519)
- **Key Published**: Uploaded to https://keys.openpgp.org
- **Credentials**: In `~/.gradle/gradle.properties`:
  ```properties
  signing.gnupg.keyName=YOUR_KEY_ID
  signing.gnupg.passphrase=YOUR_GPG_PASSPHRASE
  ```

### 3. Local Environment
- **Homebrew**: `libsodium` installed (`brew install libsodium`)
- **GPG**: Installed and configured (`brew install gpg`)
- **Git**: Clean working directory on `main` branch
- **Java**: JDK 11 or later

## Release Process

### Phase 1: Prepare Release

#### Step 1: Determine Version Number

Follow [Semantic Versioning](https://semver.org/):
- **Major** (X.0.0): Breaking changes
- **Minor** (0.X.0): New features, backwards compatible
- **Patch** (0.0.X): Bug fixes, documentation updates

Example: `0.2.1` for a patch release with bug fixes.

#### Step 2: Update Version Numbers

Update version in the following files:

**build.gradle.kts** (root):
```kotlin
allprojects {
    group = "com.soneso.stellar"
    version = "X.Y.Z"  // Update this
}
```

**Documentation files** (update all version references):
- `README.md`
- `docs/getting-started.md`
- `demo/CLAUDE.md`
- `demo/README.md`
- `demo/shared/build.gradle.kts` (in comments)
- `docs/platforms/ios.md`
- `docs/platforms/macos.md`

Search for previous version number and replace:
```bash
# Find all occurrences
grep -r "0.2.0" --include="*.md" --include="*.kts" .

# Use IDE find-and-replace for accuracy
```

#### Step 3: Update CHANGELOG.md

Add a new version entry at the top:

```markdown
## [X.Y.Z] - YYYY-MM-DD

### Added
- New features

### Changed
- Modifications to existing features

### Fixed
- Bug fixes

### Deprecated
- Features that will be removed

### Removed
- Removed features

### Security
- Security fixes
```

Include information from recent commits:
```bash
# Review commits since last release
git log vPREVIOUS_VERSION..HEAD --oneline

# Get detailed commit messages
git log vPREVIOUS_VERSION..HEAD --format="%h %s%n%b"
```

#### Step 4: Create Release Notes

Create `RELEASE_NOTES_X.Y.Z.md` with:
- Overview of the release
- What's new/changed/fixed
- Migration guide (if breaking changes)
- Platform support status
- Known issues (if any)

Use previous release notes as a template.

#### Step 5: Review Changes

Check that all changes are documented:
```bash
# Review all modified files
git status

# Check diff
git diff
```

### Phase 2: Build and Test

#### Step 6: Clean Build

```bash
# Clean previous builds
./gradlew clean

# Build all platforms (without tests)
./gradlew :stellar-sdk:assemble -x test -x allTests
```

Expected output: `BUILD SUCCESSFUL`

#### Step 7: Run Tests (Optional but Recommended)

```bash
# Run JVM tests
./gradlew :stellar-sdk:jvmTest

# Run specific test classes on JS
./gradlew :stellar-sdk:jsNodeTest --tests "KeyPairTest"

# Run macOS tests (if on macOS)
./gradlew :stellar-sdk:macosArm64Test
```

**Note**: Some integration tests may fail if testnet is down. This is acceptable for release if unit tests pass.

### Phase 3: Git Release

#### Step 8: Commit Release Changes

```bash
# Stage all release files
git add -A

# Create release commit
git commit -m "Release version X.Y.Z

Brief description of the release.

Changes:
- List major changes
- Update version numbers
- Update documentation

Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

#### Step 9: Create Git Tag

```bash
# Create annotated tag
git tag -a vX.Y.Z -m "Version X.Y.Z - Brief description"

# Verify tag
git tag -l -n1 | tail -5
git show vX.Y.Z --no-patch
```

### Phase 4: Publish to Maven Central

#### Step 10: Publish to Staging Repository

Use the **Nexus Publishing Plugin** command (NOT the direct OSSRH command):

```bash
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository --no-daemon
```

**Important**: Use `publishToSonatype`, not `publishAllPublicationsToOSSRHRepository`

This command will:
1. Create a staging repository
2. Upload all 8 platform artifacts (JVM, JS, iOS variants, macOS variants)
3. Upload sources, javadocs, and GPG signatures
4. Close the staging repository (validate)
5. Release to Maven Central (publish)

Expected duration: 5-7 minutes

Expected output:
```
> Task :initializeSonatypeStagingRepository
Created staging repository 'com.soneso--XXXXXXXX' at https://...

[... build tasks ...]

> Task :closeSonatypeStagingRepository
> Task :releaseSonatypeStagingRepository
> Task :closeAndReleaseSonatypeStagingRepository

BUILD SUCCESSFUL in 6m 5s
```

**Save the staging repository ID** from the output (e.g., `com.soneso--e77ef82e-2f32-48a6-bbf5-bdb77392d6cb`).

#### Step 11: Verify Publication

Check the Central Portal:
- URL: https://central.sonatype.com/publishing/deployments
- Status should show "Published" or "Publishing"
- All artifacts should be present (POM, JAR, sources, javadoc, signatures)

### Phase 5: Wait for Maven Central Sync

#### Step 12: Monitor Sync Progress

Maven Central sync typically takes **15-30 minutes**.

Check periodically:
```
https://central.sonatype.com/artifact/com.soneso.stellar/stellar-sdk/X.Y.Z
```

When synced, you'll see:
- Version X.Y.Z listed
- All 8 platforms available
- Download links active

**Do not proceed until sync is complete** - the artifacts must be publicly available before creating the GitHub release.

### Phase 6: Commit Build Fixes (If Any)

If you had to make any build configuration fixes during publishing, commit them:

```bash
git add stellar-sdk/build.gradle.kts
git commit -m "Fix Gradle task dependencies for publishing

[Description of what was fixed]

Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Phase 7: Push to GitHub

#### Step 13: Push Commits and Tags

**Only after Maven Central sync is complete:**

```bash
# Push commits
git push origin main

# Push tag
git push origin vX.Y.Z

# Verify on GitHub
git log --oneline -5
```

#### Step 14: Create GitHub Release

1. Navigate to: https://github.com/Soneso/kmp-stellar-sdk/releases/new
2. **Choose tag**: Select `vX.Y.Z`
3. **Release title**: `Version X.Y.Z - Brief Description`
4. **Description**: Copy content from `RELEASE_NOTES_X.Y.Z.md`
5. **Set as latest release**: Check this box
6. Click **Publish release**

### Phase 8: Verify Release

#### Step 15: Final Verification

Verify the release is complete:

**Maven Central**:
- [ ] Visit: https://central.sonatype.com/artifact/com.soneso.stellar/stellar-sdk/X.Y.Z
- [ ] All 8 platforms visible
- [ ] Dependencies downloadable

**GitHub**:
- [ ] Release visible: https://github.com/Soneso/kmp-stellar-sdk/releases
- [ ] Tag appears in tags list
- [ ] Commit history updated

**Documentation**:
- [ ] README shows correct version
- [ ] Getting Started guide references new version
- [ ] CHANGELOG.md includes release

## Troubleshooting

### Issue: Gradle 9.0 Task Dependency Validation Error

**Symptom**:
```
A problem was found with the configuration of task ':stellar-sdk:signXXXPublication'
Task 'publishYYY' uses this output without declaring an explicit dependency
```

**Solution**: This should already be fixed in `stellar-sdk/build.gradle.kts` (lines 302-317). If you encounter this, ensure the task dependency fix covers the correct repository type.

### Issue: No Staging Repository Created

**Symptom**:
```
No staging repository with name sonatype created
```

**Solution**: You used the wrong command. Use:
```bash
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
```

NOT:
```bash
./gradlew publishAllPublicationsToOSSRHRepository closeAndReleaseSonatypeStagingRepository
```

### Issue: GPG Signing Failure

**Symptom**:
```
Signing key not found or passphrase incorrect
```

**Solutions**:
1. Verify GPG key exists: `gpg --list-secret-keys`
2. Verify key is RSA (not Ed25519): `gpg --list-keys --keyid-format LONG`
3. Check `~/.gradle/gradle.properties` has correct key ID and passphrase
4. Restart GPG agent: `gpgconf --kill gpg-agent && gpg-agent --daemon`

### Issue: Maven Central Sync Taking Too Long

**Symptom**: Artifacts not appearing after 30+ minutes

**Solutions**:
1. Check Central Portal deployment status
2. Verify staging repository was released (not just closed)
3. Contact Sonatype support if still not synced after 2 hours

### Issue: Integration Tests Failing

**Symptom**: Network-related test failures

**Solution**: Integration tests depend on Stellar Testnet availability. If only integration tests fail but unit tests pass, you can proceed with the release. Document any known test failures in release notes.

### Issue: Build Fails on Native Platforms

**Symptom**: iOS/macOS builds fail with libsodium errors

**Solutions**:
1. Verify libsodium installed: `brew list libsodium`
2. Reinstall if needed: `brew reinstall libsodium`
3. Clean build: `./gradlew clean`

## Post-Release Tasks

After a successful release:

1. **Announce Release**:
   - Update project README if needed
   - Notify users on relevant channels
   - Tweet/post about new version

2. **Monitor Issues**:
   - Watch for bug reports related to new version
   - Respond to GitHub issues promptly

3. **Update Internal Documentation**:
   - Note any lessons learned
   - Update this document if process changed

4. **Plan Next Release**:
   - Review backlog
   - Set milestones for next version

## Quick Reference Commands

```bash
# Clean and build
./gradlew clean
./gradlew :stellar-sdk:assemble -x test

# Create release
git add -A
git commit -m "Release version X.Y.Z"
git tag -a vX.Y.Z -m "Version X.Y.Z - Description"

# Publish to Maven Central
./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository --no-daemon

# Push to GitHub (after Maven sync completes)
git push origin main
git push origin vX.Y.Z
```

## Release Checklist

Use this checklist for each release:

- [ ] Version number determined (semantic versioning)
- [ ] Version updated in all files
- [ ] CHANGELOG.md updated
- [ ] Release notes created
- [ ] Clean build successful
- [ ] Tests passing (or failures documented)
- [ ] Release commit created
- [ ] Git tag created
- [ ] Published to Maven Central (BUILD SUCCESSFUL)
- [ ] Staging repository ID saved
- [ ] Central Portal shows "Published"
- [ ] Maven Central sync complete (15-30 min)
- [ ] Version visible on Maven Central
- [ ] Commits pushed to GitHub
- [ ] Tag pushed to GitHub
- [ ] GitHub release created
- [ ] Release verified on all platforms
- [ ] Announcement made (if applicable)

---

**Document Version**: 1.0
**Last Updated**: October 25, 2025
**Based on**: Releases 0.2.0 and 0.2.1
