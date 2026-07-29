import { buildGoogleMapsUrl } from '@/utils/googleMaps'
import { formatDuration } from '@/utils/durationFormatter'

const getFriendName = (friend) => (
  friend?.name || friend?.fullName || friend?.username || friend?.email || 'Friend'
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
    return `At current position for ${formatDuration(duration)}`
  }

  if (activityType === 'TRIP') {
    return `Moving for ${formatDuration(duration)}`
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
          label: 'Status',
          value: friend.status
        }
      : null,
    lastSeen
      ? {
          label: 'Last seen',
          value: lastSeen
        }
      : null,
    friend?.address || friend?.location
      ? {
          label: 'Location',
          value: friend.address || friend.location
        }
      : null,
    activity
      ? {
          label: 'Activity',
          value: activity
        }
      : null,
    battery
      ? {
          label: 'Battery',
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
    avatarAlt: `${getFriendName(friend)} avatar`,
    iconClass: 'pi pi-user',
    rows,
    actions: googleMapsUrl
      ? [
          {
            key: 'open-google-maps',
            label: 'Open in Google Maps',
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
