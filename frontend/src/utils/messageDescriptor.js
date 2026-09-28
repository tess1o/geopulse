import { t, te } from '@/locales'

/**
 * Resolve a backend `MessageDescriptor` ({ key, parameters, fallback }) to display text.
 *
 * `key` is translated when the active locale's catalog has it; `parameters` interpolate into it --
 * named to match the Java record's `parameters()` component, which Jackson serializes verbatim (see
 * `shared/api/MessageDescriptor.java`; `ApiViolation`'s own `parameters` field follows the same
 * convention, read the same way in `apiErrorDetail.js`). A missing/untranslated key falls back to
 * `fallback` (the backend's English text), so a language that has not caught up with a new message
 * yet degrades to English instead of a raw dotted key or blank text. A plain string is passed through
 * unchanged for callers that still send one.
 */
export const formatMessageDescriptor = (message) => {
  if (typeof message === 'string') return message
  if (message?.key && te(message.key)) {
    return t(message.key, message.parameters || {})
  }
  return message?.fallback || message?.key || ''
}
