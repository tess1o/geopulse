export const DEMO_MODE_SUMMARY = 'Demo Mode'
export const DEMO_ACTION_DISABLED_MESSAGE = 'This action is disabled in demo mode.'

export function showDemoModeToast(toast, detail = DEMO_ACTION_DISABLED_MESSAGE, options = {}) {
  toast.add({
    severity: 'warn',
    summary: DEMO_MODE_SUMMARY,
    detail,
    life: 3500,
    ...options
  })
}

export function showDemoReadOnlyToast(toast, detail = DEMO_ACTION_DISABLED_MESSAGE, options = {}) {
  toast.add({
    severity: 'info',
    summary: 'Read-only',
    detail,
    life: 3000,
    ...options
  })
}
