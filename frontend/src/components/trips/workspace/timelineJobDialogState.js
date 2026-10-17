// Shared by the trip workspace page and TripTimelineJobDialog: the page blocks hiding the dialog while the job
// runs, and the dialog disables its close controls on the same rule.

export const normalizeTimelineJobStatus = (progress) => String(progress?.status || '').toUpperCase()

/** The dialog may close once the job finished or failed, or when there is no job (or no way to follow it). */
export const isTimelineJobDialogClosable = ({ jobId, progress, error }) => {
  if (error) return true
  if (!jobId) return true
  const status = normalizeTimelineJobStatus(progress)
  return status === 'COMPLETED' || status === 'FAILED'
}
