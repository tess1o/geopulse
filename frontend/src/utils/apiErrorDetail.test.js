import { describe, expect, it } from 'vitest'
import { extractApiErrorDetail, formatViolationField } from './apiErrorDetail'

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

    expect(extractApiErrorDetail(error, 'Fallback')).toBe(
      'Name: Provider name must use lowercase letters, numbers, and hyphens'
    )
  })

  it('prefers explicit API messages over generic errors', () => {
    const error = {
      message: 'Request failed with status code 400',
      response: {
        data: {
          message: "Cannot delete custom provider 'local-photon' while it is the primary provider"
        }
      }
    }

    expect(extractApiErrorDetail(error, 'Fallback')).toBe(
      "Cannot delete custom provider 'local-photon' while it is the primary provider"
    )
  })

  it('extracts useful messages from Quarkus details payloads', () => {
    const error = {
      response: {
        data: {
          details: "Error id 123, java.lang.IllegalArgumentException: Cannot delete custom provider 'test' while it is the primary provider"
        }
      }
    }

    expect(extractApiErrorDetail(error, 'Fallback')).toBe(
      "Cannot delete custom provider 'test' while it is the primary provider"
    )
  })

  it('falls back to caller-provided text when no response detail exists', () => {
    expect(extractApiErrorDetail({}, 'Failed to save custom provider')).toBe('Failed to save custom provider')
  })
})
