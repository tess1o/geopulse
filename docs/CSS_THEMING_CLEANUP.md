# CSS / theming cleanup: tracking

Branch: `css-theming-cleanup` (cut from `ui-improvements`; merged back manually). One commit per phase.
Verification per phase: `npm run build`, `npm run test:run`, checks on the built CSS, then a manual UI review
(the "Check in the UI" list for each phase) in light, dark and system mode.

## Why

Light/dark mode was broken across the app because of a few root causes:

1. **The preset inverted the dark palette.** `GeopulsePreset.js` flipped the dark surface palette (0 = darkest,
   950 = white). Every Aura token assumes the opposite, so PrimeVue's dark mode was wrong at the source.
   About 1000 lines of `.p-dark .p-xxx { … !important }` patched it in `style.css`, and components patched it again.
2. **No CSS layers.** PrimeVue's runtime styles won ties, which led to 1400+ `!important`.
3. **Confusing and undefined tokens.**
   - `--gp-surface-white` is dark in dark mode, and `--gp-surface-dark` has the same value in both modes.
   - `--gp-border-dark` is white-alpha in light mode.
   - About 150 uses of PrimeVue 3 variables (`--surface-card`, `--primary-color`, …) that don't exist in PrimeVue 4.
   - About 50 `--gp-*` names that are used but never defined.
4. **Broken selectors.**
   - Scoped `:global(.p-dark) .x` compiles to a bare `.p-dark{}` that styles `<html>`.
   - `.p-dark :deep(…)` never matches.
   - Rules aimed at teleported dialogs and popovers never match.
   - Dead `html.dark` and `[data-theme]` branches.
5. **Copy-paste.**
   - The same DataTable dark block in 7 files, dialog styling in 4, page headers in 21, admin cards in 9,
     and the timeline card base in 6.
   - Duplicated map popup palettes and JS colour tables.

## Target architecture

| Piece | Role |
|---|---|
| `src/presets/GeopulsePreset.js` | Single source of colours. Slate in both schemes; PrimeVue components are themed by tokens, not CSS. |
| `src/styles/tokens.css` | All `--gp-*` tokens. The **only** place with light and dark values. Aliases `--p-*` where the concept exists. |
| `src/styles/layers.css` | Layer order: `tailwind-base, primevue, app-components, tailwind-utilities`. |
| `src/styles/base.css` | `html` and `body`: font, text colour, page background. |
| `src/styles/components.css` | Shared `gp-*` classes (the `app-components` layer). |
| `src/styles/primevue-overrides.css` | The few global PrimeVue tweaks that tokens can't express. |
| `src/styles/maps.css` | Map CSS (was `mapStyles.css`). |
| `src/styles/index.css` | Entry point, imported once from `main.js`. |

Conventions (enforced in Phase 7):

- Components use tokens and don't re-colour themselves under `.p-dark`. If a colour must differ between the
  schemes and no token fits, add a semantic token in `tokens.css`.
- A dark-only rule in a scoped style is written as `.p-dark .x`. Never `:global(.p-dark) .x`, which compiles to a
  bare `.p-dark{}`, and never `.p-dark :deep(...)`, which never matches.
- Overlays (Dialog, Popover, Menu) are teleported to `<body>`. Style them through the preset, a `pt` or `class`
  hook plus `:global(.my-overlay …)`, or not at all. `:deep()` from the opener component doesn't reach them.
- No `!important` for beating PrimeVue; the layers take care of that.

### Surface and border tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `--gp-surface-ground` | #f8fafc | #0f172a | page background |
| `--gp-surface-card` | #ffffff | #1e293b | cards, panels, dialogs (= `--p-content-background`) |
| `--gp-surface-muted` | #f8fafc | #334155 | nested block inside a card |
| `--gp-surface-emphasis` | #f1f5f9 | #475569 | stronger nested block |
| `--gp-surface-hover` | #f1f5f9 | #334155 | hover rows (= `--p-content-hover-background`) |
| `--gp-surface-inverse` / `--gp-text-inverse` | #1e293b / #fff | #f8fafc / #1e293b | tooltip-like chips |
| `--gp-border` | #e2e8f0 | #334155 | default border (= `--p-content-border-color`) |
| `--gp-border-medium` | #e2e8f0 | #475569 | inputs, stronger dividers |
| `--gp-border-subtle` | #f1f5f9 | #334155 | faint dividers |

