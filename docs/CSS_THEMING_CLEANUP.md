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
| `src/styles/layers.css` | Layer order: `tailwind-base, vendor, primevue, app-components, tailwind-utilities`. |
| `src/styles/vendor/*.css` | Third-party map CSS (MapLibre, Leaflet plugins) imported `layer(vendor)` by the lazily loaded map code. Leaflet's own CSS is imported the same way from `index.css`. |
| `src/styles/base.css` | `html` and `body`: font, text colour, page background. |
| `src/styles/components.css` | Shared `gp-*` classes (the `app-components` layer). |
| `src/styles/primevue-overrides.css` | The few global PrimeVue tweaks that tokens can't express. |
| `src/styles/maps.css` | Map CSS (was `mapStyles.css`). The popup CSS in `src/maps/shared/styles` is imported next to it from `index.css`. |
| `src/maps/shared/mapColors.js` | Marker colours used from JS; the ones CSS also uses are mirrored as `--gp-map-marker-*` tokens. |
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

| Metric | Start | After Phase 1 | After Phase 2 | After Phase 3 | After Phase 4 | After Phase 5 | After Phase 6 | Target |
|---|---|---|---|---|---|---|---|---|
| `!important` in `src` | 1407 | 1403 | 968 | 868 | 495 | 326 | 142 | < 150 |
| lines containing `p-dark` (`.vue` and `.css`) | 1114 in 141 files | 1107 in 140 files | 939 in 139 files | 869 in 140 files | 170 in 21 files | 104 in 14 files | 4 in 1 file (`tokens.css`) | `tokens.css` plus a few justified cases |
| PrimeVue 3 `var(--…)` uses (incl. aliased `--text-color*`) | 564 | 564 | 563 | 555 | 0 | 0 | 0 | 0 |
| undefined `--gp-*` names | 60 | 51 (+2 set at runtime) | 51 (+2 set at runtime) | 51 (+2 set at runtime) | 0 (+2 set at runtime) | 0 (+2 set at runtime) | 0 (+2 set at runtime) | 0 |
| bare `.p-dark{}` rules in the built CSS (excluding token blocks) | 13 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| `.p-dark :deep(` / `:deep(.p-dark` selectors (never match) | 19 / 2 | 19 / 2 | 19 / 2 | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 | 0 |

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

### Phase 2: Remove the global override wall ✅ `a0c91dbb7`
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

### Phase 3: Fix broken and dead selectors ✅ `d95b73da7`
Verified first: a teleported `.p-dialog` and its ancestors carry no `data-v-*` attribute, so scoped `:deep(.p-dialog…)`
never matches, while a `class` passed to `<Dialog>`/`<Popover>` does land on the overlay root. Rules that never
matched were deleted, since what users see today is already the preset look. Rules with a real intent were moved
to a working selector.
- [x] `.p-dark :deep(...)` (19) and `:deep(.p-dark …)` (2): all deleted. EditFavoriteDialog, TripClassificationDialog,
  DataExportImportPage (the base rules already use tokens that flip) and GeofenceTemplatesTab (Aura already
  styles `.p-invalid`).
- [x] FriendsLocationTab: every `.mode-toggle-compact .p-button` rule was dead, light ones included. PrimeVue 4's
  SelectButton renders `.p-togglebutton`, not `.p-button`. Deleted; the one real intent (hide the labels under
  480px) is now a plain scoped `.toggle-label` rule.
- [x] Teleported overlays:
  - `:deep(.p-dialog*)` deleted in OidcProviderDialog, EditFavoriteDialog, TripClassificationDialog,
    TimelineRegenerationModal, TripDetailsDialog and GpsPointEditDialog; the preset and the `gp-dialog-*` classes
    cover them. TimelineRegenerationModal had an inline `width: 500px` whose phone overrides never applied, so
    it overflowed small screens. It now uses `gp-dialog-sm`.
  - EditFavoriteDialog footer and content button rules deleted.
  - Popover content padding in Home.vue and SettingsSearchTrigger moved to the components' unscoped blocks as
    `.home-wn-popover .p-popover-content` / `.settings-search-popover .p-popover-content`.
- [x] Home.vue: the light `--home-*` palette moved from the scoped `:root` (compiled to `[data-v-…]:root`) to
  `.landing-page`, so the light landing page gets its intended background and text colours.
