import page from './page.js'
import access from './access.js'
import connectedApps from './connectedApps.js'
import general from './general.js'
import notifications from './notifications.js'
import security from './security.js'
import timeline from './timeline.js'

/**
 * Personal settings catalogs, grouped by tab.
 *
 * `page` is spread flat rather than nested under a `page` key: it already carries its own `page.*`
 * sub-object for the shell's header copy, so nesting it would produce `profile.page.page.title`.
 * Everything else in it (`searchPlaceholder`, `groups`, `tabs`, ...) belongs at the `profile.*` level.
 *
 * Split per tab so the file a translator opens matches the tab they are working on.
 */
export default {
    ...page,
    access,
    connectedApps,
    general,
    notifications,
    security,
    timeline
}
