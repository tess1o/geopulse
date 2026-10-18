// Polls a background job until it completes or fails. `fetchJob` returns the job; `statusOf` reads its status from
// that response. `onUpdate` sees every response. Returns { promise, cancel }: the promise resolves with the final
// response, or with null when cancelled.
export const pollJob = ({ fetchJob, statusOf = job => job?.status, onUpdate = () => {}, intervalMs = 2000 }) => {
  let cancelled = false
  let timeoutId = null
  let resolvePromise = () => {}

  const promise = new Promise((resolve, reject) => {
    resolvePromise = resolve
    const poll = async () => {
      if (cancelled) {
        resolve(null)
        return
      }
      try {
        const job = await fetchJob()
        if (cancelled) {
          resolve(null)
          return
        }
        onUpdate(job)
        if (['completed', 'failed'].includes(statusOf(job))) {
          resolve(job)
          return
        }
        timeoutId = setTimeout(poll, intervalMs)
      } catch (error) {
        reject(error)
      }
    }
    poll()
  })

  return {
    promise,
    cancel: () => {
      cancelled = true
      if (timeoutId) {
        clearTimeout(timeoutId)
        timeoutId = null
      }
      resolvePromise(null)
    }
  }
}
