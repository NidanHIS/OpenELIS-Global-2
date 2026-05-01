# ABI Frontend Dashboard Branch Documentation

## Purpose

This document explains the frontend work currently kept on the
`feat/abi-frontend` branch after cleanup against `nidan-dev`.

The branch is intentionally scoped to the dashboard UI. Before this
documentation file was added, the functional diff against `nidan-dev` was only:

- `frontend/src/components/home/Dashboard.tsx`
- `frontend/src/components/home/Dashboard.css`
- `frontend/src/languages/en.json`
- `docs/frontend-dashboard-abi-branch.md` documents the branch and does not
  affect runtime behavior.

All other frontend, backend, Liquibase, Docker, nginx, and documentation changes
were restored to match `nidan-dev` to reduce merge conflicts and keep the review
focused.

## High-Level Summary

The dashboard was redesigned into a more operational frontend experience for
laboratory users. The page now presents dashboard metrics, active order queues,
incoming sample collection work, backlog views, search, pagination, patient
name enrichment, and action links from one dashboard surface.

No new backend endpoint is introduced by this branch. The frontend consumes
existing OpenELIS REST endpoints and formats the returned data for the Carbon
Design System dashboard UI.

## Files Changed

### `Dashboard.tsx`

Main React component for the dashboard. This file owns:

- Dashboard metric loading.
- Tile selection and drill-down behavior.
- Active order and backlog table views.
- Incoming order collection table.
- Search and filtering logic.
- Department tab filtering.
- Pagination for both frontend lists and paged backend responses.
- Patient name enrichment when grouped order rows do not already include a name.
- Navigation links for result entry, validation, reports, barcode, and sample
  collection.

### `Dashboard.css`

Styles for the redesigned dashboard experience. This file owns:

- Responsive tile grid layout.
- Dashboard shell and split-panel layout.
- Summary cards.
- Active/backlog toggle controls.
- Table wrappers with sticky headers and sticky pagination.
- Search areas.
- Mobile breakpoints.
- Visual treatment for priority, actions, empty states, and panel spacing.

### `en.json`

English i18n keys used by the redesigned dashboard. Only `en.json` was changed,
which follows the project translation workflow. Non-English locale files remain
unchanged and are handled through Transifex.

## Frontend Concepts Used

### React Functional Component

`HomeDashBoard` is a React functional component using hooks instead of class
component lifecycle methods.

Main hook types used:

- `useState`: Stores UI state such as counts, selected tile, table data, search
  text, pagination state, loading state, selected department, and active/backlog
  mode.
- `useEffect`: Runs side effects such as API loading on mount, loading data when
  a tile changes, loading departments, and scheduling refresh at midnight.
- `useMemo`: Computes filtered lists and summary values without recalculating on
  every render.
- `useCallback`: Keeps helper functions stable when they are dependencies of
  effects or memoized calculations.
- `useRef`: Stores mutable values that should not cause re-rendering, such as
  mounted state, request sequence tracking, and patient name cache.
- `useContext`: Reads shared application context for user/session details and
  notification helpers.

### Carbon Design System

The UI uses Carbon components from `@carbon/react`:

- `Tile` and `ClickableTile` for dashboard metrics.
- `Grid` and `Column` for layout.
- `DataTable`, `Table`, `TableHead`, `TableBody`, and related components for
  queue tables.
- `Pagination` for table pagination.
- `Tabs`, `TabList`, and `Tab` for department filtering.
- `TextInput` for search fields.
- `Button` and icon-only actions for navigation and paging.
- `Loading` for dashboard loading state.

The CSS customizes layout and visual polish around Carbon components while still
using Carbon as the UI foundation.

### Internationalization

All new user-facing dashboard text is externalized through React Intl.

Pattern used:

```tsx
const message = useCallback(
  (id: string) => intl.formatMessage({ id }),
  [intl],
);
```

This keeps JSX cleaner and avoids hardcoded English strings in the component.
New keys were added only to `frontend/src/languages/en.json`.

## API Implementation In The Frontend

The dashboard uses existing OpenELIS API helper functions from
`frontend/src/components/utils/Utils.js`:

- `getFromOpenElisServer`: Callback-based GET helper.
- `getFromOpenElisServerV2`: Promise-based GET helper.
- `convertAlphaNumLabNumForDisplay`: Formats lab numbers for display/search.
- `hasRole`: Checks whether the current user has a role.

### Metrics API

Endpoint:

```text
/rest/home-dashboard/metrics
```

Purpose:

- Loads top-level metric counts.
- Populates dashboard tiles such as in-progress orders, validation-ready orders,
  completed orders, rejected orders, and samples to collect.

Frontend behavior:

- Called on initial mount.
- Called again at midnight to refresh date-based metrics.
- The response is stored in the `counts` state object.