Status colours (`success`, `warning`, `danger`, `info`) each come in five variants: `--gp-<status>` (solid),
`-strong` (hover on a solid fill), `-soft` (tinted background), `-border`, and `-text` (readable text). Each
variant has a dark value.

### Rename map (Phase 4 codemod)

| Old | New |
|---|---|
| `--gp-surface-white`, `--surface-card`, `--surface-0`, `--gp-surface-0`, `--p-surface-card` | `--gp-surface-card` |
| `--surface-ground`, `--gp-surface-ground-dark`, `--gp-surface-darker` used as page background | `--gp-surface-ground` |
| `--gp-surface-light`, `--surface-50`, `--surface-100`, `--surface-section`, `--gp-surface-lighter`, `--gp-surface-50`, `--gp-surface-100` | `--gp-surface-muted` |
| `--gp-surface-gray`, `--gp-surface-medium` | `--gp-surface-emphasis` |
| `--surface-hover` | `--gp-surface-hover` |
| `--gp-surface-dark` / `--gp-surface-darker` inside `.p-dark` rules | delete the rule, because the base token already flips |
| `--gp-border-light`, `--gp-border-dark`, `--surface-border`, `--gp-border-color`, `--gp-surface-border(-dark)` | `--gp-border` |
| `--text-color`, `--gp-text-on-surface-emphasis` | `--gp-text-primary` |
| `--text-color-secondary` | `--gp-text-secondary` |
| `--gp-text-tertiary` | `--gp-text-muted` |
| `--primary-color`, `--gp-primary-color` | `--gp-primary` |
| `--primary-color-text`, `--gp-primary-text` (on a fill) | `--gp-primary-contrast` |
| `--gp-primary-50…900`, `--primary-N` | `--p-primary-N` |
| `--gp-primary-rgb` | `color-mix(in srgb, var(--gp-primary) X%, transparent)` |
| `--gp-error` | `--gp-danger` |
| `--gp-warn-*`, `--gp-warning-color` | `--gp-warning*` |
| `--gp-<status>-light`, `-50`, `-100` | `--gp-<status>-soft` |
| `--gp-<status>-dark`, `-700`, `-800`, `-900` | `-text` in `color`; `-strong` in background and border |
| `--gp-<status>-200`, `-300` | `--gp-<status>-border` |
| `--gp-<status>-contrast` | `--gp-primary-contrast` (white on a fill) |
| PrimeVue 3 palette (`--green-500`, …) | the status token if the use is semantic, otherwise `--p-green-500` etc. |
| `--gp-radius-md`, `--border-radius` | `--gp-radius-medium` |
| `--gp-shadow-dark` | `--gp-shadow-large` |
| `--font-family`, `'Inter', …` (Inter is never loaded) | `--gp-font-family` |
| `--font-mono` and the 7 different monospace stacks | `--gp-font-mono` |

Runtime-set variables are kept: `--gp-friend-marker-color`, `--gp-navbar-datepicker-width`, `--user-color`, `--tag-color`, …

## Metrics

| Metric | Start | After Phase 1 | After Phase 2 | Target |
|---|---|---|---|---|
| `!important` in `src` | 1407 | 1403 | 968 | < 150 |
| lines containing `p-dark` (`.vue` and `.css`) | 1114 in 141 files | 1107 in 140 files | 939 in 139 files | `tokens.css` plus a few justified cases |
| PrimeVue 3 `var(--…)` uses (incl. aliased `--text-color*`) | 564 | 564 | 563 | 0 |
| undefined `--gp-*` names | 60 | 51 (+2 set at runtime) | 51 (+2 set at runtime) | 0 |
| bare `.p-dark{}` rules in the built CSS (excluding token blocks) | 13 | 0 | 0 | 0 |
| `.p-dark :deep(` / `:deep(.p-dark` selectors (never match) | 19 / 2 | 19 / 2 | 19 / 2 | 0 |

---

## Phases

### Phase 0: Branch ✅
- [x] Branch `css-theming-cleanup` from `ui-improvements`.

### Phase 1: Foundation ✅ `3dd5ed2d3`
- [x] Preset: slate in both schemes, dark palette in the normal orientation, dark semantic tokens
  (content, overlay, formField, text, list, navigation) matching the `--gp-*` dark values.
