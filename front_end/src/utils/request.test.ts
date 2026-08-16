import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { axios } from './request'

const values = new Map<string, string>()
const storage: Storage = {
  get length() {
    return values.size
  },
  clear() {
    values.clear()
  },
  getItem(key: string) {
    return values.get(key) ?? null
  },
  key(index: number) {
    return Array.from(values.keys())[index] ?? null
  },
  removeItem(key: string) {
    values.delete(key)
  },
  setItem(key: string, value: string) {
    values.set(key, value)
  },
}

Object.defineProperty(globalThis, 'sessionStorage', {
  configurable: true,
  value: storage,
})

const originalAdapter = axios.defaults.adapter

beforeEach(() => {
  storage.clear()
  vi.restoreAllMocks()
})

afterAll(() => {
  axios.defaults.adapter = originalAdapter
})

describe('shared Axios instance compatibility', () => {
  it('keeps cookie credentials and the compatible session token request header', async () => {
    let observedConfig: InternalAxiosRequestConfig | undefined
    const adapter: AxiosAdapter = async config => {
      observedConfig = config
      return {
        config,
        data: { code: '200', data: 'ok' },
        headers: {},
        status: 200,
        statusText: 'OK',
      }
    }
    axios.defaults.adapter = adapter
    storage.setItem('token', 'fake-compatible-token')

    const response = await axios.get('/api/compatibility-check')

    expect(response.status).toBe(200)
    expect(observedConfig?.withCredentials).toBe(true)
    expect(observedConfig?.headers.get('token')).toBe('fake-compatible-token')
  })

  it('rejects the original Axios error while logging only the safe description', async () => {
    const error = new AxiosError('fake-sensitive-message', 'ERR_NETWORK')
    const adapter: AxiosAdapter = async () => {
      throw error
    }
    axios.defaults.adapter = adapter
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    await expect(axios.get('/api/network-failure')).rejects.toBe(error)

    expect(consoleError).toHaveBeenCalledWith('HTTP 请求失败：', {
      name: 'AxiosError',
      code: 'ERR_NETWORK',
    })
    expect(JSON.stringify(consoleError.mock.calls)).not.toContain('fake-sensitive-message')
  })
})
