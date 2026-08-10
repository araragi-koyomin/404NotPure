<script setup lang="ts">
import "../../style/fade.css"
import {computed, nextTick, onMounted, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {DArrowLeft, DArrowRight, Search, ShoppingCart} from '@element-plus/icons-vue'
import {router} from '../../router';
import {addToCart as apiAddToCart, getAllProducts, getProductStockpile, Product} from "../../api/product.ts";
import {AdvertisementInfo, getAdvertisements} from "../../api/advertisement.ts";
import {callTomatoAssistant} from "../../api/tools.ts";
import {parseBookCategory} from "../../utils";
import {describeRequestError} from "../../utils/safe-error.ts";

// Reactive states
const searchKeyword = ref('')
const activeCategory = ref<string | null>(null)
const showAssistant = ref(true)
const sidebarOpen = ref(true)
const input = ref('')
const addCartDialogVisible = ref(false)
const cartQuantity = ref(1)
const selectedProduct = ref<Product | null>(null)
const selectedStock = ref(0)
const messages = ref([
  { role: 'assistant', content: '你好，我是番茄助手 🍅 有什么想问我的吗？'}
])
const boxRef = ref<HTMLElement | null>(null)

const LOCAL_STORAGE_KEY = 'tomato-chat-messages'
const saveMessages = () => {
  localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(messages.value))
}

const sendMessage = async () => {
  const text = input.value.trim()
  if (!text) return

  messages.value.push({ role: 'user', content: text })
  saveMessages()
  input.value = ''

  try {
    const res = await callTomatoAssistant(text)
    messages.value.push({ role: 'assistant', content: res.answer })
    saveMessages()
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '出错啦，请稍后再试 🍅' })
    saveMessages()
  }

  await nextTick()
  if (boxRef.value) {
    boxRef.value.scrollTop = boxRef.value.scrollHeight
  }
}
// Static book categories
const bookCategories = [
  "文学小说", "历史传记", "哲学宗教", "艺术设计", "科学技术",
  "计算机与互联网", "医学与健康", "教育考试", "经济管理",
  "政治法律", "社会科学", "旅行与地理", "儿童读物"
]

// Product and advertisement list
const products = ref<Product[]>([])
const loading = ref(false)

const loadProducts = async () => {
  loading.value = true
  try {
    const res = await getAllProducts()
    if (res.data.code === '200') {
      products.value = res.data.data
    } else {
      ElMessage({
        message: res.data.code + res.data.msg,
        type: 'error',
        center: true,
      });
    }
  } catch (err) {
    ElMessage.error('加载商品发生异常')
  } finally {
    loading.value = false
  }
}

const advertisements = ref<AdvertisementInfo[]>([]);

const loadAdvertisements = async () => {
  try {
    advertisements.value = await getAdvertisements();
  } catch (error) {
    console.error('获取广告失败:', describeRequestError(error));
    ElMessage.error('加载广告失败，请稍后重试');
  }
};

// Helpers
const normalize = (str: string) => str.trim().toLowerCase()
const toggleSidebar = () => {
  sidebarOpen.value = !sidebarOpen.value
}
const selectCategory = (category: string) => {
  activeCategory.value = category
}

// Computed product list based on keyword
const filteredProducts = computed(() =>
    products.value.filter(p => {
      const matchKeyword = normalize(p.title).includes(normalize(searchKeyword.value))
      const matchCategory = !activeCategory.value || parseBookCategory(p.category || null) === activeCategory.value
      return matchKeyword && matchCategory
    })
)

// Handle add-to-cart event
const addToCart = async (product: Product) => {
  selectedProduct.value = product
  cartQuantity.value = 1

  try {
    const res = await getProductStockpile(product.id!)
    if (res.data.code === "200") {
      selectedStock.value = res.data.data.amount - res.data.data.frozen
      addCartDialogVisible.value = true
    } else {
      ElMessage.error("加载库存失败：" + res.data.msg)
    }
  } catch (error) {
    ElMessage.error("请求错误：" + error)
  }
}

