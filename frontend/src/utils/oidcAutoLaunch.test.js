import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
    clearAutoLaunchSuppression,
    markAutoLaunchAttempted,
    markSignedOut,
    resolveAutoLaunchProvider
} from './oidcAutoLaunch'

const createStorage = () => {
    const values = new Map()
    return {
        getItem: (key) => (values.has(key) ? values.get(key) : null),
        setItem: (key, value) => values.set(key, String(value)),
        removeItem: (key) => values.delete(key),
        clear: () => values.clear()
    }
}

const installStorage = (name, storage) => {
    Object.defineProperty(globalThis, name, { configurable: true, value: storage })
    Object.defineProperty(window, name, { configurable: true, value: storage })
}

const provider = { name: 'authentik', displayName: 'Authentik' }

const status = (overrides = {}) => ({
    oidcAutoLaunchEnabled: true,
    passwordLoginEnabled: false,
    oidcLoginEnabled: true,
    demoModeEnabled: false,
    ...overrides
})

describe('resolveAutoLaunchProvider', () => {
    beforeEach(() => {
        installStorage('localStorage', createStorage())
        installStorage('sessionStorage', createStorage())
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    it('launches the only provider when password login is off and auto-launch is on', () => {
        expect(resolveAutoLaunchProvider(status(), [provider], {})).toBe(provider)
    })

    it.each([
        ['auto-launch is off', { oidcAutoLaunchEnabled: false }],
        ['password login is on', { passwordLoginEnabled: true }],
        ['OIDC login is off', { oidcLoginEnabled: false }],
        ['demo mode is on', { demoModeEnabled: true }]
    ])('shows the page when %s', (_, overrides) => {
        expect(resolveAutoLaunchProvider(status(overrides), [provider], {})).toBeNull()
    })

    it('shows the page without a status', () => {
        expect(resolveAutoLaunchProvider(null, [provider], {})).toBeNull()
    })

    it.each([
        ['no provider', []],
        ['several providers', [provider, { name: 'google', displayName: 'Google' }]]
    ])('shows the page with %s', (_, providers) => {
        expect(resolveAutoLaunchProvider(status(), providers, {})).toBeNull()
    })

    it.each([
        ['autoLaunch=0', { autoLaunch: '0' }],
        ['a reason', { reason: 'registration_disabled' }],
        ['an error', { error: 'access_denied' }]
    ])('shows the page when the URL carries %s', (_, query) => {
        expect(resolveAutoLaunchProvider(status(), [provider], query)).toBeNull()
    })

    it('keeps the redirect query working', () => {
        expect(resolveAutoLaunchProvider(status(), [provider], { redirect: '/app/trips' })).toBe(provider)
    })

    it('stays off after a deliberate sign-out until the next sign-in', () => {
        markSignedOut()
        expect(resolveAutoLaunchProvider(status(), [provider], {})).toBeNull()

        clearAutoLaunchSuppression()
        expect(resolveAutoLaunchProvider(status(), [provider], {})).toBe(provider)
    })

    it('launches after a deliberate sign-out when the user pressed "Sign in"', () => {
        markSignedOut()

        expect(resolveAutoLaunchProvider(status(), [provider], { autoLaunch: '1' })).toBe(provider)
    })

    it('does not let "Sign in" relaunch in a tab that came back from the provider', () => {
        markSignedOut()
        markAutoLaunchAttempted()

        expect(resolveAutoLaunchProvider(status(), [provider], { autoLaunch: '1' })).toBeNull()
    })

    it('does not let "Sign in" override the other conditions', () => {
        expect(resolveAutoLaunchProvider(status({ oidcAutoLaunchEnabled: false }), [provider], { autoLaunch: '1' })).toBeNull()
    })

    it('does not launch twice in a tab that came back without signing in', () => {
        markAutoLaunchAttempted()
        expect(resolveAutoLaunchProvider(status(), [provider], {})).toBeNull()

        clearAutoLaunchSuppression()
        expect(resolveAutoLaunchProvider(status(), [provider], {})).toBe(provider)
    })

    it('shows the page when storage cannot be read', () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {})
        installStorage('localStorage', {
            getItem: () => {
                throw new Error('blocked')
            }
        })

        expect(resolveAutoLaunchProvider(status(), [provider], {})).toBeNull()
    })
})
