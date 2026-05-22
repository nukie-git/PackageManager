# SmartPack PackageManager — Master Fix Prompt
**Target:** `https://github.com/SmartPack/PackageManager` (your local fork)
**Environment:** Android Studio Meerkat Feature Drop Patch 1 · AGP 8.10.1 · Gradle 8.12 (upgraded from 8.11.1) · JDK 17 · SDK `C:\Users\nukie\AppData\Local\Android\Sdk`

---

## Current build status (as of last run)

| Check | Result |
|---|---|
| `assembleFdroidDebug` | ✅ BUILD SUCCESSFUL (33s, Gradle 8.12) |
| Compile errors | ✅ 0 errors |
| Gradle version | ✅ 8.12 confirmed |
| App launches without crash | ✅ Confirmed |
| App list populates | ✅ 118 items loaded correctly |
| JDK running the build | ⚠️ JDK 21 (Meerkat embedded) — see Task 5h/6b |
| Groovy DSL space-assignment | ⚠️ 8 instances — see Task 5h |
| source/target VERSION_1_8 obsolete | ⚠️ Deprecated under JDK 21 — fix in Task 6b |
| ExportNameBuilder deprecated API | ⚠️ `PackageInfo.versionCode` likely — see Task 0D ExportNameBuilder fix |
| Orphaned `tools:node="remove"` in manifests | ⚠️ `READ_PHONE_STATE` — see Task 0E |
| Double `generateData` on cold start | ⚠️ 6.2s wasted scan — see Task 0F-1 |
| Root check on main thread | ⚠️ 33 skipped frames — see Task 0F-2 |
| `MATCH_UNINSTALLED_PACKAGES` on every load | ⚠️ Dominant scan cost — see Task 0F-3 |
| Cold start shimmer duration | ⚠️ ~8s measured — target <2s after Task 0F |
| SDK version bumps | ⏳ Pending — Task 6 (minSdk 26, compileSdk 36, targetSdk 35) |
| App version bump + v-prefix fix | ⚠️ Displays "Vv7.8" — double prefix bug — see Task 6a |
| Batch select + uninstall + export | ⏳ Pending — Task 7 |
| APK Finder + move + rename | ⏳ Pending — Task 8 |
| Runtime crash — `PackageTasksFragment` NPE | ✅ Fixed |
| Runtime crash — `UninstalledAppsFragment` NPE | ✅ Fixed |

**Current priority order:** Task 0E (manifest) → Task 0F (performance) → Task 5h → Task 6 → Tasks 1–4 (features) → Task 7 (batch) → Task 8 (APK finder) → Task 9 (validation).

---



Paste this entire document into Google Antigravity. It is written as a sequential agent task list. Each task begins with an **idempotency check** — if the change is already present, skip that task entirely and move to the next. Do not re-apply already-completed work. After all tasks are done, run a **pre-build validation pass** (Task 9, last section) before attempting a debug build.

---

## Task 0 — Environment sanity check (run first, and again before build)

Before touching any source file, verify the following. Re-run this checklist after all tasks are complete.

1. Confirm `local.properties` contains:
   ```
   sdk.dir=C:\\Users\\nukie\\AppData\\Local\\Android\\Sdk
   ```
   If absent or wrong, write the correct value. Do not touch any other line.

2. Confirm `gradle/libs.versions.toml` exists and is valid TOML (no unclosed quotes, no duplicate keys). If it is missing or malformed, stop and report — do not proceed.

3. Confirm `settings.gradle` references the app module and applies the version catalog plugin. If absent, stop and report.

4. Confirm `app/src/main/AndroidManifest.xml` is well-formed XML. Parse it; if it throws a parse error, report the line number and stop.

5. Confirm `app/src/main/res/values/themes.xml` (or `styles.xml`) exists. Note the exact parent theme name currently declared — you will need it in Task 1.

6. Confirm `app/src/main/res/values/strings.xml` exists and is well-formed XML.

7. Scan for any `TODO` / `FIXME` / `//noinspection` comments that reference unresolved symbols — log them but do not block.

8. Run a symbol-resolution pass on all Java files under `app/src/main/java/`. Report any unresolved imports or missing class references before proceeding.

---

## Task 0B — ⛔ BLOCKING: Fix PackageData API mismatch (fix before all other tasks)

> **This is a build blocker.** The project produces 28 `cannot find symbol` errors on `PackageData` across 6 files. All errors are from the same root cause: `PackageData` was refactored (most likely during the v7.8 Dashboard rewrite) and 9 methods were removed or renamed, but their call sites were never updated. Nothing else in this prompt can be tested until this compiles clean.

### Idempotency check
- Attempt `./gradlew :app:compileFdroidDebugJavaWithJavac`. If it completes with 0 errors — **skip this entire task**.

### Step 0B-0 — Read PackageData.java first

Before changing anything, open `PackageData.java` and list every `public static` method currently declared in it, with its exact signature. This is your ground truth. Every fix below must map a missing call to a method that actually exists after this read — do not guess.

### Step 0B-1 — Resolve the 9 missing methods

For each missing method below, use the following decision logic:

> 1. Does a method with the **same name but a different signature** exist in `PackageData`? If yes → update the call site to match the new signature.
> 2. Does the functionality exist under a **different class** (e.g. `PackageUtils`, `AppSettings`, `APKUtils`, `SortHelper`)? If yes → update the import and call site.
> 3. Is the method **completely absent** across the whole codebase? If yes → implement a minimal stub in `PackageData` that delegates to whatever internal data it now holds, and note it clearly as a stub.

Apply the decision logic to each of the following:

---

#### Missing method 1 — `PackageData.init(Activity)`
**Call site:** `StartActivity.java:75`
```java
PackageData.init(activity);
```
**Likely cause:** `init` was renamed to accept `Context` instead of `Activity`, or initialization was moved to the `Application` class. Check if `PackageData` has an `init(Context)` or similar. If so, `Activity` is a `Context` — change the call to pass `activity` (no cast needed). If initialization is now done elsewhere, remove this call and note it.

---

#### Missing method 2 — `PackageData.isTextMatched(String, String)`
**Call sites:** `PackageTasksAdapter.java:98,106` · `Downloads.java:29,35`
```java
PackageData.isTextMatched(someString, searchText)
```
**Likely cause:** This utility method was moved to a `Utils` or `SearchUtils` class. Search the whole `java/` tree for `isTextMatched` or a `containsIgnoreCase`-style utility. If found elsewhere, update all 4 call sites. If completely absent, add it to `PackageData`:
```java
public static boolean isTextMatched(String text, String query) {
    if (text == null || query == null) return false;
    return text.toLowerCase().contains(query.toLowerCase());
}
```

---

#### Missing method 3 — `PackageData.clearAppSettings(String)`
**Call site:** `PackageInfoFragment.java:234`
```java
PackageData.clearAppSettings(mPackageName)
```
**Likely cause:** Moved to `AppSettings` or a per-package settings helper. Search for `clearAppSettings` or `clearSettings` in the codebase. Update the call site if found. If absent, add a stub to `PackageData` that clears the relevant `SharedPreferences` keys for the given package name — examine what keys are used for per-package settings to implement it correctly.

---

#### Missing method 4 — `PackageData.getBundleSize(String)`
**Call sites:** `PackageInfoFragment.java:259` · `PackageDetails.java:363`
```java
PackageData.getBundleSize(somePath)
```
**Likely cause:** Moved to `APKUtils`, `PackageUtils`, or `SplitAPKInstaller`. Search for `getBundleSize` or equivalent directory-size calculation. If found, update both call sites. If absent, add to `PackageData`:
```java
public static long getBundleSize(String dirPath) {
    if (dirPath == null) return 0L;
    java.io.File dir = new java.io.File(dirPath);
    if (!dir.exists() || !dir.isDirectory()) return 0L;
    long size = 0L;
    java.io.File[] files = dir.listFiles();
    if (files == null) return 0L;
    for (java.io.File f : files) { size += f.length(); }
    return size;
}
```

---

#### Missing method 5 — `PackageData.generateData(ProgressBar, Activity)`
**Call sites:** `PackageTasksFragment.java:238` · `PackageTasksFragment.java:253`
```java
PackageData.generateData(mProgress, activity);
```
**Likely cause:** Signature changed to `generateData(ProgressBar, Context)`, or the method was split or removed as part of the Dashboard rewrite. Check `PackageData` for any `generateData` variant. If the signature changed to accept `Context`, the call site requires no change (`Activity` is a `Context`). If the method was split or removed, find the replacement sequence of calls and update the call sites accordingly.

---

#### Missing method 6 — `PackageData.getData(String, Activity)`
**Call sites:** `PackageTasksFragment.java:239` · `PackageTasksFragment.java:258` · `PackageTasksFragment.java:780`
```java
mData = PackageData.getData(mSearchText, activity);
```
**Likely cause:** Same refactor as `generateData`. Check for `getData(String, Context)` or a split into separate calls. Update all 3 call sites consistently — they must all use the same pattern.

---

#### Missing method 7 — `PackageData.getSortingType(Activity)`
**Call sites:** `PackageTasksFragment.java` — 7 occurrences
```java
PackageData.getSortingType(activity)
```
**Likely cause:** Moved to `AppSettings`, or signature changed to `getSortingType(Context)`. Search for `getSortingType` across the codebase. If in `AppSettings`, update all 7 call sites to `AppSettings.getSortingType(activity)`. If `PackageData` now has `getSortingType(Context)`, no call-site change needed (`Activity` is a `Context`).

---

#### Missing method 8 — `PackageData.setSortingType(int, Activity)`
**Call sites:** `PackageTasksFragment.java` — 5 occurrences
```java
PackageData.setSortingType(n, activity);
```
**Likely cause:** Same as `getSortingType`. Apply the same fix consistently with method 7 — both must migrate to the same class.

---

#### Missing method 9 — `PackageData.getPackageInfo(String, Context)`
**Call sites:** `PackageDetails.java:79` · `PackageDetails.java:80`
```java
PackageData.getPackageInfo(packageName, context)
```
**Likely cause:** Moved to `PackageUtils` or replaced by a direct `PackageManager` call. Search for `getPackageInfo` in `PackageUtils` and other utility classes. If found, update both call sites. If absent, add to `PackageData`:
```java
public static android.content.pm.PackageInfo getPackageInfo(String packageName, android.content.Context context) {
    try {
        return context.getPackageManager().getPackageInfo(packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS);
    } catch (android.content.pm.PackageManager.NameNotFoundException e) {
        return null;
    }
}
```

---

### Step 0B-2 — Verify no new broken references were introduced

After all call sites and/or stubs are updated:
1. Re-read every modified file end-to-end and confirm all imports are still valid.
2. Confirm no `Activity` vs `Context` type mismatch was introduced — `Activity` can always be passed where `Context` is expected, but not vice versa.
3. Confirm the `PackageDetails.java:79` call is still wrapped in `Objects.requireNonNull()` — the null-check must be preserved around `getPackageInfo`.

