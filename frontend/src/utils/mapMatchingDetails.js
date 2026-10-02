import { formatMessageDescriptor } from '@/utils/messageDescriptor'

const HTTP_STATUS = /HTTP (\d{3})/
// Valhalla answers 400 with error_code 443 when it cannot snap the trace to a road or path.
const NO_MATCHING_PATH = /error_code"?\s*:\s*443/

/**
 * Turns a trip's map-matching state into human copy.
 *
 * `state.error` is either a MessageDescriptor from the backend or a plain string, so it goes through
 * formatMessageDescriptor. Reasons produced by GeoPulse itself (skips judged before queueing) are already
 * written for users and are passed through as the title.
 *
 * @returns {{ title: string, detail: string|null }} detail holds the raw server text when the title is a
 * translated message, and is null when the title is the server text itself.
 */
export const describeMapMatchingState = (state) => {
  const status = state?.status || null

  if (status === 'COMPLETED') {
    return {
      title: 'This route was refined with map matching.',
      detail: 'It is drawn from roads and paths instead of the recorded GPS trace.'
    }
  }
  if (status === 'QUEUED') {
    return { title: 'This trip is queued for map matching.', detail: 'The route will be refined shortly.' }
  }
  if (status === 'PROCESSING') {
    return { title: 'This trip is being matched right now.', detail: null }
  }

  const reason = formatMessageDescriptor(state?.error)
  if (!reason) {
    return { title: 'This trip could not be map-matched.', detail: null }
  }

  const httpStatus = Number(reason.match(HTTP_STATUS)?.[1] || 0)

  if (httpStatus === 400 && NO_MATCHING_PATH.test(reason)) {
    return {
      title: 'The routing engine could not find a road or path for this trip.',
      detail: reason
    }
  }
  if (httpStatus === 400) {
    return { title: 'The routing engine rejected this trip\'s GPS trace.', detail: reason }
  }
  if (httpStatus === 404) {
    return { title: 'The routing engine has no map data for this area.', detail: reason }
  }
  if (httpStatus === 408 || httpStatus === 429 || httpStatus >= 500) {
    return {
      title: 'The routing engine was temporarily unavailable. This trip will be retried.',
      detail: reason
    }
  }
  return { title: reason, detail: null }
}