- [x] FriendsMap: dead `.dark` rules and the unused header and title rules deleted. The loading and empty overlays
  used `--p-surface-50`, which stays light in dark mode; they now use `--gp-surface-ground`. BarChart no longer
  watches `data-theme`. DataExportTab and DataImportTab: `:root[class*="dark"]` becomes `.p-dark`.
- [x] `.p-highlight`: all 8 removed. DataTable paginators already listed `.p-paginator-page-selected` next to it.
  FriendsPage's `.p-tabs-*` and `.p-tabmenu-nav`/`.p-menuitem-*` rules (PrimeVue 3 TabView and TabMenu markup;
  the page uses `TabContainer`) were deleted.
- [x] DataTable: the 11 `.X-table :deep(.p-datatable)` rules and every paginator rule in StaysTable, TripsTable and
  DataGapsTable (`:paginator="false"`) deleted.
- [x] AppNavbarWithDatePicker: the `:deep()` rule in the unscoped block shipped as a literal `:deep(`, which
  browsers drop. Deleted; it only targeted the input wrapper anyway, not the teleported panel.
- [x] mapPopupContent.css: the dark weather-chip colour is now `:where(.p-dark) .stack-item-weather`, before the
  severity modifiers, so clear, rain, storm and snow keep their colours in dark mode.
- [x] Built CSS: no literal `:deep(`, no `[data-v-…]:root`, no `.p-highlight`, no `.dark ` selectors.

Left for later phases (found during this one):
- `maps.css` `.p-contextmenu .p-menuitem-link` uses PrimeVue 3 classes (Phase 6, with the context-menu move).
- AppNavbarWithDatePicker's unscoped `.p-dark .p-datepicker …` block and AppNavigation's `.p-dark .p-drawer`
  (Phase 4).
- The Family A and B DataTable dark blocks still restyle what the preset now covers (Phase 5).

**Check in the UI:**
- the dialogs listed above in both modes (they should look unchanged, apart from TimelineRegenerationModal fitting on
  a phone);
- the Home "what's new" popover: no inner padding, 18–22rem wide;
- the settings search popover: slightly tighter padding;
- Home in light mode: the landing background, header border and hero text colours now apply (a visible change);
- the Friends map: the loading and empty overlays are dark in dark mode; the live-location/timeline-history toggle labels are hidden
  under 480px;
- the Data export/import tabs in dark mode;
- timeline map popups in dark mode: weather chips are coloured by severity again.

### Phase 4: Token sweep ✅
- [x] Codemod (a style parser, so each `var()` is renamed with its property and whether it sits in a `.p-dark` rule):
  about 1850 renames in 180 files. Context-dependent cases:
  - Constant-dark tokens (`--gp-surface-dark`, `-darker`, `--gp-border-dark`) all sat in `.p-dark` rules and
    became `--gp-surface-card`, `--gp-surface-ground` and `--gp-border`.
  - Old status shades were fixed by hand after the codemod. Dark `-900` backgrounds were tints, so they become
    `-soft`, not `-strong`. `-100`/`-300` shades used as text become `-text`.
  - `--p-text-color-secondary`, `--p-surface-border` and `--p-shadow-lg` were undefined in PrimeVue 4
    (SharedLocationPage and others); semantic `--p-*` uses now go through their `--gp-*` aliases.
  - `--gp-surface-card` used as a text colour (white text on a fill, a dark code block) becomes
    `--gp-primary-contrast` or a fixed palette colour.
  - JS reads (BarChart) updated.
- [x] Temporary aliases removed from `tokens.css`, including `--text-color` and `--text-color-secondary`. No
  PrimeVue 3 variables and no undefined `--gp-*` names are left (apart from the two set at runtime).
- [x] `.p-dark` blocks, handled by tools and then per file:
  - Exact no-ops (same value as the base rule, which now flips) deleted.
  - Pairs where the dark token's light value equals the base literal: the base takes the token.
  - Pattern rules:
    - primary text brightened in dark becomes the new `--gp-primary-text`;
    - `p-primary-50`/`900` becomes `--gp-primary-soft`;
    - secondary text swapped in dark: the override is dropped;
    - same-family status text: the override is dropped.
  - The remaining 351 rules in 93 files were decided per file. The policy: the component uses its base token in
    both modes, and dark-only surface swaps are dropped. Literal pairs got tokens. PrimeVue restyles already
    covered by the preset were deleted: the drawer, datepicker, the card inside the location-source cards, and
    the teleported popover overrides.
