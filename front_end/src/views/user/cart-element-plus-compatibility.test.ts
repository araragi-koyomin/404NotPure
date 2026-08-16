import { describe, expect, it } from 'vitest'
import cartSource from './Cart.vue?raw'

describe('Cart Element Plus compatibility', () => {
  it('uses checkbox value instead of the deprecated label-as-value API', () => {
    expect(cartSource).toContain('<el-checkbox :value="item.cartItemId"')
    expect(cartSource).not.toContain('<el-checkbox :label="item.cartItemId"')
  })
})
