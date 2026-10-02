import { describe, expect, it } from 'vitest'
import {
  formatApiErrorDetail,
  formatViolationField,
  hasErrorReference,
  normalizeApiError,
  productionErrorContext,
  withErrorReference
} from './apiErrorDetail'

describe('apiErrorDetail', () => {
  it('formats nested violation field names for display', () => {
    expect(formatViolationField('create.request.displayName')).toBe('Display Name')
    expect(formatViolationField('update.request.delayMs')).toBe('Delay milliseconds')
  })

  it('extracts bean validation violations from API responses', () => {
    const error = {
      response: {
        data: {
          violations: [
            {
              field: 'create.request.name',
              message: 'Provider name must use lowercase letters, numbers, and hyphens'
            }
          ]
        }
      }
    }

    expect(formatApiErrorDetail(error, 'Fallback')).toBe(
      'Name: Provider name must use lowercase letters, numbers, and hyphens'
    )
  })

  it('resolves a localizable violation descriptor through its fallback', () => {
    const error = {
      response: {
        data: {
          violations: [
            {
              field: 'size.request.password',
              code: 'SIZE',
              parameters: { min: 3, max: 128 },
              detail: {
                key: 'validation.size',
                parameters: { min: 3, max: 128 },
                fallback: 'Password must be between 3 and 128 characters'
              }
            }
          ]
        }
      }
    }

    expect(formatApiErrorDetail(error, 'Fallback')).toBe(
      'Password: Password must be between 3 and 128 characters'
    )
  })

  it('never renders a violation descriptor as an object', () => {
    const error = {
      response: {
        data: {
          violations: [{ field: 'email', detail: { key: 'validation.notBlank' } }]
        }
      }
    }

    expect(formatApiErrorDetail(error, 'Fallback')).toBe('Email: validation.notBlank')
  })

  it('prefers an explicit problem detail over the generic axios error', () => {
    const error = {
      message: 'Request failed with status code 400',
      response: {
        data: {
          detail: "Cannot delete custom provider 'local-photon' while it is the primary provider"
        }
      }
    }

    expect(formatApiErrorDetail(error, 'Fallback')).toBe(
      "Cannot delete custom provider 'local-photon' while it is the primary provider"
    )
  })

  it('falls back to the problem title when the detail is absent', () => {
    const error = {
      response: {
        data: {
          title: 'Conflict'
        }
      }
    }

    expect(formatApiErrorDetail(error, 'Fallback')).toBe('Conflict')
  })

  it('falls back to caller-provided text when no response detail exists', () => {
    expect(formatApiErrorDetail({}, 'Failed to save custom provider')).toBe('Failed to save custom provider')
  })

  it('keeps production logging context free of headers and response bodies', () => {
    const error = {
      name: 'AxiosError',
      config: { headers: { Authorization: 'Bearer secret' } },
      response: {
        status: 401,
        headers: { 'x-request-id': 'req-1' },
        data: { code: 'AUTHENTICATION_REQUIRED', errorId: 'err-1', secret: 'do-not-log' }
      }
    }

    expect(productionErrorContext(error)).toEqual({
      name: 'AxiosError',
      status: 401,
      code: 'AUTHENTICATION_REQUIRED',
      requestId: 'req-1',
      errorId: 'err-1'
    })
  })

  // An INTERNAL_ERROR carries no detail, so the problem title -- an HTTP reason phrase -- is all
  // there is to show. The reference is what makes the failure reportable.
  it('appends the support reference to a server-side failure', () => {
    const error = {
      message: 'Request failed with status code 500',
      response: {
        status: 500,
        data: {
          type: 'urn:geopulse:error:INTERNAL_ERROR',
          title: 'Internal Server Error',
          status: 500,
          code: 'INTERNAL_ERROR',
          errorId: 'e85795d5-f7bb-48c9-adc2-cafe8d249f92'
        }
      }
    }

    expect(formatApiErrorDetail(error, 'Failed to load timeline')).toBe(
      'Internal Server Error\nCheck backend logs for ID: e85795d5-f7bb-48c9-adc2-cafe8d249f92'
    )
  })

  it('leaves client-side failures free of a reference', () => {
    const error = {
      message: 'Request failed with status code 401',
      response: {
        status: 401,
        data: {
          type: 'urn:geopulse:error:INVALID_CREDENTIALS',
          title: 'Unauthorized',
          code: 'INVALID_CREDENTIALS',
          errorId: 'err-1',
          detail: 'Invalid email or password'
        }
      }
    }

    expect(formatApiErrorDetail(error, 'Fallback')).toBe('Invalid email or password')
  })

  // A proxy 500 has no problem document, so there is no reference to quote. The detail falls back
  // through title to the axios message, which is all an opaque body leaves.
  it('appends nothing when a server-side failure carries no reference', () => {
    const error = {
      message: 'Request failed with status code 500',
      response: { status: 500, data: '<html><body>500 Internal Server Error</body></html>' }
    }

    expect(hasErrorReference(error)).toBe(false)
    expect(formatApiErrorDetail(error, 'Failed to load timeline')).toBe(
      'Request failed with status code 500'
    )
  })

  it('reads the reference from the response header when the body omits it', () => {
    const error = {
      response: {
        status: 503,
        headers: { 'x-error-id': 'header-err-1' },
        data: { title: 'Service Unavailable' }
      }
    }

    expect(hasErrorReference(error)).toBe(true)
    expect(withErrorReference('Service Unavailable', error)).toBe(
      'Service Unavailable\nCheck backend logs for ID: header-err-1'
    )
  })

  // The timeline store rethrows the normalized error (stores/timeline.js), which has no .response
  // at all -- so the id has to survive normalization for the reference to reach the toast.
  it('keeps the reference on an error that was already normalized', () => {
    const normalized = normalizeApiError({
      message: 'Request failed with status code 500',
      response: {
        status: 500,
        data: {
          type: 'urn:geopulse:error:INTERNAL_ERROR',
          title: 'Internal Server Error',
          status: 500,
          code: 'INTERNAL_ERROR',
          errorId: 'e85795d5-f7bb-48c9-adc2-cafe8d249f92'
        }
      }
    })

    expect(hasErrorReference(normalized)).toBe(true)
    expect(formatApiErrorDetail(normalized, 'Failed to load timeline')).toBe(
      'Internal Server Error\nCheck backend logs for ID: e85795d5-f7bb-48c9-adc2-cafe8d249f92'
    )
  })
})
