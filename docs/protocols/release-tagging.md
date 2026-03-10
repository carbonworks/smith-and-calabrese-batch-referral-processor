# Protocol: Release Tagging

## Version Format

**`major.minor.hotfix`** (e.g., `1.0.0`, `1.0.1`, `1.1.0`)

- **Major**: Breaking changes or major feature milestones
- **Minor**: New features or significant enhancements
- **Hotfix**: Bug fixes and patches

## Release Candidates

Release candidates are tagged on `main` with a `-rcN` suffix:

```
1.0.0-rc1    First release candidate
1.0.0-rc2    Second release candidate (if rc1 needs changes)
```

### MSI Version Numbering (Pre-Release Offset)

Windows MSI only compares the first 3 fields of a version number for upgrade logic. To allow RC-to-RC upgrades without uninstalling, RCs use a **pre-release offset** convention:

```
Target release: 1.1.0
  rc1 packageVersion: 1.0.901
  rc2 packageVersion: 1.0.902
  rc3 packageVersion: 1.0.903
Final packageVersion: 1.1.0
```

The `9xx` patch values are chosen to be well above any realistic hotfix number, making them clearly identifiable as pre-release. Each RC is strictly greater than the previous, and the final release version is strictly greater than all RCs.

When tagging the final release, update `packageVersion` from the RC value back to the target version (e.g., `1.0.902` → `1.1.0`).

### Workflow

1. **Set the RC version** in `app/build.gradle.kts`:
   ```kotlin
   packageVersion = "1.0.901"  // rc1 for v1.1.0
   ```

2. **Tag the RC** on `main` when the codebase is ready for validation:
   ```
   git tag -a v1.1.0-rc1 -m "Release candidate 1 for v1.1.0"
   git push origin v1.1.0-rc1
   ```

3. **Build and validate** — build the installer from the tagged commit and test on target machines.

4. **If the RC passes** — update `packageVersion` to the final version, commit, tag, and push:
   ```kotlin
   packageVersion = "1.1.0"
   ```
   ```
   git tag -a v1.1.0 -m "Release v1.1.0"
   git push origin v1.1.0
   ```

5. **If the RC needs changes** — commit fixes, bump to next RC version, tag:
   ```kotlin
   packageVersion = "1.0.902"  // rc2 for v1.1.0
   ```
   ```
   git tag -a v1.1.0-rc2 -m "Release candidate 2 for v1.1.0"
   git push origin v1.1.0-rc2
   ```

## Post-Release Hotfixes

If a hotfix is needed after release:

1. Branch from the release tag: `git checkout -b hotfix/1.0.1 v1.0.0`
2. Apply the fix and commit
3. Tag: `git tag -a v1.0.1 -m "Hotfix v1.0.1"`
4. Merge back to `main`

## Tag Naming

All tags use the `v` prefix: `v1.0.0-rc1`, `v1.0.0`, `v1.0.1`.

## Pre-Release Checklist

Before tagging an RC, verify the following:

### 1. jlink Module Scan

Run `jdeps` against the app JAR to ensure all required Java platform modules are available in the jlink runtime image:

```bash
# Build the mapping file (generated during packageMsi)
MAPPING="app/build/compose/tmp/packageMsi/libs-mapping.txt"

# Extract required modules
CP=$(sed 's/;.*//' "$MAPPING" | tr '\n' ';')
jdeps --print-module-deps --ignore-missing-deps --multi-release 17 \
  --class-path "$CP" app/build/libs/app-*.jar

# Compare against the runtime image
cat app/build/compose/tmp/main/runtime/release
```

Every module listed by `jdeps` must appear in the runtime image's `MODULES` line. If a module is missing, either:
- **Preferred**: Remove the dependency from application code (e.g., replace `java.sql.Date` with a `java.time` equivalent)
- **Fallback**: Add the module explicitly in `build.gradle.kts` via `nativeDistributions { modules("java.sql") }`

### 2. FOSS Licensing Review

Verify that `app/src/main/resources/NOTICE.txt` accurately reflects the current dependency tree:

```bash
./gradlew :app:dependencies --configuration runtimeClasspath
```

Check for:
- **New dependencies** added since the last release — each needs an entry in NOTICE.txt with name, version, copyright holder, license type, and project URL
- **Version bumps** — update version numbers in existing NOTICE.txt entries
- **Removed dependencies** — delete entries for libraries no longer in the dependency tree
- **Help screen sync** — the open-source components list in `HelpScreen.kt` (LicensingCard) must match NOTICE.txt

### 3. Changelog

Update **both** changelog locations before tagging:

1. **`CHANGELOG.md`** (project root) — move the `[Unreleased]` section to a versioned heading with the release date, and add a fresh empty `[Unreleased]` section above it.
2. **`HelpScreen.kt`** (`CHANGELOG_ENTRIES` inline data) — update to match `CHANGELOG.md` so the in-app "What's New" card reflects the release.

Each release section uses these categories:

- **Added** — new features or fields
- **Changed** — modifications to existing behavior
- **Fixed** — bug fixes

Review the git log since the last release tag to ensure all user-visible changes are captured:

```bash
git log --oneline v1.0.1..HEAD
```

### 4. Version Number

The version in `app/build.gradle.kts` (`nativeDistributions.packageVersion`) must match the release version before tagging.

## Build Artifacts

Installers are built from tagged commits.
