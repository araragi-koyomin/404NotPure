<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {getCartList, removeFromCart as apiRemoveFromCart, updateCartQuantity} from '../../api/cart'
import {getProductStockpile} from '../../api/product'
import {Search} from '@element-plus/icons-vue'
import {router} from '../../router'

// -------------------------
// 🛒 购物车相关状态
// -------------------------
const cartList = ref<{ items: any[]; total: number; totalAmount: number }>({
  items: [],
  total: 0,
  totalAmount: 0
})
const searchKeyword = ref('')
const selectedItems = ref<string[]>([])
const isAllSelected = ref(false)
const isIndeterminate = ref(false)

// -------------------------
// 📦 加载购物车 + 库存信息
// -------------------------
const loadCart = async () => {
  try {
    cartList.value = await getCartList()

    await Promise.all(
        cartList.value.items.map(async (item) => {
          try {
            const stockRes = await getProductStockpile(item.productId)
            const { amount = 0, frozen = 0 } = stockRes.data.code === '200' ? stockRes.data.data : {}
            item.amount = amount
            item.frozen = frozen
          } catch {
            ElMessage.error(`商品 ${item.title} 库存加载失败`)
            item.amount = 0
            item.frozen = 0
          }
        })
    )
  } catch (error) {
    ElMessage.error('获取购物车数据失败！')
  }
}

// -------------------------
// 🧹 删除 + 修改数量
// -------------------------
const removeItemFromCart = async (cartItemId: string) => {
  try {
    await apiRemoveFromCart(cartItemId)
    ElMessage.success('商品已删除')
    await loadCart()
  } catch {
    ElMessage.error('删除商品失败')
  }
}

const updateQuantity = async (cartItemId: string, quantity: number) => {
  try {
    await updateCartQuantity(cartItemId, quantity)
    ElMessage.success('商品数量已更新')
    await loadCart()
  } catch {
    ElMessage.error('修改商品数量失败')
  }
}

// -------------------------
// ✅ 勾选操作
// -------------------------
const toggleSelectAll = () => {
  selectedItems.value = isAllSelected.value ? cartList.value.items.map(i => i.cartItemId) : []
  isIndeterminate.value = false
}

watch(selectedItems, () => {
  const total = cartList.value.items.length
  const selected = selectedItems.value.length
  isAllSelected.value = selected === total
  isIndeterminate.value = selected > 0 && selected < total
})

// -------------------------
// 💰 结算 + 跳转
// -------------------------
const totalAmount = computed(() =>
    selectedItems.value.reduce((sum, id) => {
      const item = cartList.value.items.find(i => i.cartItemId === id)
      return item ? sum + item.price * item.quantity : sum
    }, 0)
)

const checkout = () => {
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请先选择商品')
    return
  }

  // 先从 cartList 中找到选中的原始商品（包含 amount、frozen）
  const rawSelectedItems = cartList.value.items.filter(item =>
      selectedItems.value.includes(item.cartItemId)
  )

  // 校验库存：quantity 是否超过 (amount - frozen)
  for (const item of rawSelectedItems) {
    const availableStock = item.amount - item.frozen
    if (item.quantity > availableStock) {
      ElMessage.error(`${item.title} 库存不足，当前可用库存为 ${availableStock} 件`)
      return
    }
  }

  // 映射为 CartItem 类型（不包含 amount 和 frozen）
  const selectedCartItems: any[] = rawSelectedItems.map(item => ({
    cartItemId: item.cartItemId,
    productId: item.productId,
    title: item.title,
    price: item.price,
    description: item.description,
    cover: item.cover,
    detail: item.detail || '',
    amount: item.quantity
  }))

  sessionStorage.setItem('cartItems', JSON.stringify(selectedCartItems))
  sessionStorage.setItem('fromProductPage', 'false'); // 添加来源标识
  selectedItems.value = []
  router.push('/order')
}

const goToProduct = (id: string) => router.push(`/product/${id}`)