### Step 0B-3 — Compile gate

Run:
```
./gradlew :app:compileFdroidDebugJavaWithJavac
```
This must exit with **0 errors** before proceeding to Task 1. If errors remain, report them — do not continue.

---

## Task 0C — ⛔ BLOCKING: Fix RecyclerView null crash in PackageTasksFragment (runtime NPE)

> **This is a runtime crash that surfaces immediately on launch.** The app compiled successfully after Task 0B but crashes at startup with:
> ```
> java.lang.NullPointerException: Attempt to invoke virtual method
> 'void androidx.recyclerview.widget.RecyclerView.setLayoutManager(...)'
> on a null object reference
>   at PackageTasksFragment.onCreateView(PackageTasksFragment.java:120)
> ```
> Root cause: `findViewById` returned `null` for the RecyclerView, meaning the ID used in the Java code does not match the ID declared in the XML layout. This issue has been seen and fixed before in this project — it may have been reintroduced by a merge or edit.

### Idempotency check
- Open `PackageTasksFragment.java` and find line 120 (or the nearby `setLayoutManager` call). Read the exact ID passed to `findViewById` — e.g. `R.id.recyclerView` or `R.id.recycler_view`.
- Open the layout file inflated in `PackageTasksFragment.onCreateView` (the file passed to `inflater.inflate(...)`). Read the exact `android:id` of the RecyclerView element.
- If the two IDs already match — **skip this entire task**.

### Step 0C-1 — Identify the inflated layout

In `PackageTasksFragment.onCreateView`, find the `inflater.inflate(R.layout.XXXX, ...)` call. Note the exact layout filename — it will be something like `fragment_package_tasks.xml` or `recycler_view.xml`. Open that file.

### Step 0C-2 — Read both IDs

| Location | ID value found |
|---|---|
| XML layout `android:id` | record it |
| Java `findViewById(R.id.???)` | record it |

The canonical correct ID is whatever the XML layout declares. The Java code must be updated to match the XML — **do not change the XML**, as it may be referenced by other layouts or data binding.

### Step 0C-3 — Fix the Java side

In `PackageTasksFragment.java`, find every `findViewById` call that references the RecyclerView and update each one to use the ID exactly as declared in the XML layout.

Typical fix looks like one of these two:
```java
// If XML says android:id="@+id/recycler_view"  (underscore)
RecyclerView recyclerView = view.findViewById(R.id.recycler_view);

// If XML says android:id="@+id/recyclerView"  (camelCase)
RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
```

Do not rename the variable — only fix the resource ID string.

### Step 0C-4 — Scan for the same mismatch in other fragments

This type of ID mismatch often affects multiple fragments in the same project when a layout was renamed or regenerated. Search all Java files under `app/src/main/java/` for `findViewById(R.id.recyclerView)` **and** `findViewById(R.id.recycler_view)`. For each hit, cross-check against the layout it inflates and confirm the IDs match. Fix any additional mismatches found using the same approach as Step 0C-3.

Fragments to check specifically (confirmed or likely affected):
- `PackageTasksFragment` — confirmed crash at `onCreateView:120`
- `UninstalledAppsFragment` — confirmed crash at `onCreateView:82`
- `PackageInfoFragment`
- `PermissionsFragment` (if present)
- Any other Fragment whose layout contains a `RecyclerView`

> **Note:** Two confirmed crashes from two separate fragments mean this is a project-wide ID inconsistency, not an isolated typo. Treat the full scan as mandatory — do not stop after fixing only the two confirmed sites.

### Step 0C-5 — Runtime gate

Install the debug APK on the test device (Xiaomi Redmi Note 9 Pro, Android 12 / MIUI 14) and confirm:
1. The app launches without crashing.
2. `PackageTasksFragment` loads and displays its list (even if empty).
3. No further `NullPointerException` appears in logcat for this fragment.

If the app still crashes on launch after this fix, capture the new stack trace and report it before proceeding to Task 1.

---

## Task 0D — ⛔ BLOCKING: App list does not populate on launch

> **Confirmed symptom:** The app launches and the RecyclerView displays but remains empty. The original upstream app populates the installed apps list immediately on start. This regressed during Task 0B.
>
> **Root cause:** When Task 0B resolved the 28 compile errors, one or more of the three data-pipeline methods — `init`, `generateData`, `getData` — was either stubbed with an empty body or wired to the wrong replacement. The app compiles and runs, but the data loading chain is silently broken.

### Idempotency check
- Launch the app on the test device. If the installed apps list populates within a few seconds of launch — **skip this entire task**.

---

### Step 0D-0 — Full stub audit across all 9 methods from Task 0B

Before diagnosing the data pipeline specifically, audit **every one of the 9 methods** that Task 0B was responsible for. A stub is any method that:
- Has an empty body `{}`
- Returns `null`, `0`, `false`, `""`, or `new ArrayList<>()` unconditionally with no logic
- Contains only a `// TODO` or `// stub` comment
- Delegates to a method that is itself a stub (transitive stub)

For each method below, open the class it lives in and read the full body. Record actual vs expected in a table like this before touching anything:

| # | Method | Expected behaviour | Stub risk if empty |
|---|---|---|---|
| 1 | `init(Activity)` | Triggers package scanning / initialises data pipeline | 🔴 App list never loads |
| 2 | `generateData(ProgressBar, Activity)` | Queries PackageManager off main thread, fills internal list | 🔴 App list never loads |
| 3 | `getData(String, Activity)` | Returns filtered list from internal cache | 🔴 App list always empty |
| 4 | `isTextMatched(String, String)` | Returns true if text contains query (case-insensitive) | 🟡 Search never matches anything |
| 5 | `getSortingType(Activity)` | Reads sort preference int from SharedPreferences | 🟡 Sort always defaults to 0, preference ignored |
| 6 | `setSortingType(int, Activity)` | Writes sort preference int to SharedPreferences | 🟡 Sort preference never saved across sessions |
| 7 | `clearAppSettings(String)` | Removes per-package preference keys for the given package | 🟡 App-specific settings never reset |
| 8 | `getBundleSize(String)` | Returns total byte size of all files in a directory | 🟠 Bundle/split APK size always shows 0 |
| 9 | `getPackageInfo(String, Context)` | Returns PackageInfo with GET_PERMISSIONS flag | 🔴 Permissions editor shows nothing or crashes |

Report the completed table before making any changes. Then apply the targeted fixes below for each confirmed stub.

---

#### Stub fix — `isTextMatched(String, String)` (method 4)

If this method was stubbed (returns `false` unconditionally), replace with:
```java
public static boolean isTextMatched(String text, String query) {
    if (text == null || query == null) return false;
    return text.toLowerCase().contains(query.toLowerCase());
}
```
Search uses: `PackageTasksAdapter.java`, `Downloads.java`. Confirm both use this method and that search now filters correctly.

---

#### Stub fix — `getSortingType(Activity)` and `setSortingType(int, Activity)` (methods 5 & 6)

These must be fixed together — they must read/write the **same** SharedPreferences key. If either or both were stubbed:

```java
private static final String SORT_PREF_KEY = "pref_sorting_type";

public static int getSortingType(Activity activity) {
    return androidx.preference.PreferenceManager
        .getDefaultSharedPreferences(activity)
        .getInt(SORT_PREF_KEY, 0);
}

public static void setSortingType(int type, Activity activity) {
    androidx.preference.PreferenceManager
        .getDefaultSharedPreferences(activity)
        .edit()
        .putInt(SORT_PREF_KEY, type)
        .apply();
}
```

Check whether `SORT_PREF_KEY` is already declared as a constant elsewhere (e.g. `AppSettings.java`). If so, use that constant instead of introducing a new one.

---

#### Stub fix — `clearAppSettings(String)` (method 7)

If stubbed, examine what per-package preference keys are used in `AppSettings.java` or similar. The method should remove all preferences scoped to the given `packageName`. A safe minimal implementation:

```java
public static void clearAppSettings(String packageName) {
    // Replace with actual per-package key prefix used in the project
    // Example pattern — adapt to whatever prefix the project uses:
    android.content.SharedPreferences prefs =
        androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(/* stored context or passed context */);
    android.content.SharedPreferences.Editor editor = prefs.edit();
    for (String key : prefs.getAll().keySet()) {
        if (key.startsWith(packageName + "_")) {
            editor.remove(key);
        }
    }
    editor.apply();
}
```

If no per-package key prefix pattern is found in the codebase, log a warning and leave the method as a no-op — but mark it with a `// FIXME: no per-package keys found` comment so it is visible.

---

#### Stub fix — `getBundleSize(String)` (method 8)

If stubbed (returns `0L`), replace with:
```java
public static long getBundleSize(String dirPath) {
    if (dirPath == null) return 0L;
    java.io.File dir = new java.io.File(dirPath);
    if (!dir.exists() || !dir.isDirectory()) return 0L;
    long size = 0L;
    java.io.File[] files = dir.listFiles();
    if (files == null) return 0L;
    for (java.io.File f : files) {
        size += f.length();
    }
    return size;
}
```

---

#### Stub fix — `getPackageInfo(String, Context)` (method 9)

If stubbed (returns `null` unconditionally), replace with:
```java
public static android.content.pm.PackageInfo getPackageInfo(
        String packageName, android.content.Context context) {
    try {
        return context.getPackageManager().getPackageInfo(
            packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS);
    } catch (android.content.pm.PackageManager.NameNotFoundException e) {
        return null;
    }
}
```

Confirm the two call sites in `PackageDetails.java:79` and `PackageDetails.java:80` are still wrapped in `Objects.requireNonNull()` — the null guard must be preserved.

---

#### ExportNameBuilder.java — deprecated API fix (confirmed in problems report)

The compiler reports `ExportNameBuilder.java uses or overrides a deprecated API`. Run:
```
./gradlew :app:compileFdroidDebugJavaWithJavac -Xlint:deprecation 2>&1
```
The most likely offender is a direct read of `PackageInfo.versionCode` (the `int` field, deprecated since API 28). Find it in `ExportNameBuilder.java` and replace with a version-guarded call:

```java
// Before (deprecated)
long vCode = pi.versionCode;

// After (API-guarded)
long vCode = (android.os.Build.VERSION.SDK_INT >= 28)
    ? pi.getLongVersionCode()
    : pi.versionCode;
```

If `-Xlint:deprecation` reveals a different deprecated call, fix that one instead — do not assume. After fixing, re-run `compileFdroidDebugJavaWithJavac` and confirm the `ADVICE` note for `ExportNameBuilder.java` is gone from the output.

---



#### `init` (or its equivalent)
Find the method that was mapped to the `PackageData.init(Activity)` call site in `StartActivity.java:75`. Answer:
- Does it actually trigger any package scanning, `PackageManager` query, or background thread/executor?
- Or is it an empty body `{}`?

