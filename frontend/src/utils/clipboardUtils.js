/**
 * Copy text to clipboard with fallback for mobile Safari
 * @param {string} text - The text to copy
 * @returns {Promise<boolean>} - Whether the copy was successful
 */
export async function copyToClipboard(text) {
  let success = false

  // Try modern Clipboard API first
  if (navigator.clipboard && navigator.clipboard.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      success = true
    } catch (clipboardError) {
      console.warn('Clipboard API failed, trying fallback:', clipboardError)
      // Continue to fallback
    }
  }

  // Fallback for mobile Safari and older browsers
  if (!success) {
    try {
      const textArea = document.createElement('textarea')
      textArea.value = text
      textArea.style.position = 'fixed'
      textArea.style.left = '-999999px'
      textArea.style.top = '-999999px'
      textArea.setAttribute('readonly', '')
      document.body.appendChild(textArea)

      // For iOS Safari
      textArea.contentEditable = 'true'
      textArea.readOnly = false

      const range = document.createRange()
      range.selectNodeContents(textArea)

      const selection = window.getSelection()
      selection.removeAllRanges()
      selection.addRange(range)

      textArea.setSelectionRange(0, text.length)

      success = document.execCommand('copy')
      document.body.removeChild(textArea)
    } catch (fallbackError) {
      console.error('Fallback copy failed:', fallbackError)
      success = false
    }
  }

  return success
}
