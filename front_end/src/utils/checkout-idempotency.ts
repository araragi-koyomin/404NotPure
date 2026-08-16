import type { OrderRequest } from '../api/order'

const STORAGE_KEY = 'tomatomall:checkout-attempt:v1'

type CheckoutStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export type CheckoutAttempt = {
  requestIdentity: string
  idempotencyKey: string
  orderId?: string
}

export const checkoutRequestIdentity = (request: OrderRequest): string => {
  const quantities = new Map<number, number>()
  request.items.forEach(item => {
    const productId = Number(item.productId)
    quantities.set(productId, (quantities.get(productId) ?? 0) + item.amount)
  })
  const items = [...quantities.entries()]
    .sort(([first], [second]) => first - second)
    .map(([productId, amount]) => `${productId}:${amount}`)
    .join(',')
  return `v1|paymentMethod=${request.paymentMethod.trim().toLowerCase()}|items=${items}`
}

export const readCheckoutAttempt = (storage: CheckoutStorage): CheckoutAttempt | null => {
  const stored = storage.getItem(STORAGE_KEY)
  if (!stored) return null
  try {
    const parsed = JSON.parse(stored) as Partial<CheckoutAttempt>
    if (typeof parsed.requestIdentity !== 'string' ||
        typeof parsed.idempotencyKey !== 'string' ||
        (parsed.orderId !== undefined && typeof parsed.orderId !== 'string')) {
      storage.removeItem(STORAGE_KEY)
      return null
    }
    return parsed as CheckoutAttempt
  } catch {
    storage.removeItem(STORAGE_KEY)
    return null
  }
}

export const getOrCreateCheckoutAttempt = (
  storage: CheckoutStorage,
  request: OrderRequest,
  uuidFactory: () => string = () => crypto.randomUUID()
): CheckoutAttempt => {
  const requestIdentity = checkoutRequestIdentity(request)
  const existing = readCheckoutAttempt(storage)
  if (existing?.requestIdentity === requestIdentity) return existing

  const created: CheckoutAttempt = {
    requestIdentity,
    idempotencyKey: uuidFactory()
  }
  storage.setItem(STORAGE_KEY, JSON.stringify(created))
  return created
}

export const markCheckoutOrderCreated = (
  storage: CheckoutStorage,
  attempt: CheckoutAttempt,
  orderId: string | number
): CheckoutAttempt => {
  const updated = { ...attempt, orderId: String(orderId) }
  storage.setItem(STORAGE_KEY, JSON.stringify(updated))
  return updated
}

export const clearCheckoutAttempt = (storage: CheckoutStorage): void => {
  storage.removeItem(STORAGE_KEY)
}

export const shouldStartNewCheckoutAfterPaymentError = (error: unknown): boolean => {
  if (typeof error !== 'object' || error === null || !('code' in error)) return false
  return (error as { code?: unknown }).code === '410'
}