#### `generateData` (or its equivalent)
Find the method mapped to `PackageData.generateData(ProgressBar, Activity)`. Answer:
- Does it actually call `context.getPackageManager().getInstalledPackages(...)` or `getInstalledApplications(...)` or any equivalent query?
- Does it populate any list, cache, or field inside `PackageData`?
- Or is it an empty body or a return of `null`/empty list?

#### `getData` (or its equivalent)
Find the method mapped to `PackageData.getData(String, Activity)`. Answer:
- Does it return a populated `List` from the cache filled by `generateData`?
- Or does it return `null`, `new ArrayList<>()`, or some other empty value unconditionally?

Report the findings for all three before making any changes.

---

### Step 0D-2 — Trace the original data-loading sequence

Open the **git history** or the upstream source for `PackageData.java` to understand the original loading sequence. If git history is unavailable, search the rest of the codebase for the following patterns to reconstruct it:

1. Search for `getInstalledPackages` and `getInstalledApplications` across all `.java` files. Identify which class/method performs the actual `PackageManager` query.
2. Search for `PackageInfo` list construction — where is the master list of installed apps assembled?
3. Search for `ProgressBar` usage in background threads — this is the progress indicator passed to `generateData`, meaning the data load is expected to happen off the main thread.
4. Search for `ExecutorService`, `Thread`, `AsyncTask`, `Handler`, or `Callable` usages in `PackageData` or any class it delegates to.

The goal is to find where the real scanning work happens so the stub can be replaced with a real delegation.

---

### Step 0D-3 — Reconstruct the data pipeline

Based on the findings from Steps 0D-1 and 0D-2, apply the appropriate fix:

#### Scenario A — Methods were stubbed empty, real logic exists elsewhere
If the real package scanning logic was found in another class (e.g. a new `AppRepository`, `PackageRepository`, `DashboardViewModel`, or `AppLoader`), update the stub bodies to delegate to that class:

```java
// Example — adapt to actual class and method names found
public static void generateData(ProgressBar progress, Activity activity) {
    // Replace with actual delegation found in Step 0D-2
    RealDataClass.loadPackages(progress, activity);
}

public static List<AppDataItem> getData(String searchText, Activity activity) {
    // Replace with actual retrieval found in Step 0D-2
    return RealDataClass.getFilteredList(searchText, activity);
}
```

#### Scenario B — Logic was deleted entirely with no replacement
If no equivalent scanning logic exists anywhere in the codebase, implement it directly in `PackageData`. The following is a reference implementation — adapt field types and list types to match what the rest of `PackageData` already uses:

```java
// Internal cache — match the type already used in PackageData for the app list
private static List<PackageInfo> sPackageList = new ArrayList<>();

public static void init(Activity activity) {
    // Nothing needed at init time if generateData is called from the Fragment.
    // If PackageData held a Context reference before, store it here:
    // sContext = activity.getApplicationContext();
}

public static void generateData(ProgressBar progress, Activity activity) {
    // Must run off the main thread — use an Executor
    java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newSingleThreadExecutor();
    android.os.Handler handler = new android.os.Handler(
        android.os.Looper.getMainLooper());

    executor.execute(() -> {
        if (progress != null) {
            handler.post(() -> progress.setVisibility(android.view.View.VISIBLE));
        }

        // Query all installed packages
        sPackageList = activity.getPackageManager()
            .getInstalledPackages(android.content.pm.PackageManager.GET_META_DATA);

        if (progress != null) {
            handler.post(() -> progress.setVisibility(android.view.View.GONE));
        }
    });
}

public static List<PackageInfo> getData(String searchText, Activity activity) {
    if (searchText == null || searchText.isEmpty()) {
        return new ArrayList<>(sPackageList);
    }
    List<PackageInfo> filtered = new ArrayList<>();
    for (PackageInfo pi : sPackageList) {
        String label = "";
        try {
            label = activity.getPackageManager()
                .getApplicationLabel(pi.applicationInfo).toString();
        } catch (Exception ignored) {}
        if (isTextMatched(label, searchText)
                || isTextMatched(pi.packageName, searchText)) {
            filtered.add(pi);
        }
    }
    return filtered;
}
```

> ⚠️ **Important:** Check the existing field type used for the package list in `PackageData` before adding `sPackageList`. If the class already has a list field (even if populated by a different method), reuse it — do not introduce a second parallel list.

#### Scenario C — generateData runs but the Fragment never calls it
If `generateData` is real and functional but `PackageTasksFragment` no longer calls it on load, find the point in `PackageTasksFragment` where data loading should be triggered (typically in `onViewCreated` or a background loader started from `onViewCreated`) and ensure the call sequence is:
```java
PackageData.generateData(mProgress, requireActivity());
mData = PackageData.getData(mSearchText, requireActivity());
mAdapter.notifyDataSetChanged(); // or equivalent adapter refresh
```

---

### Step 0D-4 — Verify system apps vs user apps separation

The original app has separate tabs/views for system apps and user apps. Confirm that `generateData` / `getData` (or their real equivalents) correctly separate packages by checking `ApplicationInfo.FLAG_SYSTEM`:

```java
boolean isSystemApp = (pi.applicationInfo.flags
    & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
```

If the filter is missing or wrong, all apps may load into one list instead of the correct tab, making it appear as if no apps loaded in the other tab.

---

### Step 0D-5 — Confirm StartActivity wires init correctly

Open `StartActivity.java`. Confirm that around line 75, the `init` call (or its replacement) is made **before** any Fragment transaction that shows `PackageTasksFragment`. If `PackageTasksFragment` is shown before initialization completes, the list will be empty on first load.

If initialization is asynchronous (runs in a background thread), confirm there is a callback, observer, or `LiveData` notification that triggers the Fragment to refresh its adapter once loading is complete. If no such mechanism exists, add one — a simple `Handler.post()` from the background thread back to the UI thread to call `mAdapter.notifyDataSetChanged()` is sufficient.

---

### Step 0D-6 — Runtime gate

Install the updated debug APK. Confirm:
1. The app launches without crashing.
2. Within 3–5 seconds, the installed apps list populates with actual installed packages.
3. Both system apps and user apps appear in their respective views.
4. Search filters the list correctly.
5. No logcat errors during or after list population.

If the list still does not populate, run `adb logcat -s PackageManager:V` during launch and report any errors or exceptions from the data-loading thread.

---

## Task 0E — Fix orphaned manifest merge directives

> **Confirmed warning (Pro variants):**
> ```
> AndroidManifest.xml:12: Warning:
>   uses-permission#android.permission.READ_PHONE_STATE was tagged at
>   AndroidManifest.xml:12 to remove other declarations but no other
>   declaration present
> ```
> A `tools:node="remove"` directive exists for `READ_PHONE_STATE` in the main manifest but no dependency or flavor manifest currently declares that permission — so the removal has nothing to act on. AGP logs this as a warning. Left unfixed, it will grow into a manifest merge error in a future AGP version.

### Idempotency check
- Open `app/src/main/AndroidManifest.xml` line 12. If no `tools:node="remove"` attribute is present on any `<uses-permission>` line — **and** the full scan below has already been run — **skip this entire task**.

---

### Step 0E-1 — Fix the confirmed instance

Open `app/src/main/AndroidManifest.xml`. Find the entry around line 12:
```xml
<uses-permission
    android:name="android.permission.READ_PHONE_STATE"
    tools:node="remove" />
```

**Decision logic — apply in order:**

1. Search all declared dependencies in `libs.versions.toml` and `app/build.gradle`. For each dependency, check if its published AAR manifest declares `READ_PHONE_STATE`. If any dependency still declares it, the `tools:node="remove"` is legitimate — **leave it in place** and close this step.

2. If no dependency declares it — the directive is fully orphaned. **Remove the entire `<uses-permission>` block** for `READ_PHONE_STATE`. Do not replace it with a plain declaration; the permission is not needed by this app.

3. After removal, confirm that no remaining `<uses-permission>` block in the main manifest uses `tools:node="remove"` unless a corresponding dependency declaration for that permission has been confirmed.

---

### Step 0E-2 — Scan all manifest files for the same pattern

Locate every `AndroidManifest.xml` in the project. The typical locations are:

```
app/src/main/AndroidManifest.xml
app/src/fdroid/AndroidManifest.xml       ← if present
app/src/pro/AndroidManifest.xml          ← if present
app/src/debug/AndroidManifest.xml        ← if present
app/src/release/AndroidManifest.xml      ← if present
app/src/androidTest/AndroidManifest.xml  ← if present
```

For each manifest found, search for every occurrence of **all three** manifest merge directives:
- `tools:node="remove"`
- `tools:node="replace"`
- `tools:node="merge"`

For each occurrence found, apply the same decision logic as Step 0E-1:
- Does a corresponding declaration exist in a dependency or another manifest that this directive is intended to act on?
- If yes — leave it. Document it in a comment above the line: `<!-- Removes X declared by dependency Y -->`.
- If no — remove the entire element. Do not convert it to a plain declaration unless the permission or element is actually required by the app.

Report every file and line where a directive was found, its decision (kept or removed), and the reason.

---

### Step 0E-3 — Verify the warning is gone

Run:
```
./gradlew :app:processProDebugMainManifest :app:processProReleaseMainManifest 2>&1
```

Confirm zero `tools:node` warnings in the output. If new warnings surface from a different permission or element, apply the same decision logic and report before fixing.

---

### Step 0E-4 — Check tools namespace declaration

If all `tools:node` directives have been removed from a manifest file, confirm whether the `xmlns:tools` namespace declaration at the top of that file is still needed by any remaining `tools:` attribute in the file (e.g. `tools:ignore`, `tools:replace` on layout attributes). If no `tools:` attributes remain, remove the `xmlns:tools` declaration from the root element to keep the manifest clean.

---

## Task 0F — Performance: eliminate double scan, move root check off main thread

> **Confirmed from logcat (2026-05-20 23:25:52 – 23:26:00):**
> - `generateData` runs **twice** on every cold start — once from `StartActivity` (~6.2s) and again from `loadUI` in `PackageTasksFragment` (~478ms). The second run ignores the first run's result entirely.
> - `RootShell.rootAccess()` is called synchronously from `PackageTasksFragment.onCreateView` line 128 on the **main thread**, causing 33 skipped frames.
> - `getInstalledPackages(MATCH_UNINSTALLED_PACKAGES)` scans all 441 packages including uninstalled ones for every load, which is the dominant cost.
> - Total time from process start to list populated: **~8 seconds**. This is the shimmer duration.

### Idempotency check
- Open `PackageData.java`. If `generateData` stores its result in a non-null static field **and** `loadUI` / `PackageTasksFragment` checks that field before triggering a new scan **and** `RootShell.rootAccess()` is not called from any Fragment's `onCreateView` — **skip this entire task**.

