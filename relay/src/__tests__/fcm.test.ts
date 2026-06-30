import { describe, it, expect, vi, beforeEach } from 'vitest'
import { sendPushNotification } from '../fcm'

beforeEach(() => {
  vi.clearAllMocks()
  globalThis.fetch = vi.fn()
})

describe('sendPushNotification', () => {
  it('uses FCM_SERVER_KEY for Authorization header', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(new Response('ok'))
    const key = 'test-key-123'

    await sendPushNotification(key, ['token1'], { type: 'test' })

    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
    const [url, opts] = (globalThis.fetch as any).mock.calls[0]
    expect(url).toBe('https://fcm.googleapis.com/fcm/send')
    expect(opts.headers.Authorization).toBe('key=test-key-123')
  })

  it('skips fetch when FCM_SERVER_KEY is empty', async () => {
    await sendPushNotification('', ['token1'], { type: 'test' })
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })

  it('skips fetch when tokens array is empty', async () => {
    await sendPushNotification('test-key', [], { type: 'test' })
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })

  it('catches fetch error gracefully', async () => {
    globalThis.fetch = vi.fn().mockRejectedValueOnce(new Error('Network failure'))
    await expect(
      sendPushNotification('test-key', ['token1'], { type: 'test' }),
    ).resolves.toBeUndefined()
  })

  it('sends all tokens as registration_ids batch', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(new Response('ok'))
    const tokens = ['token-a', 'token-b', 'token-c']

    await sendPushNotification('test-key', tokens, { type: 'test' })

    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
    const [, opts] = (globalThis.fetch as any).mock.calls[0]
    const body = JSON.parse(opts.body)
    expect(body.registration_ids).toEqual(['token-a', 'token-b', 'token-c'])
    expect(body.data).toEqual({ type: 'test' })
  })

  it('includes payload data in the request', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(new Response('ok'))

    await sendPushNotification('test-key', ['token1'], {
      type: 'recovery_initiated',
      walletAddress: '0x123',
      title: 'Test Title',
      body: 'Test body',
    })

    const [, opts] = (globalThis.fetch as any).mock.calls[0]
    const body = JSON.parse(opts.body)
    expect(body.data.type).toBe('recovery_initiated')
    expect(body.data.walletAddress).toBe('0x123')
    expect(body.data.title).toBe('Test Title')
    expect(body.data.body).toBe('Test body')
  })
})
