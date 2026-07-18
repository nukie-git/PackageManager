# AGENTS.md — SmartPack Package Manager AI Agent Guide

**Project:** SmartPack Package Manager v7.11
**Language:** Java (Android)  
**Build System:** Gradle 8.12 with AGP 8.10.1  
**Min SDK:** 24 → 26 (Android 7–15)  
**Architecture Pattern:** Fragment-based UI with utility-layer package data abstraction

---

## Big Picture Architecture

### Multi-view Fragment Hierarchy (App List)
The app organizes installed apps across **three core Fragment views** defined in `PackageTasksFragment.java`, each backed by the same `PackageData` cache:
- **System Apps** — apps with `ApplicationInfo.FLAG_SYSTEM` set
- **User Apps** — regular installed apps (non-system)
- **Uninstalled Apps** — packages with `MATCH_UNINSTALLED_PACKAGES` flag

**Data loading sequence** (`PackageData.java`):
1. `init()` → calls `generateData(null, context, false)` if cache is null
2. `generateData()` → queries `PackageManager.getInstalledPackages()` off-thread, populates `mRawData` (volatile `List<PackageItems>`)
3. `getData(searchText)` → filters `mRawData` by app type, search term, and sort order; returns `List<PackageItems>`

**Key impl detail:** `mRawData` is shared; uninstalled packages are scanned separately and cached in `mRemovedData` only when the Uninstalled tab is opened (lazy load to improve cold-start performance).

---

## Critical Developer Workflows

### Building & Running
```bash
# Full debug build (all flavors)
./gradlew assembleFdroidDebug

# Fast incremental build after small changes
./gradlew assembleFdroidDebug --parallel

# Run tests (if any added)
./gradlew test

# Check build for deprecation warnings
./gradlew assembleFdroidDebug --warning-mode all
```

### Custom Dependencies & Imports
- **Root/Privileged execution:** `RootShell.java` (synchronous) → wrap off-thread
- **Shizuku integration:** `ShizukuShell.java` (vendor-specific privilege escalation)
- **sCommon utilities:** `in.sunilpaulmathew.sCommon.*` — UI dialogs, file ops, string/int prefs
  - `sCommonUtils.getString(key, default, context)` ← all preference access goes through this
- **APK parsing:** `aXML` library for manifest inspection (experimental APK explorer feature)
- **Zip manipulation:** `zip4j` for split-APK bundling

### Build Output & Artifacts
- Debug APK: `app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk`
- Release signed APK (requires `sp.jks` key): placed in same directory
- ProGuard mappings (release build): `app/build/outputs/mapping/fdroidRelease/`

---

## Project-Specific Conventions

### Preference & Settings Access
**All preference access must go through `AppSettings` or `sCommonUtils.getString/getInt/getBoolean`**, never direct `SharedPreferences`. This centralizes defaults and key names:
- Export naming: `PREF_EXPORT_MODE`, `PREF_EXPORT_SEP`, `PREF_EXPORT_TPL` → uses `ExportNameBuilder`
- App type filter: `"appTypes"` key (values: `"all"`, `"system"`, `"user"`)
- Sort order: `"sort_apps"` key → integer (0=name, 1=date, 2=size, etc.; verify in code)

### Data Model
- **`PackageItems.java`** — lightweight wrapper around `PackageInfo` used in UI lists (provides `getAppName()`, `isSystemApp()`, `getPackageName()` accessors)
- **`ExportNameBuilder.java`** — orchestrates filename generation with three modes: `PACKAGE_NAME`, `APP_NAME`, `APP_PACKAGE_VERSION` (custom template mode is configurable)

### Fragment Patterns
- **Layout inflation & ViewBinding** — must check view IDs match exactly between Java `findViewById()` and XML `android:id` (confirmed bug in Task 0C: RecyclerView ID mismatch)
- **Background threads** → always use `new Thread()` or `ExecutorService`; sync results to UI via `requireActivity().runOnUiThread()` or `Handler.post()`
- **RecyclerView lifecycle** — safe to call `notifyDataSetChanged()` only after adapter is bound in `onViewCreated`

---

## Integration Points & Cross-Component Communication

### Privilege Escalation (Root vs Shizuku)
```
RootShell.rootAccess()  [synchronous blocking I/O → MUST run off main thread]
    ↓
ShizukuShell.java  [alternative for system-granted privilege]
    ↓
Used by: SplitAPKInstaller, ExportedAppsFragment (debloat/disable features)
```
**Critical:** Check privilege availability **before** offering UI actions (e.g., hide "Uninstall System" button if no privilege).

