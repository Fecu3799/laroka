import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'

vi.mock('../services/pushService', () => ({ upsertPushSubscription: vi.fn() }))

import { upsertPushSubscription } from '../services/pushService'
import { usePushSubscription } from '../hooks/usePushSubscription'

const SUB_ID = '11111111-1111-1111-1111-111111111111'
const fakeSub = { toJSON: () => ({ endpoint: 'e', keys: { p256dh: 'p', auth: 'a' } }) }

function setupPushEnv({ existing = null, permission = 'granted', subscribeResult = fakeSub } = {}) {
  const subscribe = vi.fn().mockResolvedValue(subscribeResult)
  const getSubscription = vi.fn().mockResolvedValue(existing)
  const registration = { pushManager: { getSubscription, subscribe } }
  Object.defineProperty(navigator, 'serviceWorker', {
    value: { ready: Promise.resolve(registration) },
    configurable: true,
  })
  window.PushManager = function PushManager() {}
  globalThis.Notification = {
    permission,
    requestPermission: vi.fn().mockResolvedValue('granted'),
  }
  return { subscribe, getSubscription }
}

function teardownPushEnv() {
  delete navigator.serviceWorker
  delete window.PushManager
  delete globalThis.Notification
}

describe('usePushSubscription', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    // Clave VAPID ficticia con formato Base64 URL-safe válido (decodifica a 65
    // bytes), sin valor criptográfico — solo para que urlBase64ToUint8Array
    // funcione sin error. No es la clave real del proyecto.
    vi.stubEnv(
      'VITE_VAPID_PUBLIC_KEY',
      'BDBVep_E6Q4zWH2ix-wRNluApcrvFDleg6jN8hc8YYar0PUaP2SJrtP4HUJnjLHW-yBFao-02f4jSG2St9wBJks=',
    )
  })

  afterEach(() => {
    teardownPushEnv()
    vi.unstubAllEnvs()
  })

  describe('getOrCreateSubscription', () => {
    it('returns id from existing subscription (no subscribe call)', async () => {
      const { subscribe } = setupPushEnv({ existing: fakeSub })
      upsertPushSubscription.mockResolvedValue(SUB_ID)
      const { result } = renderHook(() => usePushSubscription())

      let id
      await act(async () => { id = await result.current.getOrCreateSubscription() })

      expect(id).toBe(SUB_ID)
      expect(subscribe).not.toHaveBeenCalled()
      expect(upsertPushSubscription).toHaveBeenCalledWith(fakeSub)
    })

    it('creates a subscription when none exists and permission granted', async () => {
      const { subscribe } = setupPushEnv({ existing: null, permission: 'granted' })
      upsertPushSubscription.mockResolvedValue(SUB_ID)
      const { result } = renderHook(() => usePushSubscription())

      let id
      await act(async () => { id = await result.current.getOrCreateSubscription() })

      // applicationServerKey debe pasarse como Uint8Array (bytes EC crudos),
      // nunca como string Base64: pasar el string produce, en algunos browsers,
      // una suscripción firmada con otra clave → 403 de FCM.
      const call = subscribe.mock.calls[0][0]
      expect(call.userVisibleOnly).toBe(true)
      expect(call.applicationServerKey).toBeInstanceOf(Uint8Array)
      expect(call.applicationServerKey.length).toBeGreaterThan(0)
      expect(id).toBe(SUB_ID)
    })

    it('returns null when push is not supported', async () => {
      // No serviceWorker / PushManager defined
      const { result } = renderHook(() => usePushSubscription())
      let id
      await act(async () => { id = await result.current.getOrCreateSubscription() })
      expect(id).toBeNull()
    })

    it('returns null when no subscription and permission not granted', async () => {
      const { subscribe } = setupPushEnv({ existing: null, permission: 'default' })
      const { result } = renderHook(() => usePushSubscription())

      let id
      await act(async () => { id = await result.current.getOrCreateSubscription() })

      expect(id).toBeNull()
      expect(subscribe).not.toHaveBeenCalled()
    })

    it('returns null (does not throw) when the backend upsert fails', async () => {
      setupPushEnv({ existing: fakeSub })
      upsertPushSubscription.mockRejectedValue(new Error('boom'))
      const { result } = renderHook(() => usePushSubscription())

      let id
      await act(async () => { id = await result.current.getOrCreateSubscription() })
      expect(id).toBeNull()
    })
  })

  describe('requestPermissionAndSubscribe', () => {
    it('opts in: shows sheet, requests permission, returns id', async () => {
      setupPushEnv({ existing: fakeSub })
      upsertPushSubscription.mockResolvedValue(SUB_ID)
      const { result } = renderHook(() => usePushSubscription())

      let pending
      act(() => { pending = result.current.requestPermissionAndSubscribe() })

      // El sheet de opt-in está abierto a la espera de la decisión del usuario.
      expect(result.current.sheet).toEqual({ open: true, variant: 'optin' })

      await act(async () => {
        result.current.acceptSheet()
        await pending.then((v) => { pending = v })
      })

      expect(pending).toBe(SUB_ID)
      expect(Notification.requestPermission).toHaveBeenCalled()
      expect(result.current.sheet.open).toBe(false)
    })

    it('dismissing the sheet returns null without requesting permission', async () => {
      setupPushEnv({ existing: fakeSub })
      const { result } = renderHook(() => usePushSubscription())

      let pending
      act(() => { pending = result.current.requestPermissionAndSubscribe() })
      await act(async () => {
        result.current.dismissSheet()
        await pending.then((v) => { pending = v })
      })

      expect(pending).toBeNull()
      expect(Notification.requestPermission).not.toHaveBeenCalled()
    })

    it('does not re-prompt within the same session', async () => {
      setupPushEnv({ existing: fakeSub })
      const { result } = renderHook(() => usePushSubscription())

      // Primera vez: abre el sheet y el usuario lo descarta.
      let first
      act(() => { first = result.current.requestPermissionAndSubscribe() })
      await act(async () => {
        result.current.dismissSheet()
        await first.then((v) => { first = v })
      })

      // Segunda vez: no abre nada, retorna null directo.
      let second
      await act(async () => { second = await result.current.requestPermissionAndSubscribe() })
      expect(second).toBeNull()
      expect(result.current.sheet.open).toBe(false)
    })
  })

  describe('showInstallInstructions', () => {
    it('shows the install sheet once per session and returns null', async () => {
      setupPushEnv({ existing: fakeSub })
      const { result } = renderHook(() => usePushSubscription())

      let pending
      act(() => { pending = result.current.showInstallInstructions() })
      expect(result.current.sheet).toEqual({ open: true, variant: 'install' })

      await act(async () => {
        result.current.dismissSheet()
        await pending.then((v) => { pending = v })
      })
      expect(pending).toBeNull()

      // Segunda vez en la sesión: no se vuelve a mostrar.
      let second
      await act(async () => { second = await result.current.showInstallInstructions() })
      expect(second).toBeNull()
      expect(result.current.sheet.open).toBe(false)
    })
  })
})
