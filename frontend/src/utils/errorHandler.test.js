import { describe, expect, it } from 'vitest'
import { errorToastOptions, isBackendDown, showErrorToast } from './errorHandler'

// A problem document as the backend actually emits it: type/code/errorId are stamped centrally by
// GeoPulseProblemPostProcessor for every 4xx and 5xx alike.
const geopulseProblem = (status, code, extra = {}) => ({
  type: `urn:geopulse:error:${code}`,
  title: 'Internal Server Error',
  status,
  code,
  errorId: 'err-1',
  requestId: 'req-1',
  ...extra
})

const axiosError = (status, data, url, message = `Request failed with status code ${status}`) => ({
  message,
  response: { status, statusText: 'Internal Server Error', data },
  config: { url }
})

describe('isBackendDown', () => {
  // The regression: a 500 the backend itself produced is a failure of a live backend, so it must
  // stay on the login form instead of navigating to the error page.
  it('does not treat a problem the backend itself produced as an outage', () => {
    const error = axiosError(500, geopulseProblem(500, 'INTERNAL_ERROR'), '/auth/sessions')

    expect(isBackendDown(error)).toBe(false)
  })

  it('ignores the word upstream inside a problem the backend produced', () => {
    const error = axiosError(
      500,
      geopulseProblem(500, 'INTERNAL_ERROR', { detail: 'Upstream notification provider rejected the request' }),
      '/api/v1/notifications/providers'
    )

    expect(isBackendDown(error)).toBe(false)
  })

  it('does not treat a structured credential rejection as an outage', () => {
    const error = axiosError(
      401,
      geopulseProblem(401, 'INVALID_CREDENTIALS', { detail: 'Invalid email or password' }),
      '/auth/sessions'
    )

    expect(isBackendDown(error)).toBe(false)
  })

  // The probe heuristic exists for the Vite dev proxy, which answers a dead backend with a 500 and
  // no usable body. That signal is the request URL and nothing else, so it must survive.
  it('keeps the probe for a 500 with a proxy HTML body', () => {
    const nginxPage =
      '<html><head><title>502 Bad Gateway</title></head><body><center><h1>502 Bad Gateway</h1></center>' +
      '<hr><center>nginx</center></body></html>'
    const error = axiosError(500, nginxPage, '/auth/sessions')

    expect(isBackendDown(error)).toBe(true)
  })

  it('keeps the probe for the empty body the dev proxy answers with', () => {
    const error = axiosError(500, '', '/auth/sessions/current')

    expect(isBackendDown(error)).toBe(true)
  })

  it('still classifies a 500 whose body reports a transport failure', () => {
    const error = axiosError(500, 'connect ECONNREFUSED 127.0.0.1:8080', '/api/v1/trips')

    expect(isBackendDown(error)).toBe(true)
  })

  // Deliberately not narrowed by the problem-document check: this clause is what keeps the error
  // page's own health poll failing, so a structured 503 stays an outage.
  it('treats 502, 503 and 504 as an outage whatever body they carry', () => {
    for (const status of [502, 503, 504]) {
      const error = axiosError(status, geopulseProblem(status, 'SERVICE_UNAVAILABLE'), '/api/v1/trips')

      expect(isBackendDown(error)).toBe(true)
    }
  })

  it('treats a transport failure as an outage', () => {
    expect(isBackendDown({ message: 'Network Error', config: { url: '/api/v1/trips' } })).toBe(true)
    expect(isBackendDown({ message: 'Failed to fetch', config: { url: '/api/v1/trips' } })).toBe(true)
  })

  it('does not treat Immich or image loading failures as an outage', () => {
    const imageError = axiosError(500, geopulseProblem(500, 'INTERNAL_ERROR'), '/api/v1/immich/photos')

    expect(isBackendDown(imageError)).toBe(false)
    expect(isBackendDown({ isImageLoadingError: true, message: 'Network Error' })).toBe(false)
  })
})

describe('showErrorToast', () => {
  const captureToasts = () => {
    const toasts = []
    return { toasts, add: (config) => toasts.push(config) }
  }

  it('routes a server-side failure to the reference group and keeps it up longer', () => {
    const { toasts, add } = captureToasts()

    showErrorToast(add, axiosError(500, geopulseProblem(500, 'INTERNAL_ERROR'), '/api/v1/timeline'))

    expect(toasts).toHaveLength(1)
    expect(toasts[0].summary).toBe('Server Error')
    expect(toasts[0].detail).toBe('Internal Server Error\nCheck backend logs for ID: err-1')
    expect(toasts[0].group).toBe('gp-error')
    expect(toasts[0].data).toEqual({ errorId: 'err-1' })
    // Longer than a normal toast so the id can be read and copied, but it still closes itself.
    expect(toasts[0].life).toBe(8000)
  })

  it('keeps the usual lifetime and group when there is no reference to quote', () => {
    const { toasts, add } = captureToasts()

    showErrorToast(add, axiosError(401, geopulseProblem(401, 'INVALID_CREDENTIALS'), '/api/v1/trips'))

    expect(toasts[0].detail).not.toContain('Check backend logs')
    expect(toasts[0].life).toBe(4000)
    expect(toasts[0].group).toBeUndefined()
    expect(toasts[0].data).toBeUndefined()
  })
})

describe('errorToastOptions', () => {
  it('routes a hand-built server-error toast to the reference group', () => {
    expect(errorToastOptions(axiosError(500, geopulseProblem(500, 'INTERNAL_ERROR'), '/api/v1/timeline'), 3000))
      .toEqual({ life: 8000, group: 'gp-error', data: { errorId: 'err-1' } })
  })

  it('leaves an error without a reference on its own lifetime', () => {
    expect(errorToastOptions(axiosError(400, { detail: 'Bad request' }, '/api/v1/timeline'), 3000))
      .toEqual({ life: 3000 })
  })
})