const confirmAddToCart = async () => {
  if (!selectedProduct.value) return

  if (cartQuantity.value < 1) {
    ElMessage.warning('商品数量必须大于等于 1')
    return
  }

  if (cartQuantity.value > selectedStock.value) {
    ElMessage.warning(`库存不足，当前库存为 ${selectedStock.value} 件`)
    return
  }

  try {
    const res = await apiAddToCart(selectedProduct.value.id!, cartQuantity.value)
    if (res.data.code === "200") {
      ElMessage.success("成功加入购物车！")
      addCartDialogVisible.value = false
    } else {
      ElMessage.error("加入购物车失败：" + res.data.msg)
    }
  } catch (error) {
    ElMessage.error("系统错误：" + error)
  }
}

const goToProduct = (id: string) => {
  router.push(`/product/${id}`)
}
const handleCardClick = (product: Product) => {
  if (product.id) {
    goToProduct(product.id)
  } else {
    ElMessage.warning("商品 ID 缺失，无法跳转详情页")
  }
}

onMounted(() => {
  loadProducts()
  loadAdvertisements()
  const saved = localStorage.getItem(LOCAL_STORAGE_KEY)
  if (saved) {
    try {
      messages.value = JSON.parse(saved)
    } catch (e) {
      console.error("聊天记录恢复失败", describeRequestError(e))
    }
  }
})
</script>

<template>
  <el-container class="layout">
    <!-- Sidebar -->
    <el-aside v-show="sidebarOpen" width="200px" class="sidebar">
      <div class="sidebar-header">
        <div class="sidebar-toggle-icon" @click="toggleSidebar">
          <el-icon><DArrowLeft /></el-icon>
        </div>
        <h3 class="sidebar-title">
          <img src="../../assets/books.png" alt="book icon" class="title-icon" />
          分类浏览
        </h3>
      </div>

      <ul class="category-list">
        <li
            :class="{ active: !activeCategory }"
            @click="activeCategory = null"
        >
          全部分类
        </li>
        <li
            v-for="(cat, i) in bookCategories"
            :key="`${cat}-${i}`"
            :class="{ active: activeCategory === cat }"
            @click="selectCategory(cat)"
        >
          {{ cat }}
        </li>
      </ul>
    </el-aside>

    <!-- Main Content -->
    <el-main class="main">
      <!-- Sidebar Toggle When Collapsed -->
      <div class="sidebar-toggle-wrapper" v-show="!sidebarOpen">
        <div class="sidebar-toggle-icon" @click="toggleSidebar">
          <el-icon><DArrowRight /></el-icon>
        </div>
      </div>

      <!-- Search Input -->
      <div class="search-wrapper">
        <el-input
            v-model="searchKeyword"
            placeholder="Tomato Mall"
            class="search-input"
            clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <el-row class="logo-carousel-row" align="top" :gutter="20" wrap>

        <el-col :span="15" class="carousel-wrapper">
          <el-carousel height="280px" type="card">
            <el-carousel-item
                v-for="(item, index) in advertisements"
                :key="index"
            >
              <el-tooltip
                  effect="dark"
                  placement="bottom"
              >
                <template #content>
                  <div v-html="`标题：${item.title}<br/>内容：${item.content}<br/>产品ID：${item.productId}`"></div>
                </template>
                <img
                    :src="item.imgUrl"
                    class="carousel-image"
                    :alt="`${item.title}`"
                    @click="goToProduct(item.productId)"
                />
              </el-tooltip>
            </el-carousel-item>
          </el-carousel>
        </el-col>
        <el-col :span="1"></el-col>
        <el-col :span="7" class="assistant-column">
          <div class="assistant-wrapper">
            <div class="assistant-toggle" @click="showAssistant = !showAssistant">
              🍅 番茄助手
            </div>
            <transition name="fade">
              <div v-if="showAssistant" class="assistant-box">
                <div class="conversation" ref="boxRef">
                  <p v-for="(msg, i) in messages" :key="i" :class="msg.role">
                    {{ msg.content }}
                  </p>
                </div>
                <div class="input-area">
                  <input
                      v-model="input"
                      @keyup.enter="sendMessage"
                      placeholder="输入你的问题吧～"
                  />
                  <button @click="sendMessage">发送</button>
                </div>
              </div>
            </transition>
          </div>
        </el-col>
        <el-col :span="1"></el-col>
      </el-row>

      <!-- Product Listing -->
      <div class="product-display">
        <template v-if="filteredProducts.length">
          <el-space wrap :size="20" class="product-space">
            <el-card
                class="product-card"
                v-for="product in filteredProducts"
                :key="product.id"
                shadow="hover"
                @click="handleCardClick(product)"
            >
              <el-tag
                  class="card-tag"
                  type="success"
                  effect="dark"
                  size="small"
              >
                {{ parseBookCategory(product.category || null) }}
              </el-tag>
              <div class="product-image-wrapper">
                <el-image
                    :src="product.cover"
                    class="product-image"
                    fit="contain"
                    lazy
                />
              </div>
              <div class="product-title">{{ product.title }}</div>
              <div class="product-author">
                作者：{{ product.specifications?.find(spec => spec.item === '作者')?.value || '未知' }}
              </div>
              <el-rate
                  :model-value="product.rate / 2"
                  disabled
                  :max="5"
                  :allow-half="true"
                  class="product-rate"
              />

              <!-- 不跳转，只加购物车 -->
              <div class="product-footer">
                <el-button
                    type="danger"
                    :icon="ShoppingCart"
                    round
                    size="small"
                    @click.stop="addToCart(product)"
                >
                  加入购物车
                </el-button>
              </div>
            </el-card>

          </el-space>
        </template>
        <template v-else>
          <div class="empty-result">暂无匹配商品</div>
        </template>
      </div>
    </el-main>
  </el-container>

  <el-dialog
      v-model="addCartDialogVisible"
      title="加入购物车"
      width="30%"
  >
    <el-form label-width="100px">
      <el-form-item label="购买数量">
        <el-input-number
            v-model="cartQuantity"
            :min="1"
            :step="1"
        />
        <div style="margin-top: 6px; font-size: 12px; color: #999">
          当前库存：{{ selectedStock }} 件
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
    <span class="dialog-footer">
      <el-button @click="addCartDialogVisible = false">取消</el-button>
      <el-button type="success" @click="confirmAddToCart">确认</el-button>
    </span>
    </template>
  </el-dialog>