---

### Step 0F-1 — Eliminate the double scan

The first `generateData` call (from `StartActivity`) completes successfully and stores 416 packages in the internal cache. The second call from `PackageTasksFragment.loadUI` logs `"First load, regenerating package data in background"` — meaning it doesn't check whether the cache is already populated.

Fix: add a populated-guard at the top of `generateData`:

```java
public static void generateData(ProgressBar progress, Activity activity) {
    // Skip if already populated — avoid redundant full scan on every fragment load
    if (mRawData != null && !mRawData.isEmpty()) {
        // Cache is warm — notify UI directly without rescanning
        if (progress != null) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> progress.setVisibility(android.view.View.GONE));
        }
        return;
    }
    // ... existing scan logic follows
}
```

Replace `mRawData` with whatever the actual internal list field is named in `PackageData`. Read the field name before writing this guard.

Also check whether `StartActivity` actually needs to call `generateData` at all, or whether that call is redundant. If `PackageTasksFragment` already calls it on first load, the `StartActivity` call may be a leftover from the pre-refactor architecture. If so, remove the `StartActivity` call and rely solely on the Fragment-triggered scan — this eliminates the 6.2s blocking scan during the splash screen entirely.

---

### Step 0F-2 — Move root check off the main thread

`RootShell.rootAccess()` is called from `PackageTasksFragment.onCreateView` at line 128. This is an I/O operation (attempting to exec `su`) and must not run on the main thread. It caused 33 skipped frames on cold start.

Find the call:
```java
// In PackageTasksFragment.onCreateView — somewhere around line 128
boolean hasRoot = RootShell.rootAccess(); // ← MAIN THREAD — wrong
```

Move it to a background thread and update UI state via `Handler.post` or `requireActivity().runOnUiThread`:

```java
// Replace the synchronous call with:
new Thread(() -> {
    final boolean hasRoot = RootShell.rootAccess();
    requireActivity().runOnUiThread(() -> {
        // Apply whatever UI state depends on root access
        // e.g. show/hide root-only buttons, update menu items
        applyRootUiState(hasRoot);
    });
}).start();
```

If `rootAccess()` result is needed before the view is shown (e.g. to decide which tabs to show), move the check earlier to `Application.onCreate` or `StartActivity.onCreate` on a background thread, storing the result in a static field that the Fragment reads without blocking.

---

### Step 0F-3 — Defer uninstalled packages scan

`getInstalledPackages(MATCH_UNINSTALLED_PACKAGES)` fetches all 441 packages including APEXes, stubs, and uninstalled-but-retained packages. This is necessary for the "Uninstalled Apps" tab but not for the initial "User Apps" or "System Apps" tabs.

Split the scan into two phases:

**Phase 1 — fast initial load (no `MATCH_UNINSTALLED_PACKAGES`):**
```java
// Fetch only currently installed packages — much faster
List<PackageInfo> installed = context.getPackageManager()
    .getInstalledPackages(PackageManager.GET_META_DATA);
// Store in mRawData immediately → UI can render
```

**Phase 2 — lazy uninstalled load (triggered only when Uninstalled tab is opened):**
```java
// Only fetch uninstalled packages when that tab is actually selected
List<PackageInfo> withUninstalled = context.getPackageManager()
    .getInstalledPackages(PackageManager.GET_META_DATA
        | PackageManager.MATCH_UNINSTALLED_PACKAGES);
// Diff against mRawData to extract removed packages → store in mRemovedData
```

Wire Phase 2 to trigger when the user navigates to the Uninstalled Apps tab (the Fragment or page that shows `mRemovedData`), not on every cold start.

> **Note:** On the Redmi Note 9 Pro (MIUI 14), `MATCH_UNINSTALLED_PACKAGES` appears to re-parse manifests for all 441 packages including system stubs, which accounts for most of the 6.2s first-scan cost. Deferring this alone should reduce cold start shimmer from ~8s to under 1s.

---

### Step 0F-4 — Progressive list display

Instead of waiting for the full scan to complete before showing any items, update the adapter incrementally:

```java
// In the background scan thread, after fetching installed packages:
final List<AppDataItem> earlyBatch = buildDisplayList(installed);
requireActivity().runOnUiThread(() -> {
    mData.clear();
    mData.addAll(earlyBatch);
    mAdapter.notifyDataSetChanged();
    // Hide shimmer here — list shows even while uninstalled scan runs
    hideShimmer();
});
// Continue with Phase 2 (uninstalled) scan...
```

The shimmer should hide as soon as installed packages are ready, not after uninstalled packages are processed too.

---

### Step 0F-5 — Runtime gate

Install the updated APK and measure with logcat:
1. Cold start to list visible — should be under 2 seconds.
2. `generateData` should appear exactly **once** per cold start in logcat, not twice.
3. No `Skipped N frames` warning from the root check.
4. `loadUI: First load, regenerating package data in background` should either not appear (cache hit) or appear only once.
5. Uninstalled Apps tab still works correctly when opened.

---

## Task 1 — Material 3 DayNight theme + Auto/Light/Dark toggle

### Idempotency check
- If `app/src/main/res/values/themes.xml` already inherits from `Theme.MaterialComponents.DayNight` or `Theme.Material3.DayNight` **and** `strings.xml` already contains `theme_auto`, `theme_light`, `theme_dark` **and** a `ThemeHelper` or equivalent class already exists under the Java package — **skip this entire task**.

### What to do

#### 1a. Update the app theme to DayNight

Open `app/src/main/res/values/themes.xml` (or `styles.xml`, whichever declares the main app theme). Change the parent to:
```xml
parent="Theme.MaterialComponents.DayNight.NoActionBar"
```
If the project already imports Material3, use `Theme.Material3.DayNight.NoActionBar` instead. Do not change any other attributes — preserve all existing items.

If a separate `res/values-night/themes.xml` exists, verify it also inherits a DayNight parent. If it conflicts (e.g. hardcoded dark color overrides that would break light mode), reconcile it by removing the night-specific file and letting DayNight handle it automatically — but only if no bespoke night colors are defined there. If bespoke colors exist, keep the night file and only fix the parent.

#### 1b. Add theme preference strings

Open `app/src/main/res/values/strings.xml`. Check if these entries already exist before adding:
```xml
<string name="theme_setting">App theme</string>
<string name="theme_auto">Follow system</string>
<string name="theme_light">Light</string>
<string name="theme_dark">Dark</string>
```
Append them just before `</resources>` if missing.

#### 1c. Add theme preference constant

Find the class that holds `SharedPreferences` keys (likely `AppSettings.java` or a `Utils` / `PackageUtils` class). Add if not already present:
```java
public static final String PREF_APP_THEME = "pref_app_theme";
// Values: "auto" | "light" | "dark"
```

#### 1d. Create ThemeHelper.java

Check if a `ThemeHelper.java` (or equivalent) already exists anywhere under `app/src/main/java/`. If it does, skip creating it. If not, create it at the same package level as the main Activity:

```java
package <REPLACE_WITH_ACTUAL_PACKAGE>;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public class ThemeHelper {

    public static void applyTheme(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String theme = prefs.getString(AppSettings.PREF_APP_THEME, "auto");
        switch (theme) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
```

Replace `<REPLACE_WITH_ACTUAL_PACKAGE>` with the actual package name from `AndroidManifest.xml`.

#### 1e. Call ThemeHelper on app start

Find the Application class (subclass of `Application` or `MultiDexApplication`). If one doesn't exist, find the main Activity's `onCreate`. In `onCreate`, add as the very first line (before `super.onCreate()` is fine, or immediately after):
```java
ThemeHelper.applyTheme(this);
```
Check if this call already exists before adding.

#### 1f. Wire up the settings UI

Find the Settings Fragment or Activity that contains the "User Interface" section. This is the fragment that already has `user_interface` as a category key. Add a theme selection dialog there. The simplest approach compatible with the existing Java UI pattern is:

1. Find where other preference items under "User Interface" are built programmatically (look for `addPreference`, `setOnPreferenceClickListener`, or a preference XML under `res/xml/`).
2. Add a `ListPreference` or a dialog that presents the three options (`theme_auto`, `theme_light`, `theme_dark`).
3. On selection, call `ThemeHelper.applyTheme(getContext())` and then `requireActivity().recreate()` to apply immediately.

If the settings screen uses a preference XML (`res/xml/preferences.xml` or similar), add:
```xml
<ListPreference
    app:key="pref_app_theme"
    app:title="@string/theme_setting"
    app:entries="@array/theme_entries"
    app:entryValues="@array/theme_values"
    app:defaultValue="auto"
    app:useSimpleSummaryProvider="true" />
```

And add the arrays to `res/values/arrays.xml` (create if absent):
```xml
<string-array name="theme_entries">
    <item>@string/theme_auto</item>
    <item>@string/theme_light</item>
    <item>@string/theme_dark</item>
</string-array>
<string-array name="theme_values">
    <item>auto</item>
    <item>light</item>
    <item>dark</item>
</string-array>
```

#### 1g. Verify color resources for both modes

Check `res/values/colors.xml`. If hardcoded hex colors are used that would break in night mode (e.g., `#FFFFFF` for backgrounds assigned directly to layouts instead of through the theme), flag them. Do not silently recolor — report them so they can be reviewed. Only change colors that are explicitly assigned to the theme attributes in `themes.xml`, not scattered layout colors.

---

## Task 2 — ExportNameBuilder integration (Issue #166)

### Idempotency check
- If `ExportNameBuilder.java` already exists anywhere under `app/src/main/java/`, **and** `AppSettings.java` already contains `PREF_EXPORT_MODE`, `PREF_EXPORT_SEP`, `PREF_EXPORT_TPL` **and** the export flow already calls `ExportNameBuilder` — **skip this entire task**.

### What to do

#### 2a. Locate the export flow

Find the method that currently constructs the exported APK filename. It will likely be in a class like `PackageUtils.java`, `APKData.java`, or similar, and will produce a filename like `<packageName>.apk` or `<appName>.apk`. Note the exact class and method name.

#### 2b. Add preference constants to AppSettings

In `AppSettings.java` (or wherever other `PREF_*` constants live), add if not present:
```java
public static final String PREF_EXPORT_MODE = "pref_export_mode";
// Values: "appname" | "packageid" | "custom"

public static final String PREF_EXPORT_SEP  = "pref_export_sep";
// Default: "_"  — separator used between template tokens

public static final String PREF_EXPORT_TPL  = "pref_export_tpl";
// Default: "{appname}_{packageid}_{versionname}"
// Supported tokens: {appname} {packageid} {versionname} {versioncode} {date}
```

#### 2c. Create ExportNameBuilder.java

Check if this file already exists. If not, create it in the same package as `AppSettings`:

```java
package <REPLACE_WITH_ACTUAL_PACKAGE>;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import androidx.preference.PreferenceManager;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ExportNameBuilder {

    private static final String DEFAULT_TEMPLATE = "{appname}_{versionname}";
    private static final String DEFAULT_SEP = "_";

    public static String build(Context context, PackageInfo pi, String appLabel) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String mode = prefs.getString(AppSettings.PREF_EXPORT_MODE, "appname");
        String sep  = sanitizeSep(prefs.getString(AppSettings.PREF_EXPORT_SEP, DEFAULT_SEP));
        String tpl  = prefs.getString(AppSettings.PREF_EXPORT_TPL, DEFAULT_TEMPLATE);

        String name;
        switch (mode) {
            case "packageid":
                name = pi.packageName;
                break;
            case "custom":
                name = resolveTemplate(tpl, pi, appLabel, sep);
                break;
            default: // "appname"
                name = (appLabel != null && !appLabel.isEmpty()) ? appLabel : pi.packageName;
                break;
        }
        return sanitizeFilename(name);
    }

    private static String resolveTemplate(String tpl, PackageInfo pi, String appLabel, String sep) {
        String date = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        String versionName = (pi.versionName != null) ? pi.versionName : "unknown";
        String label = (appLabel != null && !appLabel.isEmpty()) ? appLabel : pi.packageName;

        return tpl
            .replace("{appname}",     label)
            .replace("{packageid}",   pi.packageName)
            .replace("{versionname}", versionName)
            .replace("{versioncode}", String.valueOf(pi.getLongVersionCode()))
            .replace("{date}",        date)
            .replace("{sep}",         sep);
    }

    private static String sanitizeSep(String sep) {
        if (sep == null || sep.isEmpty()) return DEFAULT_SEP;
        // Allow only safe filename characters as separators
        return sep.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.trim().isEmpty()) return "export";
        return name.trim().replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}
```

#### 2d. Integrate into the export path

In the export method identified in step 2a, replace the current filename construction logic with:
```java
String exportName = ExportNameBuilder.build(context, packageInfo, appLabel) + ".apk";
```
Make sure `context`, `packageInfo` (`PackageInfo`), and `appLabel` (the human-readable label string) are available in scope. If `appLabel` is not in scope, retrieve it with:
```java
String appLabel = context.getPackageManager()
    .getApplicationLabel(packageInfo.applicationInfo).toString();
```

For split APK / bundle exports, append `.apks` / `.apkm` / `.xapk` instead of `.apk` as appropriate.

#### 2e. Add export naming UI in Settings

Under the existing "exported_apps_name" (`Exported APK Name`) settings area (confirmed present in `strings.xml`), add UI to select the export mode and configure the separator/template. Follow the same pattern used for the theme toggle in Task 1f. A simple `ListPreference` for mode plus an `EditTextPreference` for template is sufficient.

---

## Task 3 — Null-safe sorting fix (Issues #155 / #156)

### Idempotency check
- Find every `Comparator` or `Collections.sort` / `List.sort` call that compares app labels or package names. If every comparator already uses a null-safe comparison (e.g. `Objects.requireNonNullElse`, null checks, or `Comparator.nullsFirst/Last`) — **skip this task**.

### What to do

#### 3a. Locate all sort comparators

Search the entire `app/src/main/java/` tree for:
- `Comparator`
- `Collections.sort`
- `List.sort`
- `.compareTo(`
- `compareToIgnoreCase(`

For each hit, check whether the left-hand and right-hand values being compared are guarded against `null`.

#### 3b. Apply null-safe comparisons

For any comparator that calls `.getLabel()`, `.loadLabel()`, `.name`, or any string field that can be null on a partially-loaded `PackageInfo`, wrap as follows:

**Before (unsafe):**
```java
return a.getLabel().compareToIgnoreCase(b.getLabel());
```

**After (safe):**
```java
String labelA = (a.getLabel() != null) ? a.getLabel() : a.packageName;
String labelB = (b.getLabel() != null) ? b.getLabel() : b.packageName;
return labelA.compareToIgnoreCase(labelB);
```

Or, if the project targets API 24+, use:
```java
import java.util.Objects;
// ...
String labelA = Objects.requireNonNullElse(a.getLabel(), a.packageName);
String labelB = Objects.requireNonNullElse(b.getLabel(), b.packageName);
return labelA.compareToIgnoreCase(labelB);
```

Apply this pattern to **every** comparator found — do not patch only one.

#### 3c. Guard size/date comparators too

For sort modes comparing `size`, `installDate`, or `updateDate`, guard against null `PackageInfo` fields. A null `lastUpdateTime` or zero `firstInstallTime` should sort to the end of the list, not crash:
```java
long timeA = (pi_a != null) ? pi_a.lastUpdateTime : 0L;
long timeB = (pi_b != null) ? pi_b.lastUpdateTime : 0L;
return Long.compare(timeA, timeB);
```

---

## Task 4 — Permissions editor crash after prior crash (Issue #158)

### Idempotency check
- Find the code path that opens the permissions editor Fragment/Activity. If it already has a `try/catch` around the Fragment transaction **and** checks `isAdded()` / `isStateSaved()` before the transaction — **skip this task**.

### What to do

#### 4a. Locate the permissions editor entry point

Search for the Fragment or Activity that shows app permissions (likely named `PermissionsFragment`, `AppPermissionsActivity`, or similar). Find where it is launched — the `FragmentTransaction` or `startActivity` call.

#### 4b. Guard the Fragment transaction

Wrap the Fragment transaction that opens the permissions screen:

```java
if (getActivity() != null
        && !getActivity().isFinishing()
        && !getActivity().isDestroyed()
        && !isStateSaved()) {
    // existing fragment transaction here
} else {
    return; // Activity is in a bad state — silently abort
}
```

If it is an Activity start (`startActivity`), guard with:
```java
try {
    startActivity(intent);
} catch (Exception e) {
    // Log but do not crash — the activity may be in a bad post-crash state
    e.printStackTrace();
}
```

#### 4c. Ensure permissions data is non-null before binding

Find where the permissions list is populated and bound to its RecyclerView adapter. Add a null/empty guard before the adapter is set:
```java
if (permissionsList == null || permissionsList.isEmpty()) {
    // Show an empty state view or toast, not a crash
    return;
}
```

#### 4d. Check for stale back-stack entries

If the crash recovery leaves a stale Fragment on the back stack, the next open will find it in a destroyed state. Add to the `onResume` of the parent Fragment/Activity:
```java
Fragment stale = getChildFragmentManager()
    .findFragmentByTag("permissions_fragment"); // use actual tag
if (stale != null && !stale.isAdded()) {
    getChildFragmentManager().beginTransaction().remove(stale).commitAllowingStateLoss();
}
```

---

## Task 5 — Gradle upgrade: 8.11.1 → 8.12

### Idempotency check
- Open `gradle/wrapper/gradle-wrapper.properties`. If `distributionUrl` already contains `gradle-8.12-bin.zip` or `gradle-8.12-all.zip` **and** `gradle.properties` already contains `org.gradle.java.home` pointing at a JDK 17 installation **and** `settings.gradle` and `app/build.gradle` contain no remaining instances of space-assignment syntax (i.e. every `url`, `namespace`, `compileSdk`, `targetSdk`, `shrinkResources`, `aidl`, `abortOnError` already uses `= ` assignment) — **skip this entire task**.

### Why this is safe
Gradle 8.12 is a minor release on the 8.x line. It is fully compatible with AGP 8.10.1 and JDK 17. No AGP version change is needed. No source code changes are required — this is a wrapper-only upgrade.

> ⚠️ **CRITICAL — do not accept IDE upgrade suggestions:** Android Studio may prompt to upgrade Gradle further (e.g. to 9.x). **Decline every such prompt.** Gradle 9.x requires JVM 17+ and has unverified compatibility with AGP 8.10.1. The target version is exactly **8.12** — no higher. If `gradle-wrapper.properties` is found to reference any version above 8.12, roll it back to 8.12 before proceeding.

### What to do

#### 5a. Pin the Gradle wrapper to exactly 8.12

