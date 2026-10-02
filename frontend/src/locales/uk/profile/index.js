import page from './page.js'
import access from './access.js'
import appearance from './appearance.js'
import connectedApps from './connectedApps.js'
import general from './general.js'
import notifications from './notifications.js'
import security from './security.js'
import timeline from './timeline.js'

/**
 * Personal settings catalogs, grouped by tab (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * `page` is spread flat -- it already carries its own `page.*` sub-object, so nesting it would
 * produce `profile.page.page.title`.
 */
export default {
    ...page,
    access,
    appearance,
    connectedApps,
    general,
    notifications,
    security,
    timeline
}
