# Implementation Plan - Modernization & Stability Fixes

This plan outlines the steps to resolve the empty app list blocker, optimize package sorting/retrieval, improve permissions editor stability, ensure Gradle 8.12 compatibility, and run pre-build validation.

## User Review Required

> [!IMPORTANT]
> **AGP Downgrade to 8.10.1:**
> The `master` branch defines `agp = "9.1.1"` in `libs.versions.toml`, which requires Gradle 9.3.1+. However, the prompt mandates pinning the Gradle wrapper to exactly **Gradle 8.12** (which is incompatible with AGP 9.1.1). To support Gradle 8.12, we must downgrade AGP to `8.10.1` in `gradle/libs.versions.toml`.

## Proposed Changes

---

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///c:/Users/nukie/apps/PackageManager/gradle/libs.versions.toml)
- Downgrade the Android Gradle Plugin version reference `agp` from `9.1.1` to `8.10.1` to ensure compatibility with Gradle 8.12.

---

### Core Data & Pipeline

#### [MODIFY] [PackageData.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/PackageData.java)
- **Fix Data Load Blocker (Task 0D):**
  - Move the sorting logic out of the `for` loop inside `PackageData.getData()` to avoid $O(N^2)$ write/sort operations on `CopyOnWriteArrayList` on every single iteration, which thrash garbage collection and block the background thread.
  - Implement automatic data initialization: if `mRawData == null` when `getData()` is called, invoke `generateData(null, context)` dynamically on the caller's thread (which is guaranteed to be a background thread in the caller fragment).
  - Overload `getRemovedPackagesData(Context context)` to automatically call `generateData(null, context)` if `mRemovedData == null`.
- **Null-Safe Sorting (Task 3):**
  - Update all string comparators (`appName`, `packageName`) to perform proper null-safe checks (e.g. falling back to package name or empty string if null).

---

### App Details & Permissions

#### [MODIFY] [PackageDetails.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/PackageDetails.java)
- **Optimize Permissions Retrieval (Task 4):**
  - Rewrite `getPermissions` to call `PackageData.getPackageInfo()` only once and cache the result in a local variable, rather than executing repeated Package Manager IPC queries inside the loop condition and body.
  - Add explicit bounds-checking for the `requestedPermissionsFlags` array.

#### [MODIFY] [PermissionsFragment.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/fragments/PermissionsFragment.java)
- **Permissions Data Leak Fix (Task 4):**
  - Only read from the static `mAPKParser` when `mAPKPicked` is true, preventing permissions of previously parsed APKs from overriding regular installed application permissions.

#### [MODIFY] [UninstalledAppsFragment.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/fragments/UninstalledAppsFragment.java)
- Update `PackageData.getRemovedPackagesData()` call to pass the `context` argument (`PackageData.getRemovedPackagesData(context)`), enabling auto-generation of data if it is not yet populated.

---

## Verification Plan

### Automated Tests
- Run `./gradlew compileFdroidDebugJavaWithJavac` to verify successful configuration and compilation.
- Run `./gradlew :app:compileFdroidDebugJavaWithJavac -Xlint:deprecation` to audit and fix any deprecated API warnings in `PackageTasksFragment.java`.
- Run `./gradlew assembleFdroidDebug` to build the final debug APK.

### Manual Verification
- Install the debug APK on the test device.
- Confirm the app list populates within 2-3 seconds on launch.
- Verify user and system apps are split correctly across tabs.
- Verify searching and sorting functionalities work without issues.
- Verify that opening details and permissions does not cause any crashes.
