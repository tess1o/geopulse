// Debug exports move coordinates east or west only. A longitude shift turns the globe around its axis, so every
// distance, speed and heading stays exactly the same and the timeline regenerates as it was. A latitude shift would
// stretch or squeeze east-west distances (they scale with the cosine of the latitude) and change the timeline.
const MIN_SHIFT_DEGREES = 30
const MAX_SHIFT_DEGREES = 180

export const generateLongitudeShift = (random = Math.random) => {
  const magnitude = MIN_SHIFT_DEGREES + random() * (MAX_SHIFT_DEGREES - MIN_SHIFT_DEGREES)
  const sign = random() < 0.5 ? -1 : 1
  return parseFloat((sign * magnitude).toFixed(6))
}
