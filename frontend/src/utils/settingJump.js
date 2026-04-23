const HIGHLIGHT_CLASS = 'gp-setting-highlight'

const escapeForAttribute = (value) => {
  return String(value).replace(/"/g, '\\"')
}

const escapeForCssSelector = (value) => {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
    return CSS.escape(value)
  }

  return String(value).replace(/[^a-zA-Z0-9_-]/g, (char) => `\\${char}`)
}

const scrollAndHighlight = (element) => {
  if (!element) return

  element.scrollIntoView({ behavior: 'smooth', block: 'center' })
  element.classList.add(HIGHLIGHT_CLASS)

  if (!element.hasAttribute('tabindex')) {
    element.setAttribute('tabindex', '-1')
  }

  element.focus({ preventScroll: true })

  window.setTimeout(() => {
    element.classList.remove(HIGHLIGHT_CLASS)
  }, 2200)
}

const querySettingElement = (settingId) => {
  const safeSettingId = escapeForAttribute(settingId)
  const escapedSettingId = escapeForCssSelector(settingId)
  return document.querySelector(
    `[data-setting-id="${safeSettingId}"], #${escapedSettingId}, #setting-${escapedSettingId}`
  )
}

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

export const jumpToSetting = async (settingId, {
  attempts = 40,
  delayMs = 150,
  onMissing = null
} = {}) => {
  if (!settingId) return false

  for (let i = 0; i < attempts; i += 1) {
    const element = querySettingElement(settingId)
    if (element) {
      scrollAndHighlight(element)
      return true
    }

    await wait(delayMs)
  }

  if (typeof onMissing === 'function') {
    onMissing(settingId)
  }

  return false
}