Open `gradle/wrapper/gradle-wrapper.properties`. Replace the entire `distributionUrl` line with:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.12-bin.zip
```

Do not change `distributionBase`, `distributionPath`, `zipStoreBase`, `zipStorePath`, or `networkTimeout`. Do not change `-bin` to `-all` unless the project was already using `-all`. If the file currently references any version other than 8.12 (including 9.x), overwrite it with the line above regardless.

#### 5b. Pin JDK 17 in gradle.properties

Open `gradle.properties` in the project root. Check if `org.gradle.java.home` is already set. If it is set to a JDK 11 path, replace it. If absent, add it:

```properties
org.gradle.java.home=C:\\Program Files\\Java\\jdk-17
```

> The exact path depends on the JDK 17 installation location. Verify by running `where java` or checking `C:\Program Files\Java\` for the installed JDK 17 directory. Use the JDK root (the folder containing `bin\java.exe`), not the `bin` subdirectory itself. Use double backslashes.

This is separate from the JDK 11 pin used in the WiFi Automatic project — that project has its own `gradle.properties` and is not affected by this change.

#### 5c. Verify Android Studio Gradle JDK setting

In Android Studio: **File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**. Confirm it is set to a JDK 17 installation (not the embedded JDK, not JDK 11). If it shows JDK 11, change it to JDK 17 now. The `org.gradle.java.home` in `gradle.properties` takes precedence over this setting for command-line builds, but both should agree.

#### 5d. Delete the cached wrapper for any non-8.12 version

If a `gradle-9.x` or `gradle-8.11.1` distribution is cached, delete it to prevent accidental reuse:

> **Manual step:** Delete any non-8.12 Gradle distribution directories under:
> `C:\Users\nukie\.gradle\wrapper\dists\`
> Keep only `gradle-8.12-bin` (or `-all`). Delete `gradle-8.11.1-*`, `gradle-9.*`, and any other versions present.

#### 5e. Verify wrapper JAR is not stale

Check that `gradle/wrapper/gradle-wrapper.jar` is present and non-zero bytes. If it is missing or zero bytes, the wrapper cannot bootstrap itself — report this and stop; the user must restore it from a clean checkout or another project.

#### 5f. Check for deprecated API usage that 8.12 flags as errors

Gradle 8.x progressively promotes previously-deprecated APIs to errors. Check `build.gradle` (root) and `app/build.gradle` for:

- `compile` configuration (must be `implementation` or `api`) — error since Gradle 7
- `android.enableJetifier` in `gradle.properties` — still valid but log if present
- `buildToolsVersion` declared explicitly — warn if below `34.0.0`; it is optional in AGP 8.x
- Any use of `project.afterEvaluate {}` wrapping dependency declarations — flag for review; can cause ordering issues in 8.x
- `jcenter()` in repository declarations — warn; jcenter is read-only and may have stale artifacts; recommend `mavenCentral()` as replacement if present

Do not automatically change any of the above — report each one with its file and line number so it can be reviewed before the build.

#### 5g. Run a wrapper-only dry run

After updating `gradle-wrapper.properties`, run:
```
./gradlew --version
```
This downloads the new distribution and prints the resolved Gradle version without touching any source. The output **must** show `Gradle 8.12`. If it shows any other version (especially 9.x), stop — do not proceed to the build task. Re-check `gradle-wrapper.properties` for any remaining reference to a different version.

#### 5h. Fix Groovy DSL space-assignment syntax (confirmed in problems report)

Gradle 8.12 flags the legacy Groovy space-assignment syntax as deprecated (removal in Gradle 10.0). All instances must be converted to explicit assignment syntax. **These are WARNING severity — do not suppress them.**

In `settings.gradle`, fix line 14 and line 17 — any `url <value>` patterns:
```groovy
// Before (deprecated space-assignment)
url "https://example.com/repo"
// After
url = "https://example.com/repo"
```

In `app/build.gradle`, fix all six of the following — each has confirmed deprecation warnings:
```groovy
// Before → After
namespace "com.smartpack.packagemanager"   →  namespace = "com.smartpack.packagemanager"
compileSdk 34                              →  compileSdk = 34
targetSdk 34                               →  targetSdk = 34
shrinkResources true                       →  shrinkResources = true
aidl true                                  →  aidl = true
abortOnError false                         →  abortOnError = false
```

After applying, run `./gradlew assembleFdroidDebug --warning-mode all` and confirm the space-assignment warnings are gone from the output.

#### 5i. Fix source/target compatibility for JDK 21

The build is running under JDK 21 (the version bundled with Android Studio Meerkat). Java 21 has deprecated compilation targeting `source/target version 8`. In `app/build.gradle`, find the `compileOptions` block and raise both values:

```groovy
compileOptions {
    sourceCompatibility JavaVersion.VERSION_11
    targetCompatibility JavaVersion.VERSION_11
}
```

> **Note:** `VERSION_11` is the correct target for this project. Do not raise to `VERSION_17` unless the project actively uses Java 17 language features — doing so unnecessarily raises the minimum JDK for downstream builds. If a `kotlinOptions { jvmTarget }` block is present, update it to `"11"` as well for consistency.

After this change, the `source value 8 is obsolete` warnings must disappear from the compiler output.

---

## Task 6 — SDK and Java version bumps

### Idempotency check
Open `app/build.gradle`. If all of the following are already true — **skip this entire task**:
- `versionName` is `"7.9"` or higher **and has no `v` prefix**
- `compileSdk = 36`
- `targetSdk = 35`
- `minSdk = 26`
- `sourceCompatibility JavaVersion.VERSION_11` and `targetCompatibility JavaVersion.VERSION_11`

### What to do

#### 6a. Bump versionName and versionCode in app/build.gradle

Open `app/build.gradle`. Find the `defaultConfig` block and read the **exact current values** of `versionCode` and `versionName` — including any `v` prefix — before making any change.

**Fix the versionName prefix first:**

The app currently displays `"Vv7.8"` — meaning `versionName` contains a lowercase `"v"` prefix (e.g. `"v7.8"`) and the UI display code prepends an additional `"V"` on top. One of the two must be removed. The correct convention is:

- `versionName` in `build.gradle`: **no `v` prefix** — store the bare version number only: `"7.8"`, `"7.9"`
- UI display code: prepend `"v"` (lowercase) when showing the version to the user

Search the codebase for wherever `BuildConfig.VERSION_NAME` or `getPackageInfo(...).versionName` is displayed in the UI (likely in an About screen, settings, or toolbar subtitle). Find the line that prepends `"V"` or `"v"` and confirm it exists. Then:

1. If `versionName = "v7.8"` in `build.gradle` → change to `versionName = "7.8"` (remove the `v`)
2. Confirm the UI code already prepends `"v"` → leave the UI code as-is
3. Result: display shows `"v7.8"` — correct

If the UI code prepends `"V"` (uppercase) instead of `"v"` (lowercase), change it to lowercase while you're there.

**Then apply the version bump:**

Apply the following logic based on the cleaned `versionName` (after removing any `v` prefix):

- If `versionName` is `"7.8"` or lower → set `versionName = "7.9"` and increment `versionCode` by 1
- If `versionName` is already `"7.9"` or higher on the 7.x line → leave `versionName` as-is, increment `versionCode` by 1 only if a new build has been installed since the last increment
- If `versionName` is `"8.0"` or higher → leave both unchanged and report

```groovy
defaultConfig {
    versionCode = <current + 1>   // read current value first, add exactly 1
    versionName = "7.9"           // no v prefix — bare number only
}
```

Report the before and after explicitly:
```
versionCode: X → X+1
versionName: "v7.8" → "7.9"  (v prefix removed, version bumped)
```

> **Rule:** `versionCode` must always be strictly greater than the previously installed version for the APK to be installable as an upgrade. Never decrement it. Never set it to an arbitrary number — always read the current value first and add 1.

#### 6b. Update SDK versions in app/build.gradle

In `app/build.gradle`, apply the following changes using the assignment syntax already fixed in Task 5h:

```groovy
compileSdk = 36

defaultConfig {
    minSdk = 26        // was 24 — drops Android 7.x (<1% market share)
    targetSdk = 35     // was 34 — required by Google Play since Aug 2025
    // versionCode and versionName: already handled in 6a — do not change here
}
```

Do not change `minSdk` below 26 — the app already uses `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` which is an API 26+ pattern.

Do not raise `targetSdk` to 36 — Android 16 behavior changes need separate verification. 35 is the correct target for this modernisation pass.

#### 6b. Update Java source and target compatibility

In the `compileOptions` block of `app/build.gradle`:

```groovy
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
```

If a `kotlinOptions` block is present, update it too for consistency:
```groovy
kotlinOptions {
    jvmTarget = "11"
}
```

This eliminates the three `source value 8 is obsolete` warnings from the JDK 21 build.

#### 6c. Sync and check for new compileSdk 36 requirements

After updating `compileSdk`, run a Gradle sync. Some AndroidX dependencies may now emit warnings or errors requiring version bumps in `libs.versions.toml`. For each one reported:
- Read the exact message — it will name the dependency and the minimum version required.
- Update only that dependency's version in `libs.versions.toml`. Do not update any dependency that did not produce a warning.
- Re-sync after each change. Do not batch-update all dependencies at once.

#### 6d. Verify minSdk 26 compatibility across the codebase

Search all Java files for API calls that require API level higher than 26 but are called without a `Build.VERSION.SDK_INT` guard. The most common patterns in this codebase:

- `PackageInfo.getLongVersionCode()` — requires API 28 — must be guarded:
  ```java
  long vCode = (Build.VERSION.SDK_INT >= 28)
      ? pi.getLongVersionCode() : pi.versionCode;
  ```
- `NotificationChannel` — requires API 26 — already at minSdk, no guard needed.
- Any `getSystemService` call returning a type only available at API 27+ — check the Javadoc.

Report any unguarded API calls found. Do not auto-fix them without listing them first.

#### 6e. Test edge-to-edge enforcement (targetSdk 35 behavior change)

With `targetSdk = 35`, Android enforces edge-to-edge display — the app window extends behind the system status bar and navigation bar. Content that was previously padded by the system may now be hidden behind these bars.

After installing the updated debug APK on the Xiaomi Redmi Note 9 Pro (Android 12 / MIUI 14):

1. Open every major screen: app list, app details, settings, permissions editor, APK export dialog.
2. Check whether any content (buttons, list items, bottom sheets, FABs) is clipped or hidden behind the navigation bar or status bar.
3. For any screen where content is obscured, apply `WindowInsetsCompat` padding:
   ```java
   ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
       Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
       v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
       return insets;
   });
   ```
   Apply to the outermost layout of the affected Fragment or Activity, not to individual child views.

4. Do not add `android:fitsSystemWindows="true"` as a blanket fix — it interferes with custom toolbars and coordinators. Use `WindowInsetsCompat` explicitly.

#### 6f. Check and migrate onBackPressed (targetSdk 35 behavior change)

Search all Activities and Fragments for `onBackPressed()` overrides. With targetSdk 35, the predictive back gesture requires migration to the callback API:

```java
// Replace onBackPressed() override with:
getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
    @Override
    public void handleOnBackPressed() {
        // your existing back logic here
    }
});
```

Register this in `onCreate()` (for Activities) or `onViewCreated()` (for Fragments). If the existing `onBackPressed()` simply calls `super.onBackPressed()` with no other logic, remove the override entirely — the default behavior is correct without it.

Report every file where `onBackPressed()` was found and how it was handled.

#### 6g. Update default export name template

In `AppSettings.java`, confirm that `getExportTemplate()` returns the correct default:

```java
public static String getExportTemplate(Context context) {
    return sCommonUtils.getString(PREF_EXPORT_TPL, "{appname}-{packageid}-{versionname}", context);
}
```

The default template must be `{appname}-{packageid}-{versionname}` — not the old `{appname}-{versionname}`. If it differs, update it. Also confirm `ExportNameBuilder.java` has the same default wherever it declares a fallback template string — both must match.

#### 6h. Runtime gate

Install the updated debug APK. Confirm:
1. App launches without crashing.
2. No content is obscured behind system bars on any screen.
3. Back navigation works correctly on all screens.
4. APK export produces filenames in the format `AppName-com.package.id-1.0.0.apk` using the default template.
5. No new logcat errors introduced by the SDK bump.

---

## Task 7 — Batch app selection, uninstall, and APK export

### Idempotency check
- Open `PackageTasksFragment.java`. If it already contains a `selectedPackages` (or equivalent) `HashSet` field, a long-press listener that enters multi-select mode, and a bottom action bar or contextual toolbar that shows batch operation buttons — **skip this entire task**.

### Overview
The app needs a proper multi-select mode in its app list. Long-pressing any app enters selection mode; tapping further apps toggles them in/out of the selection. A persistent bottom action bar appears showing the selection count and available batch operations: **Export APKs** and **Uninstall**. Tapping outside any item or pressing back exits selection mode and clears the selection.

---

### Step 7-1 — Selection state model

Add a selection state holder to the class that manages the app list (likely `PackageTasksFragment` or its adapter). If a `ViewModel` exists for the fragment, add state there:

```java
// In PackageTasksFragment (or ViewModel if one exists)
private final HashSet<String> mSelectedPackages = new HashSet<>();
private boolean mSelectionMode = false;
```

Selection state must be preserved across configuration changes (rotation). If the fragment does not already use a `ViewModel`, add one now:
```java
// AppListViewModel.java — create in same package as PackageTasksFragment
public class AppListViewModel extends androidx.lifecycle.ViewModel {
    public final HashSet<String> selectedPackages = new HashSet<>();
    public boolean selectionMode = false;
}
```

---

### Step 7-2 — RecyclerView adapter changes

In the adapter for the app list (likely `PackageTasksAdapter`):

1. Add a reference to the selection set: `private HashSet<String> mSelected;`
2. In `onBindViewHolder`, check if the item's package name is in `mSelected` and apply a visual selection indicator — a tinted background or a checkmark overlay using `MaterialCardView` elevation/stroke or a `CheckBox` overlay:
   ```java
   boolean isSelected = mSelected.contains(item.getPackageName());
   holder.itemView.setActivated(isSelected);
   // activated state should be reflected via selector drawable in the item layout
   ```
3. Add item layout state: in the RecyclerView item XML, give the root view a `android:background="@drawable/selector_item_selected"` — create this drawable in `res/drawable/`:
   ```xml
   <!-- res/drawable/selector_item_selected.xml -->
   <selector xmlns:android="http://schemas.android.com/apk/res/android">
       <item android:state_activated="true"
             android:drawable="@color/colorSelectedItemBackground" />
       <item android:drawable="@android:color/transparent" />
   </selector>
   ```
   Add `colorSelectedItemBackground` to `res/values/colors.xml` as a low-opacity tint of the primary color (e.g. `#1A6750A4` for ~10% opacity purple).

