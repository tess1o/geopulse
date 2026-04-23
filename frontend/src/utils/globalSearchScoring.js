const normalize = (value) => {
  return (value || '')
    .toString()
    .toLowerCase()
    .trim()
}

const tokenize = (value) => normalize(value).split(/\s+/).filter(Boolean)
const MIN_SUBSEQUENCE_QUERY_LENGTH = 5

const isSubsequenceMatch = (needle, haystack) => {
  if (!needle || !haystack) return false
  let needleIndex = 0
  for (let i = 0; i < haystack.length && needleIndex < needle.length; i += 1) {
    if (haystack[i] === needle[needleIndex]) {
      needleIndex += 1
    }
  }
  return needleIndex === needle.length
}

const scoreField = (query, value) => {
  const target = normalize(value)
  if (!target) return 0

  if (target === query) return 1000
  if (target.startsWith(query)) return 700
  if (target.includes(query)) return 500
  if (query.length >= MIN_SUBSEQUENCE_QUERY_LENGTH) {
    const targetTokens = tokenize(target)
    if (targetTokens.some((token) => isSubsequenceMatch(query, token))) return 150
  }
  return 0
}

const scoreKeywords = (query, keywords) => {
  if (!Array.isArray(keywords) || keywords.length === 0) return 0

  let best = 0
  for (const keyword of keywords) {
    best = Math.max(best, scoreField(query, keyword) - 150)
  }

  return Math.max(best, 0)
}

const scoreTokens = (queryTokens, itemText) => {
  if (queryTokens.length <= 1) return 0
  const allMatched = queryTokens.every((token) => itemText.includes(token))
  if (!allMatched) return 0

  return 90
}

const getPrimaryTitle = (item) => item?.title || item?.displayName || ''
const getSecondaryText = (item) => item?.subtitle || item?.metaLine || ''

export const scoreSearchItem = (query, item) => {
  const normalizedQuery = normalize(query)
  if (!normalizedQuery) return 0

  const title = normalize(getPrimaryTitle(item))
  const subtitle = normalize(getSecondaryText(item))
  const itemText = `${title} ${subtitle} ${(item.keywords || []).join(' ')}`.trim()

  let score = 0
  score = Math.max(score, scoreField(normalizedQuery, title))
  score = Math.max(score, scoreField(normalizedQuery, subtitle) - 200)
  score = Math.max(score, scoreKeywords(normalizedQuery, item.keywords))
  score += scoreTokens(tokenize(normalizedQuery), itemText)

  if (item.kind === 'setting') score += 25

  return score
}

export const searchAndRankItems = (query, items, { minScore = 120 } = {}) => {
  const scored = items
    .map((item) => ({ item, score: scoreSearchItem(query, item) }))
    .filter((entry) => entry.score >= minScore)

  scored.sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score
    return getPrimaryTitle(a.item).localeCompare(getPrimaryTitle(b.item))
  })

  return scored
}