- [x] Landing page (Home, ExploreGeoPulsePanel, TipOfDayCard): its palette moved to `tokens.css` as `--gp-landing-*`
  (background, glows, header, text, accent, glass panels). This also fixed light-only styles that stayed light in
  dark mode (the secondary hero button, the mobile panel tabs).
- [x] Tailwind: GpsFilteringSettings uses `border-surface` / `text-muted-color`. AdminOidcProvidersPage's delete
  notes use status tokens, and the PrimeFlex `border-round` is gone. Muted `text-surface-400/500` in the friends
  components becomes `text-muted-color`. Mid-tone icon colours (`text-*-500`) stay, since they read in both modes.
- [x] `!important`: 868 down to 495.
  - BaseButton: the global `.p-button` rules left the component. The radius went to the preset `button` token.
    Values Aura already has (label weight, icon-only sizes, disabled opacity) were dropped, and so were the dead
    `.p-buttonset` and `.p-ink` rules (ripple is off). The rest moved to `primevue-overrides.css` without
    `!important`.
  - Navbars: the two components repeated the same global toolbar rules and settled them by load order.
    AppNavbarWithDatePicker now uses `.gp-app-navbar-toolbar.gp-app-navbar-with-datepicker`, and its dead
    `.gp-datepicker` rules (the class is never rendered) were deleted.
  - PrimeVue-only overrides in AIChatPage, TripsManagementPage, OidcProvidersSection and TimelinePage were
    stripped.
  - Kept on purpose:
    - NotificationBell, where the global `.p-button` shadow rule is more specific than its own;
    - the navbar's override of the bell size;
    - the Popover/Dialog size and position, which PrimeVue sets inline;
    - the `gp-dialog-*` widths;
    - reduced-motion rules;
    - Home's static hero button, which cancels the global hover lift;
    - the toast z-index.
  - The rest is in the Phase 5 table files (246) and the Phase 6 map CSS and map components (148).
- [x] Component-local variables: NotificationBell's `--gp-bell-*`, `--gp-footer-btn-*` and `--gp-mark-seen-*` are now
  `--bell-*` and so on. The runtime-set `--gp-friend-marker-color` and `--gp-navbar-datepicker-width` are kept.
- [x] Fonts: every monospace stack is `var(--gp-font-mono)`, and the one `'Inter'` is `var(--gp-font-family)`.
- [x] Bugs found along the way:
  - ActivitySummaryCard's `:global(.p-tooltip .p-tooltip-text)` stripped the padding and background from every
    tooltip in the app once the dashboard had loaded. It is now scoped to its own `pt` class.
  - AdminAuditLogsPage's JSON viewer had dark text on a dark background in dark mode.
  - StayDetailsDialog's duration badge was blue text on a solid blue background.
  - FriendsMap-style non-flipping `--p-surface-N` surfaces were replaced in AIChatPage and OnboardingTour.

Left for later phases:
- Phase 5: the Family A and B DataTable dark blocks and their `!important` (StaysTable, TripsTable, DataGapsTable,
  TechnicalDataPage, PlaceVisitsTable, FavoritesManagementPage, GeocodingManagementPage).
- Phase 6: map CSS (`maps.css`, the popup CSS, weather markers, the map layer components).

