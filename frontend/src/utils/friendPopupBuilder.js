/**
 * Friend Popup Builder Utility
 * 
 * Provides a clean, template-based approach for creating friend popup content
 * instead of messy HTML string concatenation.
 */

/**
 * Creates a DOM element with specified tag, classes, and content
 */

import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

const createElement = (tag, className = '', content = '') => {
  const element = document.createElement(tag)
  if (className) element.className = className
  if (content) element.innerHTML = content
  return element
}

/**
 * Creates friend popup header with avatar and name
 */
const createPopupHeader = (friend, options = {}) => {
  const header = createElement('div', 'popup-header')
  
  // Avatar
  if (friend.avatar || friend.avatarUrl) {
    const avatar = createElement('img', 'popup-avatar')
    avatar.src = friend.avatar || friend.avatarUrl
    avatar.alt = friend.name || friend.fullName || 'Friend'
    header.appendChild(avatar)
  } else if (options.showAvatarPlaceholder) {
    const placeholder = createElement('div', 'popup-avatar-placeholder')
    placeholder.innerHTML = '<i class="fas fa-user"></i>'
    header.appendChild(placeholder)
  }
  
  // Info section
  const info = createElement('div', 'popup-info')
  
  // Name
  const name = createElement('div', 'popup-name')
  name.textContent = friend.name || friend.fullName || friend.username || 'Friend'
  info.appendChild(name)
  
  // Username (if different from name)
  if (friend.username && friend.name !== friend.username) {
    const username = createElement('div', 'popup-username')
    username.textContent = `@${friend.username}`
    info.appendChild(username)
  }
  
  header.appendChild(info)
  return header
}

/**
 * Creates status indicator
 */
const createStatusSection = (friend) => {
  if (!friend.status) return null
  
  const statusContainer = createElement('div', `popup-status ${friend.status.toLowerCase()}`)
  
  const indicator = createElement('i', 'status-indicator')
  const text = createElement('span')
  text.textContent = friend.status
  
  statusContainer.appendChild(indicator)
  statusContainer.appendChild(text)
  
  return statusContainer
}

/**
 * Creates last seen section
 */
const createLastSeenSection = (friend) => {
  const lastSeenDate = friend.lastSeen || friend.timestamp
  if (!lastSeenDate) return null
  
  const lastSeen = createElement('div', 'popup-last-seen')
  lastSeen.textContent = `Last seen: ${timezone.timeAgo(lastSeenDate)}`
  
  return lastSeen
}

/**
 * Creates location section
 */
const createLocationSection = (friend) => {
  const locationText = friend.address || friend.location
  if (!locationText) return null
  
  const location = createElement('div', 'popup-location')
  location.innerHTML = `
    <i class="fas fa-map-marker-alt"></i>
    <span>${locationText}</span>
  `
  
  return location
}

/**
 * Creates action buttons section
 */
const createActionsSection = (friend, options = {}) => {
  if (!options.showActions) return null
  
  const actions = createElement('div', 'popup-actions')
  
  if (options.actions?.message) {
    const messageBtn = createElement('button', 'popup-button message')
    messageBtn.textContent = 'Message'
    messageBtn.onclick = () => options.actions.message(friend.id)
    actions.appendChild(messageBtn)
  }
  
  if (options.actions?.locate) {
    const locateBtn = createElement('button', 'popup-button locate')
    locateBtn.textContent = 'Locate'
    locateBtn.onclick = () => options.actions.locate(friend.id)
    actions.appendChild(locateBtn)
  }
  
  return actions
}

/**
 * Main function to create friend popup content
 */
export const createFriendPopupContent = (friend, options = {}) => {
  const defaults = {
    showAvatarPlaceholder: false,
    showActions: false,
    containerClass: 'friend-popup',
    actions: null
  }
  
  const config = { ...defaults, ...options }
  
  // Create main container
  const container = createElement('div', config.containerClass)
  
  // Add header
  const header = createPopupHeader(friend, config)
  container.appendChild(header)
  
  // Add status if available
  const status = createStatusSection(friend)
  if (status) container.appendChild(status)
  
  // Add last seen
  const lastSeen = createLastSeenSection(friend)
  if (lastSeen) container.appendChild(lastSeen)
  
  // Add location
  const location = createLocationSection(friend)
  if (location) container.appendChild(location)
  
  // Add actions
  const actions = createActionsSection(friend, config)
  if (actions) container.appendChild(actions)
  
  return container.outerHTML
}

/**
 * Simplified version for basic popups (like FriendsLayer)
 */
export const createBasicFriendPopup = (friend) => {
  return createFriendPopupContent(friend, {
    containerClass: 'friend-popup',
    showAvatarPlaceholder: false,
    showActions: false
  })
}

/**
 * Full-featured version for detailed popups (like FriendMarker)
 */
export const createDetailedFriendPopup = (friend, actions = null) => {
  return createFriendPopupContent(friend, {
    containerClass: 'friend-marker-popup',
    showAvatarPlaceholder: true,
    showActions: !!actions,
    actions
  })
}

/**
 * Creates popup configuration object for Leaflet
 */
export const createPopupConfig = (friend, options = {}) => {
  const defaults = {
    detailed: false,
    actions: null,
    popupOptions: {
      closeButton: true,
      autoClose: false,
      className: 'friend-popup'
    }
  }
  
  const config = { ...defaults, ...options }
  
  const content = config.detailed 
    ? createDetailedFriendPopup(friend, config.actions)
    : createBasicFriendPopup(friend)
  
  return {
    content,
    options: config.popupOptions
  }
}

// Global functions for onclick handlers (if needed)
if (typeof window !== 'undefined') {
  window.messageFriend = (friendId) => {
    console.log('Message friend:', friendId)
    // Implement messaging functionality
  }
  
  window.locateFriend = (friendId) => {
    console.log('Locate friend:', friendId)
    // Implement location functionality
  }
}