### Tile Detail APIs

The selected tile determines the endpoint.

Mapping:

```text
AVERAGE_TURN_AROUND_TIME -> /rest/home-dashboard/turn-around-time-metrics
ORDERS_FOR_USER          -> /rest/home-dashboard/ORDERS_FOR_USER?systemUserId={id}
ORDERS_IN_PROGRESS       -> /rest/home-dashboard/ORDERS-Grouped
ON_GOING_ORDERS          -> /rest/home-dashboard/ORDERS-Grouped
ORDERS_READY_FOR_VALIDATION -> /rest/home-dashboard/VALIDATION-Grouped
Other metric types       -> /rest/home-dashboard/{tile.type}
```

Purpose:

- Loads detailed rows for the selected metric.
- Supports grouped in-progress order display.
- Supports validation queues.
- Supports user-entered order views.

Frontend behavior:

- When `selectedTile` changes, the component resets pagination and search state,
  sets loading, and loads data for the selected tile.
- Paged endpoints use `page={targetPage}` appended with either `?` or `&`
  depending on whether the endpoint already contains query parameters.

### Incoming Orders API

Endpoint:

```text
/rest/incoming-orders
```

Purpose:

- Loads external/incoming orders awaiting collection.

Frontend behavior:

- Maps API fields into DataTable row fields.
- Uses `externalOrderNumber` as the row id.
- Formats `receivedTimestamp`.
- Converts missing values to `-`.
- Updates `counts.samplesToCollect` based on the incoming order list length.

Mapped frontend row shape:

```ts
{
  ...item,
  id: item.externalOrderNumber,
  received: formatIncomingOrderTimestamp(item.receivedTimestamp),
  tests: item.testCount != null ? String(item.testCount) : "-",
  source: item.source ?? "-",
}
```

### Test Section API

Endpoint:

```text
/rest/user-test-sections/ALL
```

Purpose:

- Loads available laboratory departments/test sections.

Frontend behavior:

- Global Administrators default to `all`.
- Other users default to the first returned department.
- Department tabs filter completed/user order views when applicable.

### Patient Search API

Endpoint patterns:

```text
/rest/patient-search-results?labNumber={labNumber}&suppressExternalSearch=true
/rest/patient-search-results?nationalID={patientId}&suppressExternalSearch=true
```

Purpose:

- Enrich grouped order rows with patient names when the grouped dashboard API
  returns patient identifiers but not a display name.

Frontend behavior:

- Uses `getFromOpenElisServerV2`.
- First tries lab number search.
- Falls back to national ID search.
- Uses `patientNameCache` to avoid repeating the same lookup.
- Uses `tileLoadSequence` to avoid stale async responses overwriting newer
  selected tile data.

## Data Flow

1. Component mounts.
2. Metrics load from `/rest/home-dashboard/metrics`.
3. Incoming orders load from `/rest/incoming-orders`.
4. Test sections load from `/rest/user-test-sections/ALL`.
5. Default selected tile becomes `ON_GOING_ORDERS`.
6. Selected tile loads its detail endpoint.
7. Detail rows are normalized for Carbon `DataTable`.
8. Missing patient names are enriched when possible.
9. Search, filter, and pagination are applied in memoized frontend selectors.
10. User clicks action links to move into result entry, validation, report,
    barcode, or sample collection workflows.

## Important State Variables

### Metrics And Data

- `counts`: Top-level dashboard metric counts.
- `timeMetrics`: Average turnaround time values.
- `data`: Detail table rows for the selected dashboard tile.
- `incomingOrdersData`: Incoming external orders for sample collection.
- `testSections`: Department/test-section tab list.

### Selection And View State

- `selectedTile`: Currently opened dashboard tile.
- `selectedTestSection`: Department tab filter.
- `rightPanelView`: Active/backlog view for order queue.
- `leftPanelView`: Active/backlog view for incoming sample collection.
- `dashboardTab`: Which split dashboard tab is selected on smaller layouts.

### Search And Pagination

- `rightSearch`: Search text for the order queue.
- `leftSearch`: Search text for incoming orders.
- `rightPage`, `rightPageSize`: Pagination state for the right panel.
- `leftPage`, `leftPageSize`: Pagination state for the left panel.
- `nextPage`, `previousPage`, `currentApiPage`, `totalApiPages`: Server paging
  state when backend endpoints return paging metadata.

### Async Safety

- `componentMounted`: Prevents state updates after unmount.
- `tileLoadSequence`: Prevents stale API responses from overwriting current tile
  data.
- `patientNameCache`: Avoids repeated patient lookup calls for the same row.

## Search And Filtering

### Order Queue Search

The right-side order queue supports search by:

- Patient ID.
- Patient name.
- Raw lab number.
- Formatted lab number.

### Incoming Order Search

Incoming order search supports:

- Patient name.
- Source.
- Test count/test display text.
- Order ID.
- Patient GUID.
- Test GUID.

### Date Filtering

The dashboard has helper functions for date parsing:

- `parseDisplayDate`
- `parseIncomingOrderTimestamp`
- `isSameLocalDay`
- `isToday`
- `isReceivedToday`

The date parser supports:

- `Date` objects.
- Numeric timestamps.
- Numeric strings.
- Direct JavaScript parseable date strings.
- Slash dates such as `dd/mm/yyyy` or `mm/dd/yyyy`.

For ambiguous slash dates, `en-US` locale prefers month-first. Other locales
prefer day-first.

## Backlog Logic

The order queue has an Active/Backlog split.

- Active rows are treated as today's/current work.
- Backlog rows are older rows based on parsed order date.
- The backlog view has its own empty state and search placeholder.

Incoming order backlog is currently empty by design in this frontend branch.
The component has a placeholder memoized selector for left backlog data, but it
does not invent backlog rows for external orders.

## Navigation And Actions

Rows expose action links based on the workflow:

- Result entry.
- Validation.
- Report.
- Barcode.
- Sample collection for incoming orders.

Incoming order collection navigates to:

```text
/SamplePatientEntry?incomingOrderNumber={externalOrderNumber}
```

The route is built with `getFullPath` so it respects OpenELIS path handling.

## Styling Changes

The dashboard CSS introduces:

- A page shell with a light clinical operations background.
- Responsive metric tile grid.
- Split dashboard header with summary cards.
- Active/backlog segmented controls.
- Search toolbars.
- Sticky table headers.
- Sticky pagination.
- Responsive behavior for tablet and mobile widths.
- Clear empty states.
- Better action button/link treatment.

CSS variables are used at the top of `Dashboard.css` for repeated dashboard
colors, surfaces, borders, shadows, and radius values.

## Fixes And Improvements In This Branch

- Reduced branch scope to dashboard-only differences against `nidan-dev`.
- Removed accidental/noisy changes from backend, Liquibase, Docker, nginx, docs,
  and unrelated frontend files.
- Replaced hardcoded dashboard strings with React Intl keys.
- Added dashboard-specific English labels and empty states in `en.json`.
- Added safer async handling with mounted-state and request sequence checks.
- Added patient-name enrichment for grouped rows.
- Added stronger search support across raw and formatted values.
- Added incoming order display with collection action links.
- Added active/backlog views for order worklists.
- Added dashboard loading and empty states.
- Added responsive CSS for smaller screens.

## What This Branch Does Not Change

- No backend Java code.
- No database schema.
- No Liquibase changelog.
- No Docker configuration.
- No nginx configuration.
- No non-English translation files.
- No global layout/header behavior.
- No authentication or authorization rules.

## Review Guide

When reviewing this branch, focus on:

- Does `Dashboard.tsx` still load the correct existing endpoints?
- Are dashboard strings using React Intl instead of hardcoded UI text?
- Do the action links route users to the expected legacy workflows?
- Does search work for patient ID, name, lab number, order ID, and GUID cases?
- Does the active/backlog split match the intended lab workflow?
- Does the responsive layout remain usable on desktop and mobile?
- Are loading and empty states clear?

The PR should not show unrelated files after the cleanup commit. If it does,
compare the branch against `nidan-dev` and restore unrelated files.

Useful check:

```bash
git diff --name-status nidan-dev...HEAD
```

Expected functional output, excluding this documentation file:

```text
M frontend/src/components/home/Dashboard.css
M frontend/src/components/home/Dashboard.tsx
M frontend/src/languages/en.json
```

## Manual Test Checklist

1. Open the dashboard as an authenticated user.
2. Confirm metric tiles load.
3. Confirm default view opens the ongoing/in-progress worklist.
4. Search by patient ID, patient name, and lab number.
5. Open active and backlog toggles.
6. Confirm department tabs appear where applicable.
7. Click result entry, validation, report, or barcode actions when present.
8. Confirm incoming orders load in the sample collection panel.
9. Search incoming orders by patient name, source, order ID, or GUID.
10. Click `Collect` and confirm navigation includes
    `incomingOrderNumber={externalOrderNumber}`.
11. Resize to tablet/mobile width and confirm controls do not overlap.

## Developer Notes

- Keep dashboard changes in the three scoped files unless a new requirement
  truly needs another file.
- Add new user-facing dashboard text to `en.json` only.
- Do not edit non-English locale files.
- Do not add backend or Liquibase changes for this frontend-only branch.
- If the branch starts showing many unrelated files again, restore them from
  `nidan-dev` before pushing.