**Check in the UI:** a broad pass over all pages in both modes (list below). Expected visible changes:
- Dark mode:
  - Nested blocks that used to be forced to the card colour now show the muted surface (#334155), as in light mode
    (timeline sidebar blocks, dialogs, digest, mobile table cards).
  - Tinted accents keep their light-mode strength instead of a boosted dark one.
  - Primary text and links use the brighter `--gp-primary-text`.
  - Page backgrounds that were forced to the card colour are the ground colour (Oidc callback, shared location,
    shared timeline).
- Light mode:
  - Borders that used `--surface-border` now show (it was undefined, so the whole declaration was dropped).
  - Some literal greys moved to the slate tokens.
  - The login, register and shared-location gradients are card to ground.
- Everywhere:
  - Buttons with the `rounded` prop are pill-shaped again (the global radius `!important` used to flatten them).
  - There is no focus ring after a mouse click (Aura's `focus-visible` ring stays).
  - Tooltips keep their padding and background after visiting the dashboard.
- Landing page: dark mode uses the same glass panels via tokens; the hero eyebrow and tip icon are emerald
  (#34d399) in dark mode.

### Phase 5: Merge copy-pasted blocks ✅
- [x] DataTable: the preset alone covers Family A and B. Every dark rule repeated a preset value (header and header
  cells = slate-900, rows = card, hover = `content.hover`, borders = `content.border`), so all 7 dark blocks were
  deleted (about 170 `!important`). No `.gp-data-table` class was needed: the only extra in the blocks, the rounded
  wrapper, targeted `.p-datatable-wrapper`, a PrimeVue 3 class. PrimeVue 4 renders `.p-datatable-table-container`
  and already sets `overflow: auto` on it inline.
  - Dead along the way: every `.p-datatable-wrapper` rule (also in AdminFullBackupSection), TechnicalDataPage's
    `.p-dark .table-section :deep(.p-card-body)` (BaseCard is not a PrimeVue Card) and `.p-dark .gp-data-table`
    (the class was never rendered).
  - TechnicalDataPage paginator: the base rules (ground background, border, page size) moved above the media
    queries, so the phone sizes win by source order and lost their `!important`. The selected-page rule repeated
    the preset highlight and was deleted.
  - PlaceVisitsTable gets `rowHover`; the other tables already hover because they have a `selectionMode`.
- [x] Page headers: the `.gp-page-header*` classes moved from PageContainer's scoped block to `components.css`
  (`.gp-page-header > .gp-page-header-content > (.gp-page-header-text > .gp-page-title + .gp-page-subtitle) +
  .gp-page-actions`). PageContainer renders the same markup for its `title`/`subtitle` props. The 21 pages that
  build their own header (12 app pages, 9 admin pages) now use these classes and dropped their local copies.
  Kept per page: the side padding on the phone settings pages (UserProfile, TimelinePreferences), the compact header
  of the full-height AI chat, no bottom margin on Notifications (its column already has a gap), and button sizing in the actions of TimelinePreferences, Invitations and Campaigns.
- [x] Admin pages: `.gp-admin-page` (padding), `.gp-admin-breadcrumb`, `.gp-admin-card` (was `.card`) and the phone
  list card `.gp-admin-list` / `.gp-admin-list-card` (`-header`, `-body`, `-actions`, `--interactive` for the
  clickable user cards) in `components.css`. AdminUserDetailsPage keeps its own sectioned `.card`.
- [x] Timeline cards: `components/timeline/timeline-card.css` holds the shared base (card box, hover, title row,
  timestamp, subtitle, phone sizes, long-press touch rules). Each card includes it with
  `<style scoped src="./timeline-card.css">` before its own block, which keeps only its colours and body styles.
  Scoped on purpose: ShareLinksPage has an unrelated `.timeline-card` class.
- [x] Empty states: `.gp-empty-state` (`-icon`, `-title`, `-message`) with `--compact` (dashboard cards) and
  `--panel` (Friends tabs) in `components.css`. Moved: StaysTable, TripsTable, DataGapsTable (success colour kept
  locally), PlaceVisitsTable, Favorites/Geocoding management, TechnicalDataPage (table and phone list),
  TopPlacesContent, RouteAnalysisContent, FriendsListTab, InvitationsTab and FriendsMapTab. BaseCard's
  `.gp-card .no-data-*` rules were deleted: they were scoped and BaseCard has no such elements (slot content carries
  the parent's scope id), so they never matched.
- [x] Stat tiles: no change. The tiles that repeat across pages already use `MetricItem` (TimelineReportsPage,
  TechnicalDataPage); LocationAnalytics' `.stat-item` and the admin list-card stats are inline stats, not tiles; the
  admin dashboard's four tiles are the only hand-rolled set and aren't repeated elsewhere.
- [x] `.settings-panel`: defined once in `components.css`. The copies in `timeline-preferences/shared-styles.css`
  (loaded only with those tabs) and UserProfilePage's `:deep()` are gone.

**Check in the UI:**
- Tables (Timeline data tables, Place visits, Technical data, Favorites, Geocoding) in both modes:
  - dark header and header cells are slate-900, rows card-coloured, hover slate-700 — as before;
  - selected rows use a soft primary tint with normal text in both modes (light primary-50, dark the
    `--gp-primary-soft` tint) instead of solid blue with white text; in dark mode they now show at all (the old
    `tr { background: … !important }` hid the selection);
  - light Place-visits rows now hover (the other tables already did);
  - dark paginators in Place visits, Favorites and Geocoding are card-coloured, as in light mode; Technical data's
    paginator keeps the header colour in both modes, and its dark page buttons lose the extra borders.
- Page headers (Profile, Timeline preferences, Data export/import, Debug export/import, Help, Share links, Location
  sources, Timeline jobs, AI chat, Notifications, all admin pages): title 1.75rem/700 (was 2rem/600 on the app
  pages), subtitle 1rem, 1rem gap below the header (was 2rem on the app pages, 1.5rem on admin), same as pages that
  use PageContainer's `title` prop. On phones the actions stack under the title.
- Admin pages: same padding and breadcrumb spacing everywhere (Campaigns and User details used their own); content
  cards gain a 1px border and the card shadow token (the Campaigns table card had no styling at all before); phone
  list cards use the card shadow token, and only the user cards scale on tap.
- Timeline sidebar cards look unchanged; the data-gap cards now also suppress text selection on long press.
- Empty states: the four timeline/place tables and Favorites/Geocoding/Technical data look the same apart from a
  slightly smaller title (1.1rem) on the management pages; the dashboard card and Friends empty states are unchanged.

### Phase 6: Maps and JS colours ✅
- [x] Vendor layer: Leaflet, leaflet.fullscreen, leaflet.markercluster and MapLibre CSS are imported with
  `layer(vendor)` (Leaflet from `index.css`, the rest through `styles/vendor/*.css` from the lazily loaded map code).
  The lazy chunks used to land after `index.css` and win ties, which is what most map `!important` was for. Layer
  order is now `tailwind-base, vendor, primevue, app-components, tailwind-utilities` (layers.css and main.js). The
  vendor CSS's own `!important` (fullscreen sizing) is nothing we override.
- [x] Popup CSS (mapPopup, mapPopupContent, rawGpsPointPopup) uses `--gp-map-popup-*` tokens and is imported once
  from `index.css`; the per-component imports and `<style src>` blocks are gone. The trip hover card uses
  `--gp-map-hover-card-*`, plain Leaflet tooltips `--gp-map-tooltip-*`. In dark mode both are the same slate glass
  panel, so the hover-card tokens point at the tooltip ones (the duplicated dark gradient is defined once).
  Stack-popup rows use the timeline tint tokens and the status `-text` tokens in both modes. The note row gets a new
  `--gp-timeline-purple-text`.
- [x] Fallbacks: `var(--gp-…, literal)` fallbacks in the map code (55) removed; the tokens always exist and several
  fallbacks didn't match them. Bug found: RasterTripPlanLayer's pin dot. Phase 4 renamed the undefined
  `--gp-danger/warning-contrast` to `--gp-primary-contrast`, which made it white on its white inner circle (and the
  circle used `--gp-surface-card`, dark in dark mode). Both are literal again (#ffffff, #7f1d1d / #7c2d12).
- [x] `maps.css`:
  - Dark rules replaced by tokens (attribution, tooltip, fullscreen button). The dark fullscreen icons are a
    `.p-dark` redefinition of the plugin's own `--fullscreen-icon-enter/-exit` in `tokens.css`, rather than repeating
    the data URIs in two selectors.
  - Map backdrop: `--gp-map-backdrop` (#f0f0f0 / card) on both hosts' `.base-map`. Their `.p-dark .base-map` rules and
    the `.p-dark .leaflet-container` gradient (only ever hidden behind `.base-map`) are gone.
  - Dead rules deleted: the `.timeline-page` phone block (TimelineMap's more specific copies always won; its one unique
    rule, the control container's `z-index: auto`, moved to TimelineMap), the `.marker-cluster-small/medium/large`
    colours (lost to the lazily loaded MarkerCluster.Default.css; every cluster group has its own icon anyway), the
    avatar `.marker-container`/`.status-badge` rules and the timeline `.marker-inner` rules (never rendered), and the
    PrimeVue 3 `.p-contextmenu .p-menuitem-link` rules.
  - `.p-contextmenu` radius moved to the preset (`contextmenu.root.borderRadius = border.radius.xl`); the shadow and
    border are Aura's.
  - `.loading-messages` moved into TimelineContainer and TimelinePage, the only users, which already restyled it; the
    layout properties they relied on (flex column, centred, 200px min height, spinner margin) moved with it.
  - The `!important` left (22) is for inline styles that Leaflet or the marker builders set (position, margins,
    transform, transition, inner-div borders) and the reduced-motion/high-contrast blocks.
- [x] Markers stay light in dark mode, because the tiles don't switch theme. A comment says so in each marker file.
  Their dark rules are deleted: RasterTimelineLayer's stack and cluster markers, VectorTimelineLayer's stack marker
  and count badge, the cross-type marker chip, the stay weather badge, the trip replay marker. The vector friend marker's
  ring is white in both modes (it was `--gp-surface-card`). Shared marker colours are `--gp-map-marker-*` tokens
  (note, photo, weather), with no dark values.
- [x] `src/maps/shared/mapColors.js` is the single JS table:
  - timeline markers: `timelineMarkerBuilder` exports `resolveTimelineMarkerVisual`, and `mapHelpers` uses it instead
    of its copy;
  - trip endpoint gradients: `tripEndpointMarkerBuilder` and `mapHelpers`. `useMapHighlights`' `highlightConfig` (the
    other copy of the colours) was never read, so it was deleted;
  - photo blue and note purple (MapLibre paint, canvas icons, the cross-type layer).
  - `mapColors.test.js` checks that the note and photo colours match their tokens, and that marker tokens have no
    dark value. Unused `MARKER_COLORS` entries in `mapHelpers` were dropped.
- [x] Raster single-note markers are violet like stacks and like every note on the vector map (they were teal, the
  timeline stack-marker colour).
- [x] Movement-type colours: `src/utils/movementTypeColors.js`, with JourneyInsights' values; DigestMetrics now shows
  public transport pink and train indigo (both were slate).
- [x] BarChart: colours come from `readCssToken` and are re-read when `useThemeMode().isDarkMode` changes. The
  MutationObserver, never disconnected, is gone, and so are the hardcoded colour fallbacks. Fonts use
  `--gp-font-family` (Inter is never loaded). The tooltip is inverted like PrimeVue tooltips
  (`--gp-surface-inverse`/`--gp-text-inverse`). Before, it was #374151 in both modes.

- [x] `!important` in TechnicalDataPage (39), GeocodingManagementPage (16) and FavoritesManagementPage (15): none was
  needed.
  - Action buttons: the only real competitor is the global phone rule `.p-button.p-button-sm` in
    `primevue-overrides.css`, which has the same specificity as a scoped `.action-button`, so order decided.
    `.p-button.action-button` outranks it.
  - Custom hover colours removed in favour of PrimeVue's text-button hover per severity. The old ones were a #3b82f6
    fill under a #1a56db icon, and light red/cyan tints that stayed light in dark mode.
  - TechnicalDataPage phone padding: `.gp-page-container.gp-page-container--fullwidth` outranks PageContainer's own
    rules (before, it was a specificity tie). The `.gp-page-header`/`.gp-page-container` focus-border rules removed
    everything from elements that have no border, outline or focus. The duplicate BaseCard margin rule was merged.
  - The datepicker and input widths only competed with the PrimeVue layer.
  - Favorites marker reset: Leaflet's CSS is in the vendor layer now.

**Check in the UI:**
- Technical data, Geocoding and Favorites tables: action buttons are the same size at desktop and phone widths. Hover
  now uses the button's severity tint (view blue, edit grey, delete red, reconcile/map cyan), also in dark mode.
  Technical data on a phone: same side padding, date pickers full width.
- Timeline map popups (stay, trip, stack list with weather chips, cross-type list), the raw GPS popup and the trip
  hover card, on both engines and in both modes. They should look as before. Small differences:
  - dark: the popup icon is light blue instead of #2563eb, and actions are #93c5fd instead of #bfdbfe;
  - dark: stack rows use the standard dark tints (stay, trip, data gap and note slightly different shades);
  - light: the photo row background is #f8fafc instead of slate-100.
- Plain Leaflet tooltips, the zoom and fullscreen buttons (icon visible in both modes, also when toggled), and the
  attribution in both modes. A phone-width timeline: attribution above the bottom sheet, tappable.
- Weather, note, photo, stay-weather-badge, stack, cluster, friend and trip-replay markers in dark mode: same colours as
  in light mode now. Raster single notes are violet.
- Trip plan pins (raster): the inner dot is visible again.
- Raster marker-cluster groups (raw GPS, location analytics): cluster colours unchanged.
- Map context menu (right-click on the timeline map): 12px radius, Aura shadow and border.
- Timeline loading and no-data boxes in the sidebar and over the map: unchanged.
- Dashboard and digest charts: follow a theme switch without a reload (also in system mode). The tooltip is dark in
  light mode and light in dark mode.
- Journey insights and digest movement bars: public transport and train have the same colours in both.

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
- Phase 4: `--gp-primary-text` is reused as a new token: primary as text, icon or accent line, light = primary,
  dark = #60a5fa. The rename map's old `--gp-primary-text` (text on a primary fill) had one use, which became
  `--gp-primary-contrast`. The name now matches the status `--gp-<status>-text` tokens.
- Phase 4: the landing page keeps its own tinted palette as `--gp-landing-*` in `tokens.css`, rather than being
  flattened onto the app surfaces.
- Phase 4: PrimeVue palette colours (`--green-500` and so on) were renamed to `--p-green-500` rather than to status
  tokens. That keeps the exact colours; they were undefined before, so their fallbacks applied.
- Phase 4: dark-only surface swaps were dropped instead of adding tokens. Components use one surface token in both
  modes, and the dark muted surface (#334155) is the nested-block colour everywhere.
- Phase 2, datepicker "today": emerald (`{emerald.500}`, white text) in **both** schemes. Before, it was emerald
  only in dark mode; the navbar's comment gave the reason: keep today distinct from the blue selected date.
  AppNavbarWithDatePicker's own dark today/selected rules are now redundant; they go in Phase 4.
- Phase 2, confirm-dialog icon: Aura default, the modal text colour. This is what already showed: the warning
  colour rule used the PrimeVue 3 class and never matched. A fixed warning colour would also be wrong for
  non-destructive confirms.
- Phase 2, tabs: nothing in the preset. The app doesn't use PrimeVue TabMenu or Tabs; `TabContainer.vue` renders
  its own markup with `p-tabmenu-*` class names and styles it with tokens that already flip.
- Phase 5: no `.gp-data-table` class. Nothing table-specific remained once the dark blocks were checked against the
  preset.
- Phase 5: page headers are shared classes rather than PageContainer props, because most of these pages put the
  header inside their own width-limited wrapper; moving it into PageContainer's header would change the layout.
- Phase 5: DataTable selected rows use a soft tint (preset `datatable.row.selected*`), not the solid app highlight.
  Cells keep their own colours (muted dates, primary durations, outlined buttons), which were unreadable on solid
  blue. Other highlights (selected list option, selected date) stay solid.
- Phase 5: dark-only row hover became PrimeVue's `rowHover` in both modes (only PlaceVisitsTable needed it).
- Phase 6: third-party map CSS goes in a `vendor` cascade layer instead of keeping `!important` to beat lazily
  loaded chunks. MapLibre's 70 KB stylesheet stays in the lazy VectorMapHost chunk, imported through a small CSS file
  with `layer(vendor)`.
- Phase 6: map markers keep one colour set in both modes, because the base tiles never switch theme. Map chrome
  (popups, tooltips, controls) follows the theme through `--gp-map-*` tokens.
- Phase 6: the context menu's look comes from preset tokens. Only the radius is GeoPulse-specific; the old
  `--gp-border-medium` border and custom shadow became Aura's `content.border` and overlay shadow.
- Phase 2, toasts: Aura's severity colours (proper dark variants) instead of the old soft background with a solid
  border in light and card background in dark. The confirm dialog's light restyle (dividers, custom paddings,
  1.125rem title, all non-outlined buttons forced to primary) was dropped, as planned: tokens can't express it.
