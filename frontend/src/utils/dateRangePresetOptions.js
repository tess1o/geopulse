/**
 * Whether a timeline label should appear in DatePicker preset dropdowns.
 *
 * Labels may now be planned entirely in the future, but every DateRangePicker
 * clamps its calendar to `maxDate = today`. Offering a future label as a preset
 * would produce an option the picker cannot represent, so it is withheld until
 * the label's start date arrives.
 */
export function shouldShowTimelineLabelAsPreset(label) {
  if (label?.showAsPreset === false) return false

  const startMs = label?.startTime ? new Date(label.startTime).getTime() : null
  return startMs === null || startMs <= Date.now()
}