---

### Step 7-3 — Long-press to enter selection mode

In `PackageTasksFragment.onViewCreated` (or wherever click listeners are set on the RecyclerView), add a long-press listener:

```java
adapter.setOnItemLongClickListener((packageName) -> {
    if (!mSelectionMode) {
        mSelectionMode = true;
        showBatchActionBar();
    }
    toggleSelection(packageName);
    return true; // consume the event
});
```

Implement `toggleSelection`:
```java
private void toggleSelection(String packageName) {
    if (mSelectedPackages.contains(packageName)) {
        mSelectedPackages.remove(packageName);
    } else {
        mSelectedPackages.add(packageName);
    }
    if (mSelectedPackages.isEmpty()) {
        exitSelectionMode();
    } else {
        updateBatchActionBar();
    }
    mAdapter.notifyDataSetChanged();
}
```

In normal tap while in selection mode, call `toggleSelection` instead of opening app details.

---

### Step 7-4 — Bottom action bar

Add a `LinearLayout` or `MaterialCardView` at the bottom of `fragment_package_tasks.xml` (or the equivalent layout), initially `GONE`:

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/batchActionBar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom"
    android:visibility="gone"
    app:cardElevation="8dp"
    app:cardCornerRadius="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="12dp"
        android:gravity="center_vertical">

        <TextView
            android:id="@+id/tvSelectionCount"
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_height="wrap_content"
            android:textAppearance="?attr/textAppearanceLabelLarge" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnBatchExport"
            style="@style/Widget.Material3.Button.TonalButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/export"
            android:layout_marginEnd="8dp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnBatchUninstall"
            style="@style/Widget.Material3.Button.TonalButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/uninstall" />

    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

Add the string resources if not already present:
```xml
<string name="batch_selected">%d selected</string>
<string name="uninstall">Uninstall</string>
```

Check `strings.xml` for existing equivalents before adding — use existing strings where they exist.

Implement in the fragment:
```java
private void showBatchActionBar() {
    mBatchActionBar.setVisibility(View.VISIBLE);
    updateBatchActionBar();
}

private void updateBatchActionBar() {
    mTvSelectionCount.setText(
        getString(R.string.batch_selected, mSelectedPackages.size()));
}

private void exitSelectionMode() {
    mSelectionMode = false;
    mSelectedPackages.clear();
    mBatchActionBar.setVisibility(View.GONE);
    mAdapter.notifyDataSetChanged();
}
```

Handle back press to exit selection mode (using the `OnBackPressedCallback` pattern from Task 6f):
```java
getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
    new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (mSelectionMode) {
                exitSelectionMode();
            } else {
                setEnabled(false);
                requireActivity().onBackPressed();
            }
        }
    });
```

---

### Step 7-5 — Batch APK export

Wire `btnBatchExport` click:

```java
mBtnBatchExport.setOnClickListener(v -> {
    if (mSelectedPackages.isEmpty()) return;
    new Thread(() -> {
        int success = 0, failed = 0;
        for (String pkg : mSelectedPackages) {
            try {
                PackageInfo pi = requireContext().getPackageManager()
                    .getPackageInfo(pkg, 0);
                String label = requireContext().getPackageManager()
                    .getApplicationLabel(pi.applicationInfo).toString();
                String filename = ExportNameBuilder.build(
                    requireContext(), pi, label) + ".apk";
                File src = new File(pi.applicationInfo.sourceDir);
                File dst = new File(AppSettings.getExportPath(requireContext()), filename);
                // copy src → dst
                copyFile(src, dst);
                success++;
            } catch (Exception e) {
                failed++;
            }
        }
        final int s = success, f = failed;
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(),
                s + " exported" + (f > 0 ? ", " + f + " failed" : ""),
                Toast.LENGTH_LONG).show();
            exitSelectionMode();
        });
    }).start();
});
```

Find or confirm the method `AppSettings.getExportPath(Context)` exists and returns the app's configured export directory. If it does not exist, search the codebase for wherever the export destination path is constructed and use that location.

Implement `copyFile` if not already present in a utility class:
```java
private static void copyFile(File src, File dst) throws IOException {
    dst.getParentFile().mkdirs();
    try (java.io.InputStream in = new java.io.FileInputStream(src);
         java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
    }
}
```

For split APKs (apps with multiple APK files), check `pi.applicationInfo.splitSourceDirs`. If non-null and non-empty, export all splits into a subfolder named after the package, or bundle them — match whatever the existing single-app export does for splits.

---

### Step 7-6 — Batch uninstall

Wire `btnBatchUninstall` click. Uninstall strategy depends on available privilege — check in this order:

**Shizuku available:**
```java
// Run for each selected package
ShizukuUtils.exec("pm uninstall " + packageName);
```
Use the existing Shizuku utility class already in the project.

**Root available:**
```java
RootUtils.exec("pm uninstall " + packageName);
```
Use the existing root utility class.

**No privilege (normal user):**
```java
// Must be done one at a time — Android has no batch uninstall API without privilege
Intent intent = new Intent(Intent.ACTION_DELETE,
    Uri.parse("package:" + packageName));
startActivity(intent);
```
Note: without root/Shizuku, uninstall requires user confirmation for each package individually via the system UI. Show a dialog warning the user of this before proceeding.

After each uninstall completes, remove the package from `mSelectedPackages` and call `mAdapter.notifyDataSetChanged()` to reflect the removal from the list.

**System apps guard:** Before uninstalling, check `ApplicationInfo.FLAG_SYSTEM`. If any selected app is a system app and privilege mode is NOT root/Shizuku, remove it from the uninstall batch and show a warning: `"X system app(s) skipped — root or Shizuku required to uninstall system apps."` Do not silently skip.

---

### Step 7-7 — Runtime gate

Install the debug APK and verify on Xiaomi Redmi Note 9 Pro (Android 12 / MIUI 14):
1. Long-press an app — selection mode activates, bottom action bar appears.
2. Tap additional apps — selection count updates correctly.
3. Tap a selected app — it deselects correctly.
4. Press back — selection mode exits cleanly, action bar disappears.
5. Batch export: select 2–3 user apps, tap Export — APKs appear in the export folder named per the `{appname}-{packageid}-{versionname}` template.
6. Batch uninstall: select a user app, tap Uninstall — the system uninstall dialog appears (or silent uninstall proceeds if Shizuku/root is active).
7. After uninstall, the app disappears from the list without a crash.

---

## Task 8 — APK Finder: locate, move, and rename external APKs

### Idempotency check
- Search the codebase for an Activity, Fragment, or menu item whose name or string key contains `finder`, `import`, `locate`, or `external_apk`. If a complete APK file-picker flow already exists that copies files to the export folder and offers rename — **skip this entire task**.

### Overview
A new **APK Finder** screen lets the user pick one or more APK files from anywhere on device storage (via the system file picker), preview their metadata (app name, package ID, version), optionally rename them using the export naming convention, and copy or move them into the app's configured export folder.

---

### Step 8-1 — Add APK Finder entry point

Add a menu item or navigation entry to reach the APK Finder. Find where the app's main menu or navigation drawer items are defined (likely in a menu XML under `res/menu/` or in the main Activity's `onCreateOptionsMenu`). Add:

```xml
<item
    android:id="@+id/action_apk_finder"
    android:title="@string/apk_finder"
    android:icon="@drawable/ic_search"
    app:showAsAction="ifRoom" />
```

Add the string:
```xml
<string name="apk_finder">APK Finder</string>
<string name="apk_finder_desc">Find and import APKs from storage</string>
<string name="import_to_export_folder">Move to export folder</string>
<string name="rename_on_import">Rename using naming convention</string>
<string name="apk_finder_empty">No APK files selected</string>
<string name="apk_finder_success">%1$d file(s) moved to export folder</string>
```

Check all string keys against `strings.xml` for existing equivalents before adding.

Wire the menu item to launch `ApkFinderFragment` (created in Step 8-2):
```java
case R.id.action_apk_finder:
    // navigate to ApkFinderFragment via FragmentManager or NavController
    // use whichever navigation pattern the rest of the app uses
    break;
```

---

### Step 8-2 — Create ApkFinderFragment

Create `ApkFinderFragment.java` in the fragments package. It hosts:
- A **Pick Files** button that launches the system file picker
- A `RecyclerView` listing the picked APK files with metadata
- A **Rename** toggle switch (default: on)
- A **Move** button that executes the import

```java
public class ApkFinderFragment extends Fragment {

    private final ArrayList<Uri> mPickedUris = new ArrayList<>();
    private final ArrayList<ApkEntry> mEntries = new ArrayList<>();
    private ApkFinderAdapter mAdapter;
    private Switch mRenameSwitch;

    // File picker launcher — uses SAF, works on all API levels ≥ 26
    private final ActivityResultLauncher<String[]> mPickerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uris -> {
                if (uris == null || uris.isEmpty()) return;
                mPickedUris.clear();
                mPickedUris.addAll(uris);
                loadApkMetadata();
            });

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle saved) {
        // inflate fragment_apk_finder layout (create in Step 8-3)
        View v = inflater.inflate(R.layout.fragment_apk_finder, container, false);
        mRenameSwitch = v.findViewById(R.id.switchRename);
        mRenameSwitch.setChecked(true); // rename on by default
        v.findViewById(R.id.btnPickApks).setOnClickListener(x ->
            mPickerLauncher.launch(
                new String[]{"application/vnd.android.package-archive"}));
        v.findViewById(R.id.btnMoveToExport).setOnClickListener(x ->
            executeImport());
        mAdapter = new ApkFinderAdapter(mEntries);
        RecyclerView rv = v.findViewById(R.id.recyclerApkFinder);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(mAdapter);
        return v;
    }

    private void loadApkMetadata() {
        mEntries.clear();
        PackageManager pm = requireContext().getPackageManager();
        for (Uri uri : mPickedUris) {
            // Resolve content URI to real path for getPackageArchiveInfo
            String path = resolveUriToPath(uri);
            if (path == null) continue;
            PackageInfo pi = pm.getPackageArchiveInfo(path,
                PackageManager.GET_META_DATA);
            if (pi == null) continue;
            pi.applicationInfo.sourceDir = path;
            pi.applicationInfo.publicSourceDir = path;
            String label = pm.getApplicationLabel(pi.applicationInfo).toString();
            String suggestedName = ExportNameBuilder.build(
                requireContext(), pi, label);
            mEntries.add(new ApkEntry(uri, path, pi, label, suggestedName));
        }
        mAdapter.notifyDataSetChanged();
    }
```

