# Category navigation regression suite

## Existing setup and scope

The app uses Kotlin/XML, activity-scoped LiveData and ViewModels, RecyclerView
ListAdapter, and ViewPager2. It has no dependency injection framework, database,
network service, or meaningful existing regression tests; the original test files
are generated templates. The suite retains that architecture and uses JUnit 4,
Robolectric 4.14.1 (API 28), AndroidX Test and Espresso. Android resources are
enabled for local tests. No emulator is needed.

The regression tests drove the production fix. Both summary screens now resolve
the bound row's stable category ID against the current ViewModel order before
jumping to a page. Missing/deleted targets are ignored. Jumps are immediate so a
pager animation does not compete with pending list changes.

Planning drag callbacks describe relative placement by category IDs. The ViewModel
is the only publisher of reordered lists; the adapter no longer submits a second
reorder computed from a stale snapshot. Repeated callbacks preserve the intended
placement. Edit/delete actions also resolve IDs against the current model, including
when an edit dialog outlives a reorder.

## Run

From the project root in PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.mmgpt.shoplistgpt.CategoryNavigationTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.mmgpt.shoplistgpt.CategoryReorderTest" --console=plain
```

The HTML report is at app/build/reports/tests/testDebugUnitTest/index.html.
Failures deliberately assert desired behavior; do not disable them or change their
expectations to match the current bug.

## Contracts

CategoryReorderTest covers whole-category moves in both directions, equal headings
with distinct IDs, composed/reversed moves, identity moves, invalid source and
destination positions (including NO_POSITION, size, and integer extremes), empty
and singleton lists, and persistence. Equality assertions include every item's
identity, flags and purchase state, so preserving only category counts is insufficient.

CategoryNavigationTest exercises the actual fragments, adapters, gesture detector
and pager. It covers first/middle/last destinations in both modes, duplicate
headings with distinct item contents, add-row and outside-row taps, placeholder
drag rejection, empty and singleton states, committed and pending reorders,
rapid drag callbacks, a deleted target, and scrolling in Shop mode.
Comments beside the tests explain why each matters.

The navigation oracle is the tapped category's stable ID in the authoritative
model. Page heading AND item contents are verified to distinguish categories with
identical titles. Plan's summary and add row offsets are checked separately from
Shop's trailing summary.

## Original TDD red baseline

Running the full command above produced 28 tests: 25 passed and 3 failed.
That includes the unchanged generated arithmetic test; the new suite contributes
27 behavioral tests (24 passing and the following 3 failing regressions):

- doubleTapWhileReorderDiffIsPendingUsesDisplayedCategoryIdentity:
  tapping the visible Produce row requests page 1 instead of its current page 2.
- rapidDragCallbacksCannotLeaveDisplayAndModelInDifferentOrders:
  displayed IDs are [7, 41, 90] while model IDs are [41, 7, 90].
- doubleTapOnDeletedCategoryDoesNotNavigateToAnotherCategory:
  a deleted row opens page 1 instead of leaving the summary at page 0.

These failures were reproduced before the production fix. Their expectations have
been retained, and the rapid-drag assertion now also verifies the intended order,
so making the display and model agree on an accidentally reversed move cannot pass.

Additional tests cover all source/target/side combinations for relative moves,
invalid and self-target IDs, repeated upward and downward callbacks, multi-target
dragging, pending Shop reorders, edits/deletions during pending updates, and the
Plan-to-Shop journey.

Validation after the fix: the full local suite and debug APK build pass using:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain
```

This is local Robolectric validation, not an emulator/device end-to-end result.

## Determinism and maintenance

CategoryUiFixture replaces the default AsyncDifferConfig.Builder diff executor
with a FIFO test queue before creating adapters. It flushes queued calculations
and the paused main looper explicitly. This reproduces pending-diff interleavings
without sleeps, racing background threads, or mocks of navigation outcomes.

The drag helper calls the real ItemTouchHelper callback. It tests drag callback
interleavings, rather than claiming to emulate a physical drag gesture end to end.
Double taps dispatch MotionEvents through the real RecyclerView with both local
and raw screen coordinates preserved.

Two private AndroidX details (Builder.sDiffExecutor and ItemTouchHelper.mCallback)
are isolated in the fixture. Review those accesses if upgrading RecyclerView.
The executor is restored and each test starts from empty Robolectric preferences;
no developer/device shopping data is touched. Do not run fixture operations
concurrently within one sandbox.

The pending-state tests retain queued list updates to verify that navigation stays
correct independently of when a diff commits. Any future change to diff scheduling
must preserve the identity, intended placement, and convergence contracts.