- [x] Preset component tokens: datatable header and footer colours, dark datatable border, inverted tooltip, toast
  typography, emerald secondary button, card shadow. Removed the badge, autocomplete and chart overrides; they
  never applied or only repeated the defaults.
- [x] `src/styles/*` created; `style.css` and the empty `flags.css` deleted; `mapStyles.css` moved to `styles/maps.css`.
- [x] PrimeVue `cssLayer` enabled, with the same order as `layers.css`.
- [x] Tailwind `darkMode: ['selector', '.p-dark']`.
- [x] `useThemeMode().isDarkMode` follows OS changes in system mode; `initializeThemeMode()` is idempotent;
  `utils/cssTokens.js` provides `readCssToken()`.
- [x] Temporary aliases for the old token names in `tokens.css`.
- [x] Removed the unused global `.container` override.

**Check in the UI:**
- Tooltips are inverted in dark mode.
- Dark inputs are one step darker than cards.
- Maximized dialogs are full-screen.
- Nothing else should change visibly.

### Hotfix: `:global(.p-dark) .x` styling `<html>` ✅ `3b2604234`
(Pulled forward from Phase 3 because it caused the red full-screen flash on reload in dark mode.)
- [x] 13 rules in 9 files rewritten to `.p-dark .x`: AppNavbar, AppNavbarWithDatePicker,
  RestoreMaintenanceScreen, LocationAnalyticsMap, NotesViewerDialog, and 4 admin pages.
- [x] Dead `html.dark` / `[data-theme="dark"]` selectors removed from the admin pages.

**Check in the UI:**
- No red flash on F5 in dark mode.
- The navbar demo badge, the notes delete-confirm box and the source chips, the analytics map overlay and refresh
  badge, and the admin mobile cards now get their dark styles. These never applied before.

### Phase 2: Remove the global override wall ✅
- [x] Deleted the "LEGACY OVERRIDE WALL" section of `styles/primevue-overrides.css` (about 1120 lines, 435
  `!important`): every `.p-dark .p-*` restyle and the light `!important` restyles of toast, textarea, confirm dialog,
  dialog and tooltip. Much of it never matched anyway: PrimeVue 3 classes (`.p-highlight`, `.p-tabs-nav`,
  `.p-confirm-dialog-icon`, `.p-variant-on`, `.p-dropdown*`) and `html body .p-dark .p-tooltip` (the tooltip is
  teleported to `<body>`, outside `.p-dark`).
- [x] Re-added in the preset: datepicker "today" in emerald in both schemes; toast radius 8px and 1rem padding.
  Kept in `primevue-overrides.css` without `!important`: compact tooltip text (0.75rem / 500), `overflow: hidden`
  on dialogs, and phone layout for confirm dialogs (inside the viewport, stacked full-width buttons). The one
  justified `!important` left is the toast z-index 9999, since PrimeVue sets it inline.
- [x] Removed the DataTable `:pt` dark classes from StaysTable, TripsTable, DataGapsTable and TechnicalDataPage.
- [x] Build: no `.p-dark .p-` selectors come from the global stylesheet. The 9 unscoped ones left in the built CSS
  come from component `<style>` blocks: AppNavbarWithDatePicker (datepicker, Phase 3/4) and AppNavigation
  (`.p-dark .p-drawer`, Phase 4).

**Check in the UI:**
Every PrimeVue component type in both modes. Expected visible changes:
- dark inputs, selects, multiselects, checkboxes and textareas are one step darker than cards (formField);
- light textareas are white like other inputs, not slate-50;
- datepicker "today" is emerald in light mode too; selected stays blue;
- toasts use Aura's tinted severity style in both modes; dark toasts are no longer card-coloured;
- confirm dialogs use the preset look: no header or footer dividers; buttons keep their own severity instead of
  all being forced to filled primary;
- dark inactive tabs in `TabContainer` are slate-700 (the muted surface) instead of slate-900;
- dark paginator page buttons have no borders.

Components to check:
- inputs, textarea, select, multiselect, autocomplete;
- datepicker (today, selected, button bar), float labels, checkbox, toggle;
- dialog and confirm dialog, toast (4 severities), tooltip, popover;
- tabs and tabmenu, card, datatable and paginator, breadcrumb, drawer.