---

### Step 8-3 — Create ApkEntry model

Create `ApkEntry.java` in the model/utils package:
```java
public class ApkEntry {
    public final Uri uri;
    public final String sourcePath;
    public final PackageInfo packageInfo;
    public final String appLabel;
    public String suggestedName; // mutable — user can edit in the list

    public ApkEntry(Uri uri, String sourcePath, PackageInfo pi,
                    String label, String suggestedName) {
        this.uri = uri;
        this.sourcePath = sourcePath;
        this.packageInfo = pi;
        this.appLabel = label;
        this.suggestedName = suggestedName;
    }
}
```

---

### Step 8-4 — Create ApkFinderAdapter

Create `ApkFinderAdapter.java`. Each item shows: app icon, app label, package ID, version name, and an editable `EditText` pre-filled with `suggestedName`. When the user edits the `EditText`, update `entry.suggestedName` so the move operation uses the edited name:

```java
holder.etName.setText(entry.suggestedName);
holder.etName.addTextChangedListener(new TextWatcher() {
    // ... boilerplate ...
    public void afterTextChanged(Editable s) {
        entry.suggestedName = s.toString().trim();
    }
});
```

Show the app icon by loading it from `PackageManager`:
```java
try {
    Drawable icon = requireContext().getPackageManager()
        .getApplicationIcon(entry.packageInfo.applicationInfo);
    holder.ivIcon.setImageDrawable(icon);
} catch (Exception e) {
    holder.ivIcon.setImageResource(R.drawable.ic_android); // fallback
}
```

---

### Step 8-5 — Create fragment_apk_finder.xml layout

Create `res/layout/fragment_apk_finder.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnPickApks"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/apk_finder_desc" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginTop="12dp">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/rename_on_import"
            android:textAppearance="?attr/textAppearanceBodyMedium" />

        <androidx.appcompat.widget.SwitchCompat
            android:id="@+id/switchRename"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

    </LinearLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerApkFinder"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginTop="12dp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnMoveToExport"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="@string/import_to_export_folder" />

</LinearLayout>
```

---

### Step 8-6 — URI to path resolution

On Android 10+ (API 29+), `ContentResolver` URI paths cannot be used directly with `File`. Use `resolveUriToPath` to copy the content to a temporary file for `getPackageArchiveInfo`:

```java
private String resolveUriToPath(Uri uri) {
    try {
        // Copy to a temp file in cache dir
        File tmp = new File(requireContext().getCacheDir(),
            "apkfinder_" + System.currentTimeMillis() + ".apk");
        try (InputStream in = requireContext()
                .getContentResolver().openInputStream(uri);
             OutputStream out = new java.io.FileOutputStream(tmp)) {
            if (in == null) return null;
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        }
        return tmp.getAbsolutePath();
    } catch (Exception e) {
        return null;
    }
}
```

Clean up temp files after the import operation completes.

---

### Step 8-7 — Execute import (move to export folder)

Implement `executeImport()`:

```java
private void executeImport() {
    if (mEntries.isEmpty()) {
        Toast.makeText(requireContext(),
            R.string.apk_finder_empty, Toast.LENGTH_SHORT).show();
        return;
    }
    boolean rename = mRenameSwitch.isChecked();
    File exportDir = new File(AppSettings.getExportPath(requireContext()));
    exportDir.mkdirs();

    new Thread(() -> {
        int success = 0, failed = 0;
        for (ApkEntry entry : mEntries) {
            try {
                String filename = rename
                    ? entry.suggestedName + ".apk"
                    : new File(entry.sourcePath).getName();
                File dst = new File(exportDir, filename);
                copyFile(new File(entry.sourcePath), dst);
                // Delete source temp file
                new File(entry.sourcePath).delete();
                success++;
            } catch (Exception e) {
                failed++;
            }
        }
        final int s = success, f = failed;
        requireActivity().runOnUiThread(() -> {
            Toast.makeText(requireContext(),
                getString(R.string.apk_finder_success, s)
                    + (f > 0 ? ", " + f + " failed" : ""),
                Toast.LENGTH_LONG).show();
            mEntries.clear();
            mPickedUris.clear();
            mAdapter.notifyDataSetChanged();
        });
    }).start();
}
```

Reuse the `copyFile` utility from Task 7-5 — do not duplicate it. If it was added to a utility class there, call it from the same class here.

---

### Step 8-8 — AndroidManifest permissions

Check `AndroidManifest.xml` for these permissions. Add only those not already present:

```xml
<!-- Required for SAF file access — no additional permission needed for ACTION_OPEN_DOCUMENT -->
<!-- Required for writing to export folder on API < 29 -->
<uses-permission
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

> **Note for MIUI 14 (Xiaomi Redmi Note 9 Pro):** MIUI may show additional permission dialogs for file access even with SAF. `ACTION_OPEN_DOCUMENT` / `OpenMultipleDocuments` is the most reliable picker method on MIUI and does not require `MANAGE_EXTERNAL_STORAGE`. Do not add `MANAGE_EXTERNAL_STORAGE` — it requires a special system settings grant that users are unlikely to approve.

---

### Step 8-9 — Runtime gate

Install and verify on Xiaomi Redmi Note 9 Pro (Android 12 / MIUI 14):
1. APK Finder opens from the menu without crashing.
2. Tapping Pick Files opens the MIUI system file picker filtered to APKs.
3. Selecting multiple APKs populates the list with icon, label, package ID, version, and pre-filled name.
4. The suggested name matches the `{appname}-{packageid}-{versionname}` template.
5. Editing a suggested name in the list updates the name used on move.
6. With Rename on: moved files appear in the export folder with the convention name.
7. With Rename off: moved files keep their original filename.
8. No crash or permission denial on MIUI during file access.
9. Temp files in cache are cleaned up after the move.

---

## Task 9 — Pre-build validation (run after all tasks, before debug build)

Run all of the following in order. Do not attempt a build if any item fails — report the failure and wait for input.

1. **XML well-formedness:** Parse every file under `app/src/main/res/` as XML. Report any file that fails to parse with its exact error and line number.

2. **Manifest check:** Confirm `AndroidManifest.xml` has no duplicate `<activity>`, `<service>`, or `<receiver>` entries. Confirm `android:theme` on `<application>` references the theme modified in Task 1 and that it resolves to a real resource.

3. **String references:** For every `@string/xxx` reference in layout XMLs and `AndroidManifest.xml`, confirm `xxx` exists in `res/values/strings.xml`. List any missing keys.

4. **Drawable references:** For every `@drawable/xxx` and `@mipmap/xxx` reference in layouts and manifest, confirm the resource exists. List missing.

5. **Color references:** For every `@color/xxx` in themes, styles, and layouts, confirm it exists in `res/values/colors.xml`. List missing.

6. **Java symbol resolution:** For each new or modified `.java` file, confirm:
   - All imports resolve to classes present in the dependency tree (check `libs.versions.toml` for declared deps).
   - No new dependency was silently assumed without being declared in `app/build.gradle`.
   - `AppSettings.PREF_*` constants used in `ThemeHelper` and `ExportNameBuilder` exist in `AppSettings.java`.
   - `PackageInfo.getLongVersionCode()` is available (requires `minSdk >= 28` or a `Build.VERSION.SDK_INT` guard for lower — check `minSdkVersion` in `app/build.gradle`).

7. **getLongVersionCode compatibility check:** If `minSdkVersion < 28`, replace `pi.getLongVersionCode()` in `ExportNameBuilder` with:
   ```java
   long vCode = (android.os.Build.VERSION.SDK_INT >= 28)
       ? pi.getLongVersionCode()
       : pi.versionCode;
   ```

8. **Duplicate resource check:** Confirm no resource name is defined twice in the same `values/` file (a common corruption after partial edits).

9. **libs.versions.toml integrity:** Re-validate as TOML. Confirm no new library was used in Java but not declared in the catalog.

10. **Gradle sync check:** Run a Gradle sync (not a full build). If sync fails, report the exact error — do not proceed to build.

11. **Deprecated API audit in PackageTasksFragment:** The compiler emits a `Note: Some input files use or override a deprecated API` advisory attributed specifically to `PackageTasksFragment.java`. This is not a build error, but it must be identified and resolved. Run the following to expose the exact call sites:
    ```
    ./gradlew :app:compileFdroidDebugJavaWithJavac -Xlint:deprecation 2>&1
    ```
    Read the full output. For each deprecated call site reported:
    - Identify the replacement API recommended in the deprecation notice or Javadoc.
    - Update `PackageTasksFragment.java` to use the non-deprecated API.
    - Common candidates in Android Fragment code: `getActivity()` (prefer `requireActivity()`), `onActivityCreated()` (prefer `onViewCreated()`), `AsyncTask` (prefer `java.util.concurrent` or a `Handler`), and pre-Tiramisu `PackageManager` methods without flags.
    - Do not suppress the warning with `@SuppressWarnings("deprecation")` — fix the root cause.
    - Re-run `compileFdroidDebugJavaWithJavac` and confirm the `Note:` lines are gone from the output.

Only if all 11 checks pass: attempt `./gradlew assembleFdroidDebug` and report the full output.

---

## Notes for Antigravity

- **Never delete existing working code to make space for new code.** Append or inject only.
- **Preserve all existing preference keys.** Existing user settings must not be broken by new keys.
- **Use the exact package name from `AndroidManifest.xml`** in every new `.java` file — do not guess or infer.
- **Do not upgrade any version in `libs.versions.toml`** unless a specific version is missing a required API (and then note the change explicitly). The Gradle wrapper version in `gradle-wrapper.properties` is handled separately by Task 5 and is not subject to this restriction.
- **File encoding:** All `.java` and `.xml` files must be saved as UTF-8 without BOM.
- **Line endings:** Use the line ending style already present in the file being modified (do not normalize to LF if the file uses CRLF).
- If any task produces an ambiguous result (e.g. multiple candidate classes for a given role), list the candidates and ask before proceeding.