</template>

<style scoped>
.layout {
  width: 100%;
  min-height: 100vh;
  position: relative;
}

.sidebar {
  padding: 20px;
  background: #fff8f0;
  border-right: 2px solid #eee;
  box-shadow: 2px 0 10px rgba(0, 0, 0, 0.1);
}

.sidebar-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  gap: 8px;
}

.sidebar-title {
  font-size: 16px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
}

.title-icon {
  width: 20px;
  height: 20px;
  object-fit: contain;
}

.sidebar-toggle-icon {
  font-size: 18px;
  cursor: pointer;
  display: flex;
  color: #333;
  transition: all 0.2s ease;
}
.sidebar-toggle-icon:hover {
  color: #f44336;
  transform: scale(1.1);
}

.sidebar-toggle-wrapper {
  position: absolute;
  top: 20px;
  left: 20px;
  z-index: 1000;
}

.category-list li {
  padding: 6px 0;
  cursor: pointer;
  transition: color 0.2s;
}
.category-list li:hover {
  color: #f56c6c;
}
.category-list li.active {
  font-weight: bold;
  color: #f44336;
}

.main {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.search-wrapper {
  width: 100%;
  max-width: 1200px;
  display: flex;
  justify-content: flex-end;
  padding: 10px 20px;
  box-sizing: border-box;
}
.search-input {
  width: 300px;
}

.logo-carousel-row {
  width: 100%;
  max-width: 1200px;
  margin-bottom: 20px;
  display: flex;
  flex-wrap: wrap; /* ✅ 允许换行 */
  height: 280px; /* ✅ 新增：确保高度固定 */
  overflow: hidden; /* ✅ 防止撑开后挤压下方元素 */
}

.carousel-wrapper {
  height: 100%;
}

.carousel-image {
  width: 100%;
  height: 280px;
  object-fit: cover;
  border-radius: 8px;
  cursor: pointer;
}

.assistant-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  height: 100%;
}

