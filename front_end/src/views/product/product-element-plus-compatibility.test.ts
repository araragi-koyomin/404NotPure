import { describe, expect, it } from 'vitest'
import createProductSource from './CreateProduct.vue?raw'
import updateProductSource from './UpdateProduct.vue?raw'

describe('Product management Element Plus compatibility', () => {
  it.each([
    ['create product', createProductSource],
    ['update product', updateProductSource],
  ])('uses the link button API on %s', (_name, source) => {
    expect(source).not.toContain('type="text"')
    expect(source).toMatch(/<el-button\s+link\s+@click="product\.rate = 0"/)
  })
})