### Phase 3: Fix broken and dead selectors
- [ ] `.p-dark :deep(...)`, 19 selectors that never match: EditFavoriteDialog 497–515,
  TripClassificationDialog 798, DataExportImportPage 351/355/548, FriendsLocationTab 167–176.
- [ ] `:deep(.p-dark …)`: GeofenceTemplatesTab 707–708.
- [ ] Teleported-overlay rules. Delete them if the preset covers them; otherwise use a `pt`/`class` hook plus `:global(.overlay-class …)`:
  - `:deep(.p-dialog*)` in OidcProviderDialog, EditFavoriteDialog, TripClassificationDialog,
    TimelineRegenerationModal, TripDetailsDialog, GpsPointEditDialog;
  - EditFavoriteDialog footer buttons;
  - popovers in Home.vue 567 and SettingsSearchTrigger 261.
- [ ] Home.vue: `:root` inside `<style scoped>` never matches, so the light `--home-*` variables are undefined.
- [ ] FriendsMap.vue 725–740: dead `.dark` rules. BarChart.vue: watches a `data-theme` attribute that nothing sets.
  DataExportTab and DataImportTab: `:root[class*="dark"]` becomes `.p-dark`.
- [ ] PrimeVue 3 class `.p-highlight` (8 places in 6 files) becomes the PrimeVue 4 class names.
- [ ] DataTable rules that never match: `.X-table :deep(.p-datatable)` (the class is already on the root), and
  paginator rules on tables with `:paginator="false"`.
- [ ] AppNavbarWithDatePicker 591: `:deep()` inside an unscoped block.
- [ ] mapPopupContent.css 95: `.p-dark .stack-item-weather` overrides the weather colours.

**Check in the UI:**
- the dialogs listed above in both modes;
- the Home "what's new" popover;
- the settings search popover;
- Home in light mode;
- the Friends map;
- the Data export/import tabs.

### Phase 4: Token sweep
- [ ] Node codemod applying the rename map; context-dependent cases are reviewed by hand.
- [ ] Remove the temporary aliases from `tokens.css`, including `--text-color` and `--text-color-secondary`.
- [ ] Delete the no-op `.p-dark` blocks (about 52 files that only swap tokens which already flip, e.g. StayCard, admin pages).
- [ ] Hardcoded colour plus `.p-dark` pairs (about 49 files): replace the literals with tokens and delete the dark
  block. Examples: Home, ExploreGeoPulsePanel, TripReplayControls, TipOfDayCard, TimelineMap, CoverageExplorerPage.
- [ ] Raw Tailwind palette classes without a dark variant become primeui semantic classes, e.g. GpsFilteringSettings
  and AdminOidcProvidersPage (where the PrimeFlex class `border-round` becomes `rounded`).
- [ ] Remove `!important` that the layers make unnecessary. Top offenders: TechnicalDataPage (130), BaseButton (94),
  AppNavbarWithDatePicker (86), maps.css (104).