const handleCardClick = (item: any, e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (
      target.closest('.el-button') ||
      target.closest('.el-input-number') ||
      target.closest('.el-checkbox')
  ) return

  if (item.productId) {
    goToProduct(item.productId)
  } else {
    ElMessage.warning('商品 ID 缺失，无法跳转详情页')
  }
}

// -------------------------
// 🔍 搜索过滤
// -------------------------
const filteredCartItems = computed(() =>
    cartList.value.items.filter(item =>
        item.title.toLowerCase().includes(searchKeyword.value.toLowerCase())
    )
)

// -------------------------
onMounted(loadCart)
</script>

<template>
  <el-container class="cart-container">
    <!-- Sidebar (左侧购物车商品展示) -->
    <el-aside width="70%" class="sidebar">
      <el-row class="sidebar-header">
        <el-col :span="1"></el-col>
        <el-col :span="23" class="sidebar-title">购物车</el-col>
      </el-row>
      <div v-if="cartList.items.length > 0">
        <div v-if="filteredCartItems.length == 0">
          <div class="empty-cart">暂无商品</div>
        </div>
        <div v-else>
          <!-- 全选框 -->
          <el-row>
            <el-col :span="1"></el-col>
            <el-col :span="23">
              <el-checkbox
                  v-model="isAllSelected"
                  :indeterminate="isIndeterminate"
                  @change="toggleSelectAll"
                  class="select-all"
              >全选</el-checkbox>
            </el-col>
          </el-row>
          <el-checkbox-group v-model="selectedItems" class="cart-checkbox-group">
            <el-row :gutter="20" class="cart-row" v-for="item in filteredCartItems" :key="item.cartItemId">
              <!-- 商品信息 -->
              <el-col :span="1"></el-col>
              <el-col :span="23">
                <el-card
                    class="product-card"
                    shadow="hover"
                    @click="handleCardClick(item, $event)"
                >
                  <el-row :gutter="10" class="product-info" wrap>
                    <el-col :span="1">
                      <el-checkbox :label="item.cartItemId">&nbsp;</el-checkbox>
                    </el-col>
                    <el-col :span="3" class="image-col">
                      <el-image :src="item.cover" class="product-image" fit="contain" />
                    </el-col>
                    <el-col :span="13" class="product-details">
                      <div class="product-title">{{ item.title }}</div>
                      <div class="product-price">¥{{ item.price }}</div>
                      <div class="product-description">描述：{{ item.description }}</div>
                      <!-- 展示库存信息 -->
                      <div class="product-stock">
                        <span>库存: {{ item.amount }}件 </span>
                        <span>冻结: {{ item.frozen }}件</span>
                      </div>
                    </el-col>
                    <el-col :span="5" class="quantity-wrapper">
                      <el-input-number
                          v-model="item.quantity"
                          :min="1"
                          :step="1"
                          @change.stop="updateQuantity(item.cartItemId, item.quantity)"
                      />
                    </el-col>
                    <el-col :span="2" class="product-actions">
                      <el-button type="danger" @click.stop="removeItemFromCart(item.cartItemId)">删除</el-button>
                    </el-col>
                  </el-row>
                </el-card>
              </el-col>
            </el-row>
          </el-checkbox-group>
        </div>
      </div>
      <div v-else>
        <div class="empty-cart">购物车是空的，赶紧去购物吧！</div>
      </div>
    </el-aside>

    <el-divider
        direction="vertical"
        style="
          height: 80%;
          align-self: center;
          margin: 0 10px;
          border-left: 2px solid rgba(0, 0, 0, 0.15);
        "
    />

    <!-- Main Content (右侧搜索框和结算明细) -->
    <el-main class="cart-main">
      <!-- 搜索框 -->
      <div class="search-wrapper">
        <el-input
            v-model="searchKeyword"
            placeholder="搜索商品"
            class="search-input"
            clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <!-- 结算信息 (放在 el-card 中) -->
      <el-card class="checkout-card" shadow="hover">
        <div class="checkout-header">
          <span>结算明细</span>
        </div>
        <div class="checkout-body">
          <!-- 展示已选中商品的图片列表 -->
          <el-row :gutter="10">
            <el-col :span="8" v-for="item in cartList.items.filter(i => selectedItems.includes(i.cartItemId))" :key="item.cartItemId">
              <el-image :src="item.cover" class="checkout-image" fit="contain" />
            </el-col>
          </el-row>
          <div class="checkout-total">
            <span>已选商品：{{ selectedItems.length }} 种</span>
            <span>总金额：¥{{ totalAmount }}</span>
          </div>
        </div>
        <div class="checkout-footer">
          <el-button type="primary" @click="checkout" :disabled="selectedItems.length === 0">结算</el-button>
        </div>
      </el-card>
    </el-main>
  </el-container>
