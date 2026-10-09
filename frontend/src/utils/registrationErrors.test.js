import { afterEach, describe, expect, it } from 'vitest'
import { normalizeApiError } from './apiErrorDetail'
import { getRegistrationErrorMessage, invitationStatusMessage } from './registrationErrors'

const problem = (status, code, extra = {}) => ({
  type: `urn:geopulse:error:${code}`,
  title: 'Bad Request',
  status,
  code,
  errorId: 'err-1',
  requestId: 'req-1',
  ...extra
})

const axiosError = (status, data) => ({
  message: `Request failed with status code ${status}`,
  response: { status, data },
  config: { url: '/registrations' }
})

const sizeViolation = (field, min, max, fallback) => ({
  field,
  in: 'body',
  code: 'SIZE',
  parameters: { min, max },
  detail: { key: 'validation.size', parameters: { min, max }, fallback }
})

/**
 * Every registration failure resolves to translated copy, never to the backend's English detail or to
 * axios's "Request failed with status code ..." text.
 */
describe('getRegistrationErrorMessage', () => {
  it('reports a taken email', () => {
    const error = axiosError(409, problem(409, 'USER_REGISTRATION_CONFLICT', { detail: 'An account with this email already exists' }))

    expect(getRegistrationErrorMessage(error)).toBe('An account with this email already exists')
  })

  // The regression: a disabled registration used to come back as USER_REGISTRATION_CONFLICT and read as
  // "email already exists".
  it('reports disabled password registration as disabled', () => {
    const error = axiosError(403, problem(403, 'PASSWORD_REGISTRATION_DISABLED', { detail: 'Password registration is disabled' }))

    expect(getRegistrationErrorMessage(error)).toBe('Sign up using email/password is currently disabled.')
  })

  it('translates field violations with their limits', () => {
    const error = axiosError(400, problem(400, 'VALIDATION_FAILED', {
      violations: [
        sizeViolation('fullName', 1, 100, 'Full name must be between 1 and 100 characters'),
        sizeViolation('password', 3, 128, 'Password must be between 3 and 128 characters')
      ]
    }))

    expect(getRegistrationErrorMessage(error)).toBe(
      'Full name must be between 1 and 100 characters; Password must be between 3 and 128 characters'
    )
  })

  it('falls back to a generic prompt for a violation it has no copy for', () => {
    const error = axiosError(400, problem(400, 'VALIDATION_FAILED', {
      violations: [{ field: 'timezone', code: 'SIZE', parameters: { min: 0, max: 255 }, detail: { fallback: 'Timezone cannot exceed 255 characters' } }]
    }))

    expect(getRegistrationErrorMessage(error)).toBe('Please check your information and try again')
  })

  it.each([
    ['USED', 'This invitation has already been used'],
    ['EXPIRED', 'This invitation has expired'],
    ['REVOKED', 'This invitation has been revoked']
  ])('explains an unusable invitation (%s)', (status, message) => {
    const error = axiosError(400, problem(400, 'INVALID_INVITATION', { parameters: { status } }))

    expect(getRegistrationErrorMessage(error)).toBe(message)
  })

  it('reports an unknown invitation link', () => {
    expect(getRegistrationErrorMessage(axiosError(404, problem(404, 'INVITATION_NOT_FOUND'))))
      .toBe('This invitation link is not valid. Please ask your administrator for a new one.')
  })

  it('reads the auth store\'s normalized error the same as the raw one', () => {
    const raw = axiosError(409, problem(409, 'USER_REGISTRATION_CONFLICT'))

    expect(getRegistrationErrorMessage(normalizeApiError(raw))).toBe('An account with this email already exists')
  })

  it('never shows axios\'s own text for a status it does not know', () => {
    const message = getRegistrationErrorMessage(axiosError(403, problem(403, 'ACCESS_DENIED', { detail: 'Access denied' })))

    expect(message).toBe('Registration failed. Please try again.')
  })

  it('uses translated copy for server and gateway failures', () => {
    expect(getRegistrationErrorMessage(axiosError(500, problem(500, 'INTERNAL_ERROR'))))
      .toBe('Server error. Please try again later')
    expect(getRegistrationErrorMessage(axiosError(503, '<html>Service Unavailable</html>')))
      .toBe('GeoPulse is temporarily unavailable. Please try again in a few minutes.')
  })

  it('uses the connection copy when there was no response', () => {
    expect(getRegistrationErrorMessage({ message: 'Network Error' }))
      .toBe('Unable to connect to GeoPulse servers. Please check your internet connection and try again.')
  })
})

describe('getRegistrationErrorMessage in Ukrainian', () => {
  afterEach(async () => {
    const { setLocale } = await import('@/composables/useLocale')
    await setLocale('en', { persist: false })
  })

  it('translates the conflict and the field limits', async () => {
    const { setLocale } = await import('@/composables/useLocale')
    await setLocale('uk', { persist: false })

    expect(getRegistrationErrorMessage(axiosError(409, problem(409, 'USER_REGISTRATION_CONFLICT'))))
      .toBe('Обліковий запис із цією електронною поштою вже існує')
    expect(getRegistrationErrorMessage(axiosError(400, problem(400, 'VALIDATION_FAILED', {
      violations: [sizeViolation('password', 3, 128, 'Password must be between 3 and 128 characters')]
    })))).toBe('Пароль має містити від 3 до 128 символів')
  })
})

describe('invitationStatusMessage', () => {
  it('falls back to the generic invalid copy for a status it does not know', () => {
    expect(invitationStatusMessage('SOMETHING_NEW')).toBe('This invitation is not valid')
  })
})
