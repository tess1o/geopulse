import { t } from '@/locales'

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
      label: t('sharing.linkOptions.share.label'),
      toastDetail: t('sharing.linkOptions.share.toastDetail'),
      url: buildShareUrl(link, baseUrl)
    },
    {
      key: 'map',
      label: t('sharing.linkOptions.map.label'),
      toastDetail: t('sharing.linkOptions.map.toastDetail'),
      url: buildShareEmbedUrl(link, baseUrl, 'map')
    }
  ]

  if (link.share_type === 'TIMELINE') {
    options.push({
      key: 'timeline',
      label: t('sharing.linkOptions.timeline.label'),
      toastDetail: t('sharing.linkOptions.timeline.toastDetail'),
      url: buildShareEmbedUrl(link, baseUrl, 'timeline')
    })
  }

  return options
}