</template>

<style scoped>
.cart-container {
  display: flex;
  width: 100%;
  min-height: 100vh;
}

.sidebar {
  width: 70%; /* el-aside 占 70% */
  padding: 20px;
  background-color: transparent;
}

.sidebar-title {
  font-weight: bold;
  font-size: 22px;
  color: #303133;
  box-shadow: 0 2px 2px rgba(0, 0, 0, 0.02);
  padding: 15px 4px;
  border-radius: 8px;
  margin-bottom: 8px;
}

.select-all {
  padding: 12px 2px;
  border-radius: 8px;
}

:deep(.el-checkbox__label) {
  font-size: 16px;
}

.cart-checkbox-group {
  width: 100%;
}

.product-card {
  background-color: rgba(255, 255, 255, 0.7); /* 半透明白色 */
  border-radius: 10px;
  box-shadow: 0 2px 6px rgba(0,0,0,0.05);
}

.product-info {
  overflow: hidden;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  padding: 8px 0;
  max-width: 1500px;
  width: 100%;
  height: 120px;
}

.image-col {
  padding-left: 0 !important;
  display: flex;
  justify-content: flex-start;
  align-items: center;
}

.product-image {
  width: 90px;
  height: 90px;
  object-fit: cover;
  display: block;
}

.product-details {
  width: 90%;
  height: 90%;
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: space-between;
  padding-left: 4px;
}

.product-title {
  font-weight: bold;
  font-size: 18px;
  color: #303133;
  margin-bottom: 4px;
}

.product-price {
  color: #f56c6c;
  font-size: 16px;
  font-weight: 500;
}

.product-description {
  font-size: 14px;
  color: #666;
  line-height: 1.0;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 2px;
}

.product-stock {
  font-size: 13px;
  color: #999;
  display: flex;
  gap: 10px;
}

.product-actions {
  width: 100%;
  display: flex;
  justify-content: center; /* 水平居中 */
  align-items: center;     /* 垂直居中 */
  height: 100%;            /* 让它填满父元素高度 */
}

.cart-main {
  width: 30%; /* el-main 占 30% */
  padding: 20px;
}

.search-wrapper {
  margin-bottom: 20px;
}

.checkout-card {
  background-color: rgba(255, 255, 255, 0.7); /* 半透明 */
  border-radius: 12px;
  padding: 20px;
}

.checkout-header {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 10px;
}

.checkout-body {
  margin-top: 10px;
}

.checkout-image {
  width: 100%;
  height: 80px;
  border-radius: 6px;
  object-fit: cover;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.checkout-total {
  margin-top: 16px;
  display: flex;
  justify-content: space-between; /* 左右对齐 */
  font-size: 16px;
  font-weight: 500;
  color: #333;
  padding: 8px 0;
  border-top: 1px solid #eee;
}

.checkout-total span:last-child {
  color: #f56c6c;
  font-size: 18px;
  font-weight: bold;
}

.checkout-footer {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.checkout-footer .el-button {
  width: 100%;
  max-width: 320px;
  font-size: 16px;
  padding: 14px 0;
  font-weight: bold;
  border-radius: 8px;
}

.empty-cart {
  text-align: center;
  font-size: 18px;
  color: #999;
}
</style>
