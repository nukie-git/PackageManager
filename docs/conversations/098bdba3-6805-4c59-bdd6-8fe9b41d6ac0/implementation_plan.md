# PackageManager Stabilization & Modernization Plan

This plan outlines the required fixes to restore the app list data pipeline, implement a modern Material 3 theme architecture, integrate a customizable ExportNameBuilder for APK exports, fix null-safe sorting comparators, stabilize the permissions editor/back-pressed callback transactions, upgrade Gradle to 8.12, and run pre-build validation.

## Proposed Changes

---

### Component: Data Pipeline (Task 0D & Task 3)

#### [MODIFY] [PackageData.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/PackageData.java)
- Make `generateData()` extremely robust against system/visibility query failures or uninstalled packages:
  - Add try-catch protection around the package iteration loop to ensure single-package issues do not break the whole scanning process.
  - Safely resolve the application label (falling back to package name if null/throws) and sourceDir (falling back to empty string).
  - Wrap `pm.getInstalledApplications(...)` in a nested try-catch block, falling back to 0-flag query or an empty list if `MATCH_UNINSTALLED_PACKAGES` is unsupported or throws an exception on some ROMs.
- Make all comparisons inside `getData()` null-safe to prevent crashes if custom sort settings (by installation date or size) are applied.
- Update `clearAppSettings()` to be a documented no-op stub since standard package preferences do not exist in this app.

#### [MODIFY] [PackageItems.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/SerializableItems/PackageItems.java)
- Guard `getInstalledTime()`, `getUpdatedTime()`, and `getAPKSize()` with null checks, returning `0L` if underlying resource fetches return null (e.g. package not found due to API visibility limits or uninstalled status).

---

### Component: Theme Management (Task 1)

#### [NEW] [ThemeHelper.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/ThemeHelper.java)
- Implement `ThemeHelper.java` to read `pref_app_theme` ("auto", "light", "dark") and apply the appropriate night mode using `AppCompatDelegate.setDefaultNightMode`.

#### [MODIFY] [AppSettings.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/AppSettings.java)
- Define `PREF_APP_THEME = "pref_app_theme"` constant.

#### [MODIFY] [BaseActivity.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/activities/BaseActivity.java)
- Call `ThemeHelper.applyTheme(this)` inside `onCreate()` to ensure all inheriting activities respect the setting.

#### [MODIFY] [MainActivity.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/MainActivity.java)
- Call `ThemeHelper.applyTheme(this)` inside `onCreate()` (since it does not extend `BaseActivity`).

#### [MODIFY] [StartActivity.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/activities/StartActivity.java)
- Call `ThemeHelper.applyTheme(this)` inside `onCreate()` instead of `sThemeUtils.initializeAppTheme(this)`.

#### [MODIFY] [SettingsFragment.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/fragments/SettingsFragment.java)
- Replace the legacy theme selection dialog choices to persist to the new `pref_app_theme` key.
- Recreate the activity upon theme changes to apply the style immediately.

---

### Component: APK Export & Naming (Task 2)

#### [NEW] [ExportNameBuilder.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/ExportNameBuilder.java)
- Implement the template-based custom file name builder supporting `{appname}`, `{packageid}`, `{versionname}`, `{versioncode}`, and `{date}` tokens with safe character sanitization.
- Add SDK version guard for `pi.getLongVersionCode()` to safely compile and run on older APIs.

#### [MODIFY] [AppSettings.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/AppSettings.java)
- Wire up helper methods to read/write `pref_export_mode`, `pref_export_sep`, and `pref_export_tpl`.
- Replace legacy naming helper references.

#### [MODIFY] [ExportAPKTasks.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/tasks/ExportAPKTasks.java)
- Use `ExportNameBuilder` to resolve the export APK name.

#### [MODIFY] [ExportBundleTasks.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/utils/tasks/ExportBundleTasks.java)
- Use `ExportNameBuilder` to resolve the export bundle (`.apkm`/`.apks`) name.

#### [MODIFY] [SettingsFragment.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/fragments/SettingsFragment.java)
- Add preference selection dialogs for Export Mode, Separator, and Custom Template in the UI.

---

### Component: Permissions Editor & Lifecycle Safety (Task 4)

#### [MODIFY] [PermissionsFragment.java](file:///c:/Users/nukie/apps/PackageManager/app/src/main/java/com/smartpack/packagemanager/fragments/PermissionsFragment.java)
- Pass `getViewLifecycleOwner()` to `addCallback` for `OnBackPressedCallback` to properly clean up and restrict back handling to the visible fragment page state.
- Guard the RecyclerView adapter bindings with null/empty checks on permissions data.

---

### Component: Build & Dependency Management (Task 5 & Task 6)

#### [MODIFY] [gradle-wrapper.properties](file:///c:/Users/nukie/apps/PackageManager/gradle/wrapper/gradle-wrapper.properties)
- Upgrade `distributionUrl` to Gradle 8.12.

---

## Verification Plan

### Automated Tests
- Run Gradle version check: `.\gradlew.bat --version`
- Run deprecation & lint audit compile task: `.\gradlew.bat :app:compileFdroidDebugJavaWithJavac -Xlint:deprecation`
- Build the final package: `.\gradlew.bat clean assembleFdroidDebug`
