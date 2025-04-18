<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { router } from '../../router'
import { submitOrder as apiSubmitOrder, initiatePayment as apiInitiatePayment, OrderRequest } from '../../api/order'

// Types
type CartItem = {
  productId: string;
  title: string;
  price: number;
  description: string;
  cover: string;
  detail?: string;
  amount: number;
}

// Refs
const cartItems = ref<CartItem[]>([])
const loading = ref(false)
const submitting = ref(false)
const orderId = ref<string>('')
const orderStatus = ref('PENDING')
const paymentFormHtml = ref('')

// 计算属性
const totalAmount = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + (item.price * item.amount), 0).toFixed(2)
})

const totalItems = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + item.amount, 0)
})

// Load items from sessionStorage
const loadCartItems = () => {
  try {
    const storedItems = sessionStorage.getItem('cartItems')
    if (storedItems) {
      cartItems.value = JSON.parse(storedItems)
      
      if (!cartItems.value.length) {
        ElMessage.warning('没有选择任何商品，无法结算')
        router.push('/cart')
      }
    } else {
      ElMessage.warning('没有选择任何商品，无法结算')
      router.push('/cart')
    }
  } catch (error) {
    console.error('加载购物车商品失败:', error)
    ElMessage.error('加载购物车商品失败')
    router.push('/cart')
  }
}

// 准备订单信息
const prepareOrderData = (): OrderRequest => {
  return {
    paymentMethod: 'Alipay',
    items: cartItems.value.map(item => ({
      productId: item.productId,
      amount: item.amount
    }))
  }
}

// Submit the order
const submitOrder = async () => {
  if (cartItems.value.length === 0) {
    ElMessage.warning('请选择商品后再结算')
    return
  }

  try {
    submitting.value = true
    const orderData = prepareOrderData()
    
    const orderResponse = await apiSubmitOrder(orderData)
    orderId.value = orderResponse.orderId
    ElMessage.success('订单创建成功，准备跳转支付')
      
    // 发起支付
    await initiatePayment(orderId.value)
  } catch (error) {
    console.error('提交订单失败:', error)
    ElMessage.error('提交订单失败，请重试')
  } finally {
    submitting.value = false
  }
}

// 处理发起支付
const initiatePayment = async (orderId: string) => {
  try {
    loading.value = true
    
    const paymentResponse = await apiInitiatePayment(orderId)
    paymentFormHtml.value = paymentResponse.paymentForm
    
    // 创建临时div来渲染支付表单
    const tempDiv = document.createElement('div')
    tempDiv.innerHTML = paymentFormHtml.value
    document.body.appendChild(tempDiv)
    
    // 提交表单以重定向到支付宝
    const form = tempDiv.querySelector('form')
    if (form) {
      form.submit()
    } else {
      ElMessage.error('支付表单生成失败，请重试')
    }
    
    // 清理临时元素
    setTimeout(() => {
      document.body.removeChild(tempDiv)
    }, 1000)
  } catch (error) {
    console.error('发起支付失败:', error)
    ElMessage.error('发起支付失败，请重试')
  } finally {
    loading.value = false
  }
}

// 取消订单并返回购物车
const cancelOrder = () => {
  ElMessageBox.confirm('确定要取消结算吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    router.push('/cart')
  }).catch(() => {
    // 用户取消了对话框
  })
}

// 支付成功后清除购物车商品
const clearCartItems = () => {
  sessionStorage.removeItem('cartItems')
}

// 生命周期钩子
onMounted(() => {
  loadCartItems()
})
</script>

<template>
  <div class="order-container">
    <div class="order-header">
      <h1>订单结算</h1>
    </div>
    
    <el-card class="order-content" shadow="hover">
      <!-- Order items section -->
      <div class="order-items">
        <h2>已选商品</h2>
        <el-divider />
        
        <div v-if="cartItems.length === 0" class="empty-order">
          <p>暂无商品，请返回购物车选择商品</p>
          <el-button type="primary" @click="router.push('/cart')">返回购物车</el-button>
        </div>
        
        <div v-else>
          <el-table :data="cartItems" style="width: 100%">
            <el-table-column width="120">
              <template #default="scope">
                <el-image 
                  :src="scope.row.cover" 
                  fit="cover" 
                  class="product-image"
                />
              </template>
            </el-table-column>
            
            <el-table-column prop="title" label="商品名称">
              <template #default="scope">
                <div class="product-info">
                  <h3>{{ scope.row.title }}</h3>
                  <p class="description">{{ scope.row.description }}</p>
                </div>
              </template>
            </el-table-column>
            
            <el-table-column prop="price" label="单价" width="120">
              <template #default="scope">
                <span class="price">¥{{ scope.row.price }}</span>
              </template>
            </el-table-column>
            
            <el-table-column prop="amount" label="数量" width="120">
              <template #default="scope">
                <span class="quantity">{{ scope.row.amount }}</span>
              </template>
            </el-table-column>
            
            <el-table-column label="小计" width="120">
              <template #default="scope">
                <span class="subtotal">¥{{ (scope.row.price * scope.row.amount).toFixed(2) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
      
      <!-- Order summary section -->
      <div class="order-summary">
        <el-divider />
        <div class="summary-row">
          <span>商品总数:</span>
          <span>{{ totalItems }} 件</span>
        </div>
        <div class="summary-row total">
          <span>订单总金额:</span>
          <span class="total-amount">¥{{ totalAmount }}</span>
        </div>
      </div>
      
      <!-- Order actions section -->
      <div class="order-actions">
        <el-button 
          type="default" 
          size="large" 
          @click="cancelOrder" 
          :disabled="submitting || loading"
        >
          取消订单
        </el-button>
        
        <el-button 
          type="primary" 
          size="large" 
          @click="submitOrder" 
          :loading="submitting || loading"
          :disabled="cartItems.length === 0"
        >
          确认支付
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.order-container {
  max-width: 1200px;
  margin: 20px auto;
  padding: 0 20px;
  min-height: 100vh;
  background-image: url("../../assets/pexels-padrinan-19670.jpg");
  background-repeat: no-repeat;
  background-position: center center;
  background-size: cover;
}

.order-header {
  text-align: center;
  margin-bottom: 20px;
}

.order-header h1 {
  font-size: 28px;
  color: #303133;
  font-weight: bold;
}

.order-content {
  background-color: rgba(255, 255, 255, 0.8);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
}

.order-items {
  margin-bottom: 20px;
}

.order-items h2 {
  font-size: 20px;
  color: #303133;
  margin-bottom: 10px;
}

.product-image {
  width: 80px;
  height: 80px;
  border-radius: 4px;
  object-fit: cover;
}

.product-info h3 {
  font-size: 16px;
  color: #303133;
  margin: 0;
  margin-bottom: 6px;
}

.product-info .description {
  font-size: 14px;
  color: #606266;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.price, .quantity, .subtotal {
  font-size: 14px;
  color: #606266;
}

.subtotal {
  font-weight: bold;
  color: #f56c6c;
}

.order-summary {
  margin-top: 30px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 16px;
  color: #606266;
}

.summary-row.total {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
  margin-top: 10px;
}

.total-amount {
  color: #f56c6c;
  font-size: 24px;
}

.order-actions {
  display: flex;
  justify-content: flex-end;
  gap: 20px;
  margin-top: 30px;
}

.order-actions .el-button {
  min-width: 120px;
  padding: 12px 20px;
}

.empty-order {
  text-align: center;
  padding: 40px 0;
}

.empty-order p {
  font-size: 16px;
  color: #909399;
  margin-bottom: 20px;
}
</style>