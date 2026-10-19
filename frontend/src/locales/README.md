# UI translations

Every language is listed once, in [`registry.js`](registry.js). The language selector, lazy loading,
PrimeVue and dayjs locale packs, `Intl` formatting and plural rules are all built from that list.

A language has one of two statuses:

| status  | `npm run dev` | production build | saved preference / browser language |
|---------|---------------|------------------|-------------------------------------|
| `draft` | shown as "Name · draft" | hidden | falls back to English |
| `ready` | shown | shown | used |

A `ready` language must be at least **95%** translated (`MIN_READY_COVERAGE` in `registry.js`).
`npm run test:run` fails if a ready language drops below that. Any string that is not translated
falls back to English.

## Adding a language

All commands below run from `frontend/`. From the repository root you can use the equivalent
`make` targets:

| npm (in `frontend/`) | make (repo root) |
|---|---|
| `npm run i18n:add -- de --name Deutsch --intl de-DE` | `make i18n-add LOCALE=de NAME=Deutsch INTL=de-DE` |
| `npm run i18n:status` | `make i18n-status` (optional `LOCALE=de`) |
| `npm run i18n:status -- de --list` | `make i18n-missing LOCALE=de` |

1. **Scaffold it** (the example uses German):

   ```bash
   npm run i18n:add -- de --name Deutsch --intl de-DE
   ```

   - `<code>` is the ISO 639 code. The browser, the profile setting and `<html lang>` all use it.
   - `--name` is the language's name **in that language** (an endonym). It is never translated.
   - `--intl` is the BCP 47 tag used to format numbers and dates.

   This command:
   - copies `en/` to `de/`, so every key is present;
   - adds a `draft` entry to `registry.js`;
   - adds `DE("de")` to the backend `SupportedLanguages` enum, so users can save the language in
     their profile.

   If `primelocale` or `dayjs` has no pack for the code, the command warns you and those widgets
   stay in English.

2. **Translate** the values in `src/locales/de/**`. Rules:
   - Keep every key and the file structure exactly as in English.
   - Keep placeholders as they are: `{count}`, `{name}`, `@:other.key`.
   - Write a literal brace as `{'{z}'}`.
   - Leave product and format names (GPX, Immich, OwnTracks) as they are. They count as "same as
     English", but the 95% threshold leaves room for them.

   To see what is left to translate:

   ```bash
   npm run i18n:status -- de --list
   ```

3. **Plurals.** Plural messages use `|`-separated forms (`'{count} visit | {count} visits'`).
   Languages with only "one/other" forms (German, French, Spanish…) need nothing extra. Languages
   with more forms (Polish, Czech, Ukrainian…) need a `pluralRule` on their registry entry. The
   Ukrainian `ukPluralRule` in `registry.js` is an example. Also add a test like the Ukrainian one in
   `locales.test.js`.

4. **Preview** with `npm run dev`. Draft languages appear in the selector there only.

5. **Optional backend content.** Add `backend/src/main/resources/home-content_de.json` to translate
   the home-page tips. Without it, English tips are shown.

6. **Ship it.** When `npm run i18n:status` shows 95% or more, set `status: 'ready'` in
   `registry.js`, then run:

   ```bash
   npm run test:run
   ```

## Keeping languages up to date

When you add a key to an English catalog, the other languages fall back to English for it until it
is translated. Run `npm run i18n:status` to see each language's coverage. The command exits
non-zero when a `ready` language is below the threshold or has keys English no longer has, so CI
can use it.

## Removing a language

Delete its directory and its `registry.js` entry, and remove its backend enum constant. Users who
had chosen it are switched to English automatically.