.assistant-box {
  width: 100%;
  background: #ffeeee;
  border-radius: 12px;
  padding: 2%;
  box-shadow: 0 0.15em 0.5em rgba(0, 0, 0, 0.1);
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;           /* 固定高度 */
  max-height: 280px;      /* 控制对话框最大高度 */
  overflow: hidden;
}

.assistant-column {
  width: 100%;
  height: 100%; /* 或设置为 100% 并由父级撑高 */
  display: flex;
  flex-direction: column;
}

.conversation {
  width: 100%;
  flex: 1;
  overflow-y: auto;
  margin-bottom: 1%;
  padding-right: 4px;     /* 给滚动条一点空间 */
  scrollbar-width: thin;
  scrollbar-color: #ccc transparent;
}

.conversation::-webkit-scrollbar {
  width: 0.4em;
}
.conversation::-webkit-scrollbar-thumb {
  background-color: #ccc;
  border-radius: 0.3em;
}

p.assistant {
  background: #fff4f4;
  padding: 0.5em 0.8em;
  border-radius: 0.5em;
  margin-bottom: 0.5em;
}
p.user {
  background: #e0f7fa;
  padding: 0.5em 0.8em;
  border-radius: 0.5em;
  text-align: right;
  margin-bottom: 0.5em;
}

.input-area {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 1%;
  margin-top: 4px;
}
.input-area input {
  width: 100%;
  flex: 1;
  border: 1px solid #ddd;
  border-radius: 0.5em;
  padding: 0.5em;
  font-size: 1em;
}
.input-area button {
  flex-shrink: 0;
  padding: 0.5em 1em;
  background: #ff6347;
  color: #fff;
  border: none;
  border-radius: 0.5em;
  cursor: pointer;
}

.assistant-toggle {
  cursor: pointer;
  font-weight: bold;
  color: #f44336;
  background-color: #fff8f0;
  padding: 1em;
  border-radius: 12px;
  box-shadow: 0 0.2em 0.4em rgba(0, 0, 0, 0.08);
  margin-bottom: 1%;
  display: flex;
  align-items: center;
  height: 5%;
}

.product-display {
  width: 100%;
  max-width: 1200px;
  padding: 0 20px;
  margin-top: 30px;
  box-sizing: border-box;
}
.product-space {
  justify-content: center;
}

.product-card {
  background: linear-gradient(180deg, #ffffff 80%, #fef1f0 100%);
  padding: 12px;
  border-radius: 12px;
  width: 200px;
  height: 310px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  cursor: pointer;
  position: relative; /* 让标签定位基于卡片 */
}
.product-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.15);
}

.product-image-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 160px;
  margin-bottom: 8px;
  background-color: #f9f9f9;
  border-radius: 8px;
  overflow: hidden;
}


.product-image {
  object-fit: contain; /* ✅ 显示完整图且不拉伸 */
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #fff; /* ✅ 背景色填充，看起来更整洁 */
}


.product-title {
  display: flex;
  justify-content: center;
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 4px;
}

.product-author {
  display: flex;
  justify-content: center;
  font-size: 13px;
  color: #666;
  text-align: center;
  margin-bottom: 4px;
}

.product-rate {
  display: flex;
  justify-content: center;
  font-size: 14px;
  margin-bottom: 6px;
}

.product-footer {
  display: flex;
  justify-content: center;
}

.empty-result {
  text-align: center;
  color: #999;
  font-size: 18px;
  margin-top: 40px;
}
.card-tag {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 10;
  background-color: sandybrown;
}
</style>
