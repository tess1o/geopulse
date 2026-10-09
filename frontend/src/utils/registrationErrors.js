import { t } from '@/locales'
import { normalizeApiError } from './apiErrorDetail'
import { formatError } from './errorHandler'

/**
 * User-facing copy for a failed registration, open or by invitation.
 *
 * Classified by the backend's error code first and its status second, and always resolved to translated
 * copy: the backend's `detail` is English diagnostic text and is never shown here. Accepts the raw axios
 * error and the auth store's normalized one alike, since normalizeApiError reads both.
 */

// Field-level copy for the constraints the registration requests declare (UserRegistrationRequest,
// InvitationRegisterRequest), keyed by request field and violation code.
const VIOLATION_MESSAGES = {
  email: {
    NOT_BLANK: () => t('auth.validation.emailRequired'),
    EMAIL: () => t('auth.validation.emailInvalid'),
    SIZE: ({ max }) => t('auth.register.errors.emailTooLong', { max })
  },
  fullName: {
    NOT_BLANK: () => t('auth.register.validation.fullNameRequired'),
    SIZE: ({ min, max }) => t('auth.register.errors.fullNameLength', { min, max })
  },
  password: {
    NOT_BLANK: () => t('auth.validation.passwordRequired'),
    SIZE: ({ min, max }) => t('auth.register.errors.passwordLength', { min, max })
  }
}

const INVITATION_STATUS_KEYS = {
  USED: 'auth.invitation.status.used',
  EXPIRED: 'auth.invitation.status.expired',
  REVOKED: 'auth.invitation.status.revoked'
}

/** Why an invitation cannot be used, from the backend's invitation status. */
export const invitationStatusMessage = (status) => {
  const key = INVITATION_STATUS_KEYS[status]
  return key ? t(key) : t('auth.invitation.invalidMessage')
}

const violationMessage = (violation) => {
  // The field may arrive as a property path (`registerUser.request.email`); the last segment names it.
  const field = String(violation.field || '').split('.').pop()
  const message = VIOLATION_MESSAGES[field]?.[violation.code]
  return message ? message(violation.parameters || {}) : null
}

export const getRegistrationErrorMessage = (error) => {
  const problem = normalizeApiError(error)

  switch (problem.code) {
    case 'USER_REGISTRATION_CONFLICT':
      return t('auth.register.errors.emailExists')
    case 'PASSWORD_REGISTRATION_DISABLED':
      return t('auth.register.disabledMessageShort')
    case 'INVITATION_NOT_FOUND':
      return t('auth.invitation.errors.notFound')
    case 'INVALID_INVITATION':
      return invitationStatusMessage(problem.parameters?.status)
    case 'VALIDATION_FAILED': {
      const messages = [...new Set(problem.violations.map(violationMessage).filter(Boolean))]
      return messages.length > 0 ? messages.join('; ') : t('auth.register.errors.checkInformation')
    }
  }

  const status = problem.status
  if (!status) {
    // No response at all: formatError already has translated copy for network failures and timeouts.
    return formatError(error).message
  }
  if (status === 400) {
    return t('auth.register.errors.checkInformation')
  }
  if (status === 429 || (status >= 502 && status <= 504)) {
    return formatError(error).message
  }
  if (status >= 500) {
    return t('auth.register.errors.serverError')
  }
  return t('auth.register.errors.failed')
}
