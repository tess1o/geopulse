import { describe, expect, it } from 'vitest'
import { formatApiErrorDetail, formatViolationField } from './apiErrorDetail'

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
})
