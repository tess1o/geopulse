/**
 * Turns a location source's `ingestStatus` (latest ingest activity reported by the backend) into what the
 * source card shows: a severity, the last receive time, the latest skipped point and any refused requests.
 * Returns i18n keys plus params rather than strings so the card stays reactive to a locale change.
 */

const REJECTION_CODES = new Set([
  'ACCURACY_EXCEEDED',
  'MISSING_ACCURACY',
  'NEGATIVE_ACCURACY',
  'SPEED_EXCEEDED',
  'DUPLICATE',
  'INVALID_PAYLOAD'
])

const AUTH_FAILURE_CODES = new Set(['INVALID_CREDENTIALS'])

/** A source that has not delivered anything for this long is flagged. */
export const STALE_AFTER_MS = 24 * 60 * 60 * 1000

/** The newest point's GPS time is shown only when it lags the receive time by more than this. */
export const DELAYED_POINT_THRESHOLD_MS = 10 * 60 * 1000

const toMillis = (value) => {
  if (!value) return null
  const millis = new Date(value).getTime()
  return Number.isNaN(millis) ? null : millis
}

export function buildLocationSourceStatus(source, { now = Date.now() } = {}) {
  const status = source?.ingestStatus || {}
  const receivedAt = toMillis(status.lastReceivedAt)
  const savedAt = toMillis(status.lastSavedAt)
  const newestPointAt = toMillis(status.lastSavedPointTimestamp)
  const rejectedAt = toMillis(status.lastRejectedAt)
  const authFailedAt = toMillis(status.lastAuthFailureAt)

  // A refusal only matters while nothing has been accepted since.
  const authProblem = authFailedAt !== null && (receivedAt === null || authFailedAt > receivedAt)
    ? {
        at: status.lastAuthFailureAt,
        messageKey: `locationSources.status.authFailure.${
          AUTH_FAILURE_CODES.has(status.lastAuthFailureCode) ? status.lastAuthFailureCode : 'UNKNOWN'
        }`
      }
    : null

  const rejection = rejectedAt !== null
    ? {
        at: status.lastRejectedAt,
        messageKey: `locationSources.status.rejection.${
          REJECTION_CODES.has(status.lastRejectionCode) ? status.lastRejectionCode : 'UNKNOWN'
        }`,
        params: status.lastRejectionParams || {}
      }
    : null

  const delayedNewestPoint = savedAt !== null && newestPointAt !== null
    && savedAt - newestPointAt > DELAYED_POINT_THRESHOLD_MS

  let severity = 'success'
  if (authProblem) {
    severity = 'danger'
  } else if (receivedAt === null) {
    severity = 'secondary'
  } else if (now - receivedAt > STALE_AFTER_MS) {
    severity = 'warn'
  }

  return {
    severity,
    lastReceivedAt: receivedAt !== null ? status.lastReceivedAt : null,
    newestPointAt: delayedNewestPoint ? status.lastSavedPointTimestamp : null,
    rejection,
    authProblem
  }
}
