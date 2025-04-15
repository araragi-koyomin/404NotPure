<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {ElButton, ElCard, ElCheckbox, ElCol, ElImage, ElInput, ElInputNumber, ElMessage, ElRow} from 'element-plus'
import {getCartList, removeFromCart as apiRemoveFromCart, updateCartQuantity} from '../../api/cart'
import {getProductStockpile} from '../../api/product' // 引入库存查询 API
import {Search} from '@element-plus/icons-vue'

// 为 cartList 添加正确的类型声明
const cartList = ref<{ items: any[]; total: number; totalAmount: number }>({
  items: [],
  total: 0,
  totalAmount: 0
})

const searchKeyword = ref('')
const selectedItems = ref<string[]>([]) // 存储选中的商品ID
const isAllSelected = ref(false) // 是否全选
const isIndeterminate = ref(false) // 是否部分选中

// 获取购物车数据并为每个商品动态添加 amount 和 frozen 属性
const loadCart = async () => {
  try {
    // 明确指定返回数据类型
    cartList.value = await getCartList() as { items: any[]; total: number; totalAmount: number }

    // 获取每个商品的库存信息
    for (let item of cartList.value.items) {
      const stockRes = await getProductStockpile(item.productId)
      if (stockRes.data.code === '200') {
        // 动态添加 amount 和 frozen 属性
        item.amount = stockRes.data.data.amount
        item.frozen = stockRes.data.data.frozen
      } else {
        ElMessage.error('加载库存失败：' + stockRes.data.msg)
        item.amount = 0
        item.frozen = 0
      }
    }
  } catch (error) {
    ElMessage.error('获取购物车数据失败！')
  }
}

// 删除购物车商品
const removeItemFromCart = async (cartItemId: string) => {
  try {
    await apiRemoveFromCart(cartItemId)
    ElMessage.success('商品已删除')
    await loadCart()
  } catch (error) {
    ElMessage.error('删除商品失败')
  }
}

// 修改商品数量
const updateQuantity = async (cartItemId: string, quantity: number) => {
  try {
    await updateCartQuantity(cartItemId, quantity)
    ElMessage.success('商品数量已更新')
    loadCart()
  } catch (error) {
    ElMessage.error('修改商品数量失败')
  }
}

// 选择全部商品
const toggleSelectAll = () => {
  if (isAllSelected.value) {
    selectedItems.value = cartList.value.items.map(item => item.cartItemId)
  } else {
    selectedItems.value = []
  }
  isIndeterminate.value = false
}

// 计算总金额
const totalAmount = computed(() => {
  return cartList.value.items
      .filter(item => selectedItems.value.includes(item.cartItemId))
      .reduce((total, item) => total + item.price * item.quantity, 0)
})

// 过滤符合搜索关键字的商品
const filteredCartItems = computed(() => {
  return cartList.value.items.filter(item => item.title.toLowerCase().includes(searchKeyword.value.toLowerCase()))
})

// 结算功能
const checkout = () => {
  if (selectedItems.value.length === 0) {
    ElMessage.warning('请先选择商品')
    return
  }

  // 模拟结算
  ElMessage.success(`结算成功，总金额 ¥${totalAmount.value}`)
  selectedItems.value = [] // 重置选中的商品
}

// 全选状态变化时更新部分选中状态
watch(selectedItems, () => {
  const selectedCount = selectedItems.value.length
  isAllSelected.value = selectedCount === cartList.value.items.length
  isIndeterminate.value = selectedCount > 0 && selectedCount < cartList.value.items.length
})

onMounted(() => {
  loadCart()
})
</script>

<template>
  <el-container class="cart-container">
    <!-- Sidebar -->
    <el-aside width="200px" class="sidebar">
      <div class="sidebar-header">
        <h3 class="sidebar-title">购物车</h3>
      </div>
    </el-aside>

    <!-- Main Content -->
    <el-main class="cart-main">
      <!-- Search Box -->
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

      <!-- Cart Items List -->
      <div v-if="cartList.items.length > 0">
        <el-checkbox-group v-model="selectedItems" class="cart-checkbox-group">
          <el-row :gutter="20" class="cart-row" v-for="item in filteredCartItems" :key="item.cartItemId">
            <el-col :span="24">
              <el-card class="product-card" shadow="hover">
                <el-checkbox :label="item.cartItemId"></el-checkbox>
                <el-row :gutter="10" class="product-info">
                  <el-col :span="4">
                    <el-image :src="item.cover" class="product-image" fit="contain" />
                  </el-col>
                  <el-col :span="12" class="product-details">
                    <div class="product-title">{{ item.title }}</div>
                    <div class="product-price">¥{{ item.price }}</div>
                  </el-col>
                  <el-col :span="4" class="quantity-wrapper">
                    <el-input-number
                        v-model="item.quantity"
                        :min="1"
                        :step="1"
                        @change="updateQuantity(item.cartItemId, item.quantity)"
                    />
                  </el-col>
                  <el-col :span="4" class="product-actions">
                    <el-button type="danger" @click="removeItemFromCart(item.cartItemId)">删除</el-button>
                  </el-col>
                </el-row>
              </el-card>
            </el-col>
          </el-row>
        </el-checkbox-group>

        <!-- Select All Checkbox -->
        <el-checkbox
            v-model="isAllSelected"
            :indeterminate="isIndeterminate"
            @change="toggleSelectAll"
        >全选</el-checkbox>

        <!-- Cart Footer -->
        <div class="cart-footer">
          <div class="checkout-info">
            <span>已选商品：{{ selectedItems.length }} 件</span>
            <span>总金额：¥{{ totalAmount }}</span>
          </div>
          <el-button type="primary" @click="checkout" :disabled="selectedItems.length === 0">结算</el-button>
        </div>
      </div>

      <!-- Empty Cart Message -->
      <div v-else>
        <div class="empty-cart">购物车是空的，赶紧去购物吧！</div>
      </div>
    </el-main>
  </el-container>
</template>

<style scoped>
.cart-container {
  display: flex;
  width: 100%;
  min-height: 100vh;
}

.cart-main {
  width: 100%;
  padding: 20px;
  background-color: #f8f8f8;
}

.search-wrapper {
  margin-bottom: 20px;
  width: 100%;
  max-width: 600px;
  margin: 0 auto;
}

.product-card {
  margin-bottom: 15px;
  padding: 10px;
  border-radius: 8px;
  background-color: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.product-info {
  display: flex;
  align-items: center;
}

.product-image {
  width: 50px;
  height: 50px;
  object-fit: cover;
}

.product-details {
  flex: 1;
  padding-left: 10px;
}

.product-title {
  font-weight: bold;
  font-size: 14px;
  color: #333;
}

.product-price {
  font-size: 14px;
  color: #ff5722;
}

.quantity-wrapper {
  width: 100px;
}

.product-actions {
  display: flex;
  justify-content: center;
}

.cart-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
}

.checkout-info {
  font-size: 16px;
  font-weight: bold;
}

.empty-cart {
  text-align: center;
  font-size: 18px;
  color: #999;
}

.sidebar {
  background-color: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.sidebar-header {
  margin-bottom: 10px;
}

.sidebar-title {
  font-size: 20px;
  font-weight: bold;
  color: #333;
}

.search-input {
  background-color: #fff;
  border-radius: 5px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}
</style>
