const normalizeVersion = (value) => {
  if (typeof value !== 'string') {
    return null
  }

  const normalized = value.trim().toLowerCase().replace(/^v/, '')
  return normalized || null
}

export const filterTipsByAudience = (tips, isAdmin) => {
  if (!Array.isArray(tips)) {
    return []
  }

  const targetAudience = isAdmin ? 'admin' : 'non_admin'

  return tips.filter((tip) => {
    const audiences = Array.isArray(tip?.audiences) ? tip.audiences.map(a => String(a).toLowerCase()) : ['all']
    return audiences.includes('all') || audiences.includes(targetAudience)
  })
}

export const getRandomTipIndex = (length, randomFn = Math.random) => {
  if (!Number.isFinite(length) || length <= 0) {
    return 0
  }

  const candidate = Number(randomFn())
  if (!Number.isFinite(candidate) || candidate < 0) {
    return 0
  }

  return Math.min(length - 1, Math.floor(candidate * length))
}

export const getNextTipIndex = (length, currentIndex) => {
  if (!Number.isFinite(length) || length <= 0) {
    return 0
  }

  const normalizedCurrent = Number.isFinite(currentIndex) ? currentIndex : 0
  return (normalizedCurrent + 1) % length
}

export const findReleaseForVersion = (whatsNewItems, appVersion) => {
  if (!Array.isArray(whatsNewItems)) {
    return null
  }

  const targetVersion = normalizeVersion(appVersion)
  if (!targetVersion) {
    return null
  }

  return whatsNewItems.find((item) => normalizeVersion(item?.version) === targetVersion) || null
}
