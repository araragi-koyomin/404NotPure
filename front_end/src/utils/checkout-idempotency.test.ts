import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearCheckoutAttempt,
  getOrCreateCheckoutAttempt,
  markCheckoutOrderCreated,
  readCheckoutAttempt,
  shouldStartNewCheckoutAfterPaymentError
} from './checkout-idempotency'

class MemoryStorage {
  private values = new Map<string, string>()

  getItem(key: string): string | null {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value)
  }

  removeItem(key: string): void {
    this.values.delete(key)
  }
}

describe('checkout idempotency state', () => {
  let storage: MemoryStorage
  const uuids = [
    '123e4567-e89b-12d3-a456-426614174000',
    '123e4567-e89b-12d3-a456-426614174001'
  ]

  beforeEach(() => {
    storage = new MemoryStorage()
  })

  it('reuses the key after refresh when the business request is unchanged', () => {
    const factory = vi.fn().mockReturnValueOnce(uuids[0])
    const first = getOrCreateCheckoutAttempt(storage, request([[8, 1], [2, 3]]), factory)
    const refreshed = getOrCreateCheckoutAttempt(storage, request([[2, 3], [8, 1]]), factory)

    expect(refreshed).toEqual(first)
    expect(factory).toHaveBeenCalledTimes(1)
  })

  it('aggregates duplicate products for a stable request identity', () => {
    const first = getOrCreateCheckoutAttempt(
      storage,
      request([[8, 2], [2, 3], [8, 3]]),
      () => uuids[0]
    )
    const second = getOrCreateCheckoutAttempt(
      storage,
      request([[2, 3], [8, 5]]),
      () => uuids[1]
    )

    expect(second.idempotencyKey).toBe(first.idempotencyKey)
  })

  it('rotates the key when products quantities or payment method change', () => {
    const factory = vi.fn().mockReturnValueOnce(uuids[0]).mockReturnValueOnce(uuids[1])
    const first = getOrCreateCheckoutAttempt(storage, request([[2, 1]]), factory)
    const changed = getOrCreateCheckoutAttempt(storage, request([[2, 2]]), factory)

    expect(changed.idempotencyKey).not.toBe(first.idempotencyKey)
    expect(changed.orderId).toBeUndefined()
  })

  it('normalizes the numeric backend order id so payment failure and refresh do not create another order', () => {
    const attempt = getOrCreateCheckoutAttempt(storage, request([[2, 1]]), () => uuids[0])
    markCheckoutOrderCreated(storage, attempt, 9001)

    const restored = getOrCreateCheckoutAttempt(storage, request([[2, 1]]), () => uuids[1])
    expect(restored.idempotencyKey).toBe(uuids[0])
    expect(restored.orderId).toBe('9001')
  })

  it('clears a conflicting attempt but keeps valid JSON boundaries', () => {
    getOrCreateCheckoutAttempt(storage, request([[2, 1]]), () => uuids[0])
    clearCheckoutAttempt(storage)
    expect(readCheckoutAttempt(storage)).toBeNull()

    storage.setItem('tomatomall:checkout-attempt:v1', '{broken')
    expect(readCheckoutAttempt(storage)).toBeNull()
  })

  it('starts a new attempt only when the backend confirms the old order was cancelled or closed', () => {
    expect(shouldStartNewCheckoutAfterPaymentError({ code: '410' })).toBe(true)
    expect(shouldStartNewCheckoutAfterPaymentError({ code: '409' })).toBe(false)
    expect(shouldStartNewCheckoutAfterPaymentError({ code: '500' })).toBe(false)
    expect(shouldStartNewCheckoutAfterPaymentError(new Error('network failure'))).toBe(false)
  })

  const request = (items: Array<[number, number]>) => ({
    paymentMethod: 'Alipay',
    items: items.map(([productId, amount]) => ({ productId: String(productId), amount }))
  })
})
