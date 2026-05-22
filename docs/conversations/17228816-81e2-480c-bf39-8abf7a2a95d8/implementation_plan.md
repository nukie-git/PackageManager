# Implementation Plan - SmartPack PackageManager Master Fixes

This plan outlines the modernization and stability fixes for the SmartPack PackageManager application based on the requested sequential task list in `antigravity_smartpack_prompt.md`.

## User Review Required

> [!NOTE]
> The base themes in `app/src/main/res/values/styles.xml` already inherit from `Theme.Material3.DayNight.NoActionBar`. Task 1 theme adjustments will focus on adding the Preference strings, implementing `ThemeHelper.java`, initializing it on app launch, and wiring it into the settings screen.

> [!IMPORTANT]
> The class `ExportNameBuilder.java` already exists in `com.smartpack.packagemanager.utils`. However, it is not wired up to the actual APK/Bundle export tasks (`ExportAPKTasks` and `ExportBundleTasks`). We will wire them up to use the customized naming modes.

---

## Proposed Changes

### Component 1: Theme & Toggle Configuration (Task 1)

#### [MODIFY] [strings.xml](file:///c:/Users/nukie/apps/PackageManager/app/src/main/res/values/strings.xml)
- Append theme selection strings `theme_setting`, `theme_auto`, `theme_light`, `theme_dark` if missing.
- Add `theme_entries` and `theme_values` arrays in `arrays.xml` (or `strings.xml`) for UI selection.

#### [NEW] [ThemeHelper.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/ThemeHelper.java)
- Implement `ThemeHelper` class under `com.smartpack.packagemanager.utils` to read theme preference from `SharedPreferences` and set default night mode via `AppCompatDelegate`.

#### [MODIFY] [MainActivity.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/MainActivity.java)
- Invoke `ThemeHelper.applyTheme(this)` at the very beginning of `onCreate`.

#### [MODIFY] [SettingsFragment.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/fragments/SettingsFragment.java)
- Wire up a UI selection dialog or list option for theme settings, saving to shared preferences, and recreating the activity on change.

---

### Component 2: Export Naming Customization (Task 2)

#### [MODIFY] [PackageData.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/PackageData.java)
- Define `getFileName(packageName, context)` using `ExportNameBuilder.buildExportName` to resolve the current unresolved symbol and provide custom filenames for batch/single exports.

#### [MODIFY] [ExportAPKTasks.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/tasks/ExportAPKTasks.java)
- Update single APK export task to construct filenames via `ExportNameBuilder.buildExportName` instead of legacy hardcoded formatting.

#### [MODIFY] [ExportBundleTasks.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/tasks/ExportBundleTasks.java)
- Update bundle export task to format Zip/APKM filenames via `ExportNameBuilder.buildExportName`.

---

### Component 3: Null-Safe Sorting & Stability (Task 3 & 4)

#### [MODIFY] Sorting Comparators
- Search and audit all custom `Comparator` implementations (e.g. sorting apps by name, label, size, date, etc.).
- Ensure all comparisons are robustly null-guarded to prevent crashes on partially loaded package info.

#### [MODIFY] Permissions Editor Integration
- Locate permissions fragment launch transaction, check lifecycle states (`isStateSaved()`, etc.), and wrap in safety guards.
- Add null-safety guards inside permissions adapter/recycler binding.

---

## Verification Plan

### Automated/Manual Verification
- Perform static analysis and compile-level verification of all files modified/created.
- Verify XML well-formedness, resource references, and imports.
