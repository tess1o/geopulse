// Friend trail colors, shared by the raster and vector friend layers and the friends map.
// Based on the Okabe-Ito palette, which stays distinguishable for common color vision deficiencies.
// Its black is replaced with Paul Tol's wine and its yellow dropped, because both are hard to see on
// dark or light tiles. Color is not the only cue: each trail ends at the friend's avatar marker.
export const FRIEND_TRAIL_COLORS = Object.freeze([
  '#0072B2',
  '#E69F00',
  '#009E73',
  '#D55E00',
  '#56B4E9',
  '#CC79A7',
  '#882255'
])

export const getFriendTrailKey = (friend) => friend?.friendId || friend?.userId || friend?.id || friend?.email

/** Stable color for a friend: the same friend gets the same color on every map. */
export const getFriendTrailColor = (friend, index = 0) => {
  const key = String(getFriendTrailKey(friend) || `friend-${index}`)
  const hash = key
    .split('')
    .reduce((acc, character) => (acc * 31 + character.charCodeAt(0)) % FRIEND_TRAIL_COLORS.length, 0)

  return FRIEND_TRAIL_COLORS[hash]
}