- [ ] Component-local variables drop the `gp-` prefix (NotificationBell's `--gp-bell-*` becomes `--bell-*`).
- [ ] Fonts: `--gp-font-family` and `--gp-font-mono` everywhere; drop the `'Inter'` references.

**Check in the UI:** a broad pass over all pages in both modes (list below).

### Phase 5: Merge copy-pasted blocks
- [ ] DataTable: Family A (StaysTable, TripsTable, DataGapsTable, TechnicalDataPage) and Family B (PlaceVisitsTable,
  FavoritesManagementPage, GeocodingManagementPage) become the preset plus an optional `.gp-data-table`.
- [ ] Page headers: `PageContainer` `title`/`subtitle` props, or a shared `.gp-page-header` (21 files).
- [ ] Admin pages: breadcrumb, card and mobile list card move to shared classes (9 files).
- [ ] Timeline cards: the shared base of StayCard, TripCard, DataGapCard and the `Overnight*` variants moves to
  `components/timeline/timeline-card.css`.
- [ ] Stat tiles use `StatCard.vue` or `.gp-stat`; empty states use `.gp-empty-state`.
- [ ] `.settings-panel`: remove the duplicate in UserProfilePage 848.

**Check in the UI:**
- the tables;
- the admin pages;
- the timeline sidebar cards;
- the management pages' headers.

### Phase 6: Maps and JS colours
- [ ] Popup CSS (mapPopup, rawGpsPointPopup, mapPopupContent) gets shared `--gp-map-popup-*` tokens and is
  imported once, globally.
- [ ] Fix the wrong fallbacks. Merge the duplicated dark Leaflet tooltip gradient. Move `.p-contextmenu` and
  `.loading-messages` out of the map CSS.
- [ ] Markers (weather, note, photo, trip reconstruction) stay light, because the tiles don't switch theme. Add a
  comment saying so, and take the shared colours from tokens.
- [ ] `src/maps/shared/mapColors.js` becomes the single table for:
  - timeline markers (`timelineMarkerBuilder` and `mapHelpers`);
  - trip endpoint gradients (`tripEndpointMarkerBuilder`, `mapHelpers`, `useMapHighlights`);
  - photo blue and note purple.
- [ ] Movement-type colours: DigestMetrics and JourneyInsights disagree; keep JourneyInsights' colours.
- [ ] BarChart: use `useThemeMode().isDarkMode` and `readCssToken`; remove the leaking MutationObserver and the
  hardcoded fallbacks.

**Check in the UI:**
- timeline map popups and tooltips in both modes;
- raw GPS popup;
- weather, note and photo markers;
- the dashboard chart, which should follow a theme switch without a reload.

### Phase 7: Guard test
- [ ] Add `src/styles/cssTokens.test.js` (vitest). It fails on:
  - undefined `--gp-*` names;
  - PrimeVue 3 variables;
  - `html.dark`, `[data-theme`, `.dark `;
  - `:global(.p-dark)` followed by more selector text, and `.p-dark :deep(`;
  - `:root` inside `<style scoped>`;
  - `.p-dark` token definitions outside `tokens.css`.
- [ ] Final metrics in the table above.

---

## Full UI checklist (for Phases 4–6)

Check each in light, dark and system mode (switch the OS theme on an open page); desktop and phone widths.

- **Pages:**
  - Home/landing; Timeline (map, cards, popups, trip replay); Dashboard (chart); Journey insights / Digest
  - Trips workspace; Places; Favorites and Geocoding management; Technical data; Data export/import; Share links
  - Friends (map and tables); Location analytics; Coverage explorer
  - Profile, settings and timeline-preferences tabs; the 9 admin pages
  - AI chat; Help; 404 and error pages; restore-maintenance screen; login and register
- **Components:**
  - Notification bell and navbar demo badge
  - Toasts (4 severities), confirm dialogs, tooltips

## Decisions and deviations log

- 2026-10-01: single palette is **slate**; scope is full and phased; work is on a separate branch.
- `--gp-spacing-xxl` is kept and defined (2rem) instead of being renamed to `2xl`, to match the existing xs…xl scale.
- `--gp-primary-light` and `--gp-primary-dark` are kept as brand shades (light and dark values in `tokens.css`)
  instead of being renamed.
- Dark primary is `primary.500` (#3b82f6) with white text, the current GeoPulse dark value. Aura's default is
  primary.400 with dark text.
- Dark highlight (selected option, row, date) is solid primary with white text, matching light mode.
- The `:global(.p-dark)` fix was pulled forward from Phase 3 (red flash on reload).
- Phase 2, datepicker "today": emerald (`{emerald.500}`, white text) in **both** schemes. Before, it was emerald
  only in dark mode; the navbar's comment gave the reason: keep today distinct from the blue selected date.
  AppNavbarWithDatePicker's own dark today/selected rules are now redundant; they go in Phase 4.
- Phase 2, confirm-dialog icon: Aura default, the modal text colour. This is what already showed: the warning
  colour rule used the PrimeVue 3 class and never matched. A fixed warning colour would also be wrong for
  non-destructive confirms.
- Phase 2, tabs: nothing in the preset. The app doesn't use PrimeVue TabMenu or Tabs; `TabContainer.vue` renders
  its own markup with `p-tabmenu-*` class names and styles it with tokens that already flip.
- Phase 2, toasts: Aura's severity colours (proper dark variants) instead of the old soft background with a solid
  border in light and card background in dark. The confirm dialog's light restyle (dividers, custom paddings,
  1.125rem title, all non-outlined buttons forced to primary) was dropped, as planned: tokens can't express it.
