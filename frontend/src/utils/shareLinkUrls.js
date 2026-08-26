export const sanitizeShareBaseUrl = (baseUrl) => {
  const fallbackBaseUrl = typeof window !== 'undefined' ? window.location.origin : ''
  const effectiveBaseUrl = baseUrl || fallbackBaseUrl
  return effectiveBaseUrl.endsWith('/') ? effectiveBaseUrl.slice(0, -1) : effectiveBaseUrl
}

export const getSharePath = (link) => {
  return link?.share_type === 'TIMELINE' ? 'shared-timeline' : 'shared'
}

export const buildShareUrl = (link, baseUrl) => {
  const sanitizedBaseUrl = sanitizeShareBaseUrl(baseUrl)
  return `${sanitizedBaseUrl}/${getSharePath(link)}/${link.id}`
}

export const buildShareEmbedUrl = (link, baseUrl, embedMode) => {
  return `${buildShareUrl(link, baseUrl)}?embed=${encodeURIComponent(embedMode)}`
}

export const buildShareLinkOptions = (link, baseUrl) => {
  if (!link?.id) return []

  const options = [
    {
      key: 'share',
      label: 'Share Link',
      toastDetail: 'Share link copied to clipboard',
      url: buildShareUrl(link, baseUrl)
    },
    {
      key: 'map',
      label: 'Map Embed Link',
      toastDetail: 'Map embed link copied to clipboard',
      url: buildShareEmbedUrl(link, baseUrl, 'map')
    }
  ]

  if (link.share_type === 'TIMELINE') {
    options.push({
      key: 'timeline',
      label: 'Map + Timeline Embed Link',
      toastDetail: 'Map + timeline embed link copied to clipboard',
      url: buildShareEmbedUrl(link, baseUrl, 'timeline')
    })
  }

  return options
}
