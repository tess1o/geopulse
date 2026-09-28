import { buildGoogleMapsUrl } from '@/utils/googleMaps'
import { formatDuration } from '@/utils/durationFormatter'
import { t } from '@/locales'

const getFriendName = (friend) => (
  friend?.name || friend?.fullName || friend?.username || friend?.email || t('maps.popups.friend.defaultName')
)

const getFriendUsername = (friend) => {
  const username = String(friend?.username || '').trim()
  if (!username || username === friend?.name) {
    return ''
  }

  return username.startsWith('@') ? username : `@${username}`
}

const getFriendAvatarUrl = (friend) => friend?.avatar || friend?.avatarUrl || ''

const formatLastSeen = (friend, timezone) => {
  const lastSeenDate = friend?.lastSeen || friend?.timestamp
  if (!lastSeenDate || !timezone?.timeAgo) {
    return ''
  }

  return timezone.timeAgo(lastSeenDate)
}

const formatActivity = (friend) => {
  const activityType = friend?.latestActivityType
  const duration = friend?.latestActivityDurationSeconds
  if (!activityType) {
    return ''
  }

  if (activityType === 'STAY') {
    return t('maps.popups.friend.atCurrentPositionFor', { duration: formatDuration(duration) })
  }

  if (activityType === 'TRIP') {
    return t('maps.popups.friend.movingFor', { duration: formatDuration(duration) })
  }

  return ''
}

const formatBattery = (friend) => {
  if (friend?.lastBattery === null || friend?.lastBattery === undefined || friend?.lastBattery === '') {
    return ''
  }

  const batteryValue = Number(friend?.lastBattery)
  return Number.isFinite(batteryValue) ? `${Math.round(batteryValue)}%` : ''
}

export const buildFriendLocationPopupModel = (friend, { timezone } = {}) => {
  const lastSeen = formatLastSeen(friend, timezone)
  const activity = formatActivity(friend)
  const battery = formatBattery(friend)
  const rows = [
    friend?.status
      ? {
          label: t('maps.popups.common.status'),
          value: friend.status
        }
      : null,
    lastSeen
      ? {
          label: t('maps.popups.friend.lastSeen'),
          value: lastSeen
        }
      : null,
    friend?.address || friend?.location
      ? {
          label: t('maps.popups.friend.location'),
          value: friend.address || friend.location
        }
      : null,
    activity
      ? {
          label: t('maps.popups.friend.activity'),
          value: activity
        }
      : null,
    battery
      ? {
          label: t('maps.popups.common.battery'),
          value: battery
        }
      : null
  ].filter(Boolean)

  const googleMapsUrl = buildGoogleMapsUrl(
    friend?.latitude ?? friend?.lastLatitude,
    friend?.longitude ?? friend?.lastLongitude
  )

  return {
    title: getFriendName(friend),
    subtitle: getFriendUsername(friend),
    avatarUrl: getFriendAvatarUrl(friend),
    avatarAlt: t('maps.popups.friend.avatarAlt', { name: getFriendName(friend) }),
    iconClass: 'pi pi-user',
    rows,
    actions: googleMapsUrl
      ? [
          {
            key: 'open-google-maps',
            label: t('maps.popups.friend.openInGoogleMaps'),
            iconClass: 'pi pi-external-link',
            href: googleMapsUrl,
            target: '_blank',
            rel: 'noopener noreferrer'
          }
        ]
      : [],
    variant: 'compact'
  }
}
