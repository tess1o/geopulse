/**
 * Document title resolution.
 *
 * Lives outside the router so both `router.afterEach` and a locale change can refresh the live title
 * from one place -- otherwise switching language would leave the stale tab title until the next
 * navigation.
 *
 * `titleKey` is preferred and resolves through the catalogs, sharing the `nav.*` namespace with the
 * navigation labels so a nav entry and its page title can never drift apart. Plain `title` is still
 * honoured so routes can migrate to keys incrementally.
 */
import { t } from '@/locales'

const BASE_TITLE = 'GeoPulse'

export const resolveDocumentTitle = (meta) => {
    const titleKey = meta?.titleKey
    const pageTitle = titleKey ? t(titleKey) : meta?.title
    return pageTitle ? `${pageTitle} - ${BASE_TITLE}` : BASE_TITLE
}

export const applyDocumentTitle = (meta) => {
    document.title = resolveDocumentTitle(meta)
}