### APK Export Flow
```
PackageInfo + AppLabel  
    ↓
ExportNameBuilder.build()  [applies selected naming mode + sanitization]
    ↓
File copy to AppSettings.getExportPath()
    ↓
Result stored in device external storage or app-specific export folder
```

### Uninstall Flow (Batch & Single)
- **Root available:** `RootShell.exec("pm uninstall [--user 0] <packageName>")`
- **Shizuku available:** `ShizukuShell` equivalent
- **No privilege:** Intent-based uninstall → system UI (one app per user confirmation)

---

## Common Patterns & Anti-Patterns

### ✅ Correct Patterns
- **Null-safe sorting:** Use `Objects.requireNonNullElse(label, fallback)` or explicit checks before `.compareToIgnoreCase()`
- **API level guards:** `if (Build.VERSION.SDK_INT >= 28) pi.getLongVersionCode() else pi.versionCode`
- **Off-thread loading:** All `PackageManager` queries run in `new Thread()` or `ExecutorService`, results posted back to main via `runOnUiThread()`

### �� Anti-Patterns (Observed Bugs)
- **Direct `SharedPreferences` access** → use `AppSettings` or `sCommonUtils` instead
- **Calling `RootShell.rootAccess()` from `onCreateView()`** → causes 33+ skipped frames (Task 0F-2)
- **Unguarded `PackageManager` queries every time data is accessed** �� cache in `mRawData`, guard with `if (mRawData != null && !mRawData.isEmpty()) return;`
- **ViewBinding/findViewById ID mismatch** → read XML `android:id`, update Java to match exactly

---

## SDK & Dependency Notes

### Version Constraints
- **compileSdk = 36** (Android 15 APIs available)
- **targetSdk = 35** (enforce edge-to-edge; `onBackPressed` migration required)
- **minSdk = 26** (Android 8+; `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` pattern)
- **Java source = 11**, **JDK = 17** minimum (JDK 21 bundled with Android Studio Meerkat)

### Gradle Version
- **Gradle wrapper = 8.12** (pinned in `gradle/wrapper/gradle-wrapper.properties`)
- **AGP = 8.10.1** — no further upgrades until verified compatibility
- **Groovy DSL syntax** — deprecated space-assignment `url "..."` must be `url = "..."` (Gradle 8.12 enforces this; removal in 10.0)

### AndroidX Compatibility
- **androidx.appcompat** & **material** — Material3 DayNight theme support already enabled
- **androidx.preference** — used for `PreferenceManager.getDefaultSharedPreferences()`
- **androidx.lifecycle & Fragment** — `getViewLifecycleOwner()` must be used for back-press callbacks

---

## Quick Command Reference

| Task | Command |
|------|---------|
| Resolve compile errors | `./gradlew :app:compileFdroidDebugJavaWithJavac` |
| Check deprecation warnings | `./gradlew :app:compileFdroidDebugJavaWithJavac -Xlint:deprecation` |
| Verify wrapper version | `./gradlew --version` |
| Clean full rebuild | `./gradlew clean assembleFdroidDebug` |
| Run with logcat filtering | `adb logcat -s SmartPack:V` (key prefix: "SmartPack" or "PackageManager") |

---

## Known Limitations & TODOs

- **APK Finder** (Task 8) — partial implementation; content URI resolution for split-APK detection incomplete
- **Batch operations** — uninstall respects only root/Shizuku; standard user mode requires per-app confirmation
- **Edge-to-edge (targetSdk 35)** — `WindowInsetsCompat` padding not applied everywhere; verify all bottom bars/sheets
- **Performance** — `MATCH_UNINSTALLED_PACKAGES` scan on MIUI 14 is expensive (~6s); Phase 2 lazy-load in place

---

## Rapid Onboarding Summary

1. **Build succeeds?** → `./gradlew assembleFdroidDebug` must exit with `BUILD SUCCESSFUL`
2. **Understand data flow:** `PackageData.init()` → `generateData()` → `getData()` → adapter
3. **Examine fragment:** `PackageTasksFragment` orchestrates three tabs (System/User/Uninstalled)
4. **Check permission/privilege:** Prefix sensitive ops with `RootShell.rootAccess()` check on background thread
5. **Modify preferences:** Always use `sCommonUtils.getString()` or `AppSettings` getters, never direct `SharedPreferences`
6. **Test UI changes:** Watch for RecyclerView ID mismatches and main-thread I/O

