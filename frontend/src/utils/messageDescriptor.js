export const formatMessageDescriptor = (message) => {
  if (typeof message === 'string') return message
  return message?.fallback || message?.key || ''
}
