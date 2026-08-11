<script setup lang="ts">
import "../../style/fade.css"
import {nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {DArrowLeft, DArrowRight, Search, ShoppingCart} from '@element-plus/icons-vue'
import {router} from '../../router';
import {
  addToCart as apiAddToCart,
  getProductPage,
  getProductStockpile,
  ProductSummary
} from "../../api/product.ts";
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
const selectedProduct = ref<ProductSummary | null>(null)
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
type CategoryOption = { label: string; codes: string }

const bookCategories: CategoryOption[] = [
  {label: "文学小说", codes: "literature"},
  {label: "历史传记", codes: "biography,history"},
  {label: "哲学宗教", codes: "philosophy,religion"},
  {label: "艺术设计", codes: "art,design"},
  {label: "科学技术", codes: "science"},
  {label: "计算机与互联网", codes: "computer,internet"},
  {label: "医学与健康", codes: "medical,health"},
  {label: "教育考试", codes: "education,exam"},
  {label: "经济管理", codes: "economics,management"},
  {label: "政治法律", codes: "politics,law"},
  {label: "社会科学", codes: "social"},
  {label: "旅行与地理", codes: "travel,geography"},
  {label: "儿童读物", codes: "children"}
]

const sortOptions = [
  {label: '默认排序', value: 'id,asc'},
  {label: '最新上架', value: 'id,desc'},
  {label: '评分最高', value: 'rate,desc'},
  {label: '价格从低到高', value: 'price,asc'},
  {label: '价格从高到低', value: 'price,desc'},
  {label: '书名排序', value: 'title,asc'}
]

const allowedPageSizes = [20, 40, 60]
const page = ref(1)
const pageSize = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)
const sort = ref('id,asc')
let searchTimer: ReturnType<typeof setTimeout> | undefined
let latestRequest = 0

const cancelScheduledKeywordSearch = () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = undefined
  }
}

// Product and advertisement list
const products = ref<ProductSummary[]>([])
const loading = ref(false)

const loadProducts = async () => {
  const requestId = ++latestRequest
  loading.value = true
  try {
    const categoryCodes = bookCategories.find(category => category.label === activeCategory.value)?.codes
    const res = await getProductPage({
      page: page.value,
      size: pageSize.value,
      keyword: searchKeyword.value.trim() || undefined,
      categories: categoryCodes,
      sort: sort.value
    })
    if (requestId !== latestRequest) return
    if (res?.data?.code === '200') {
      products.value = res.data.data.items
      totalElements.value = res.data.data.totalElements
      totalPages.value = res.data.data.totalPages
    } else {
      products.value = []
      totalElements.value = 0
      totalPages.value = 0
      ElMessage({
        message: (res?.data?.code || '') + (res?.data?.msg || '加载商品失败'),
        type: 'error',
        center: true,
      });
    }
  } catch (err) {
    if (requestId !== latestRequest) return
    products.value = []
    totalElements.value = 0
    totalPages.value = 0
    ElMessage.error('加载商品发生异常')
  } finally {
    if (requestId === latestRequest) loading.value = false
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
const toggleSidebar = () => {
  sidebarOpen.value = !sidebarOpen.value
}

const routeQueryValue = (value: unknown): string | undefined =>
    typeof value === 'string' ? value : undefined

const positiveInteger = (value: unknown, fallback: number): number => {
  const parsed = Number(routeQueryValue(value))
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

const currentQuery = (overrides: Record<string, string | number | undefined> = {}) => {
  const categoryCodes = bookCategories.find(category => category.label === activeCategory.value)?.codes
  return {
    page: page.value,
    size: pageSize.value,
    sort: sort.value,
    ...(searchKeyword.value.trim() ? {keyword: searchKeyword.value.trim()} : {}),
    ...(categoryCodes ? {categories: categoryCodes} : {}),
    ...overrides
  }
}

const navigateWithState = (mode: 'push' | 'replace', overrides: Record<string, string | number | undefined>) => {
  const query = currentQuery(overrides)
  Object.keys(query).forEach(key => {
    if (query[key as keyof typeof query] === undefined || query[key as keyof typeof query] === '') {
      delete query[key as keyof typeof query]
    }
  })
  return router[mode]({path: '/allProduct', query})
}

const selectCategory = (category: string | null) => {
  const codes = bookCategories.find(option => option.label === category)?.codes
  navigateWithState('push', {page: 1, categories: codes})
}

const submitKeyword = () => {
  cancelScheduledKeywordSearch()
  navigateWithState('replace', {page: 1, keyword: searchKeyword.value.trim() || undefined})
}

const scheduleKeywordSearch = () => {
  cancelScheduledKeywordSearch()
  searchTimer = setTimeout(submitKeyword, 400)
}

const changeSort = (value: string) => navigateWithState('push', {page: 1, sort: value})
const changePage = (value: number) => navigateWithState('push', {page: value})
const changePageSize = (value: number) => navigateWithState('push', {page: 1, size: value})

const applyRouteState = () => {
  cancelScheduledKeywordSearch()
  const query = router.currentRoute.value.query
  page.value = positiveInteger(query.page, 1)
  const requestedSize = positiveInteger(query.size, 20)
  pageSize.value = allowedPageSizes.includes(requestedSize) ? requestedSize : 20
  const requestedSort = routeQueryValue(query.sort)
  sort.value = sortOptions.some(option => option.value === requestedSort) ? requestedSort! : 'id,asc'
  searchKeyword.value = routeQueryValue(query.keyword) || ''
  const requestedCategories = routeQueryValue(query.categories)
  activeCategory.value = bookCategories.find(category => category.codes === requestedCategories)?.label || null
  loadProducts()
}

// Handle add-to-cart event
const addToCart = async (product: ProductSummary) => {
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

const goToProduct = (id: string | number) => {
  router.push(`/product/${id}`)
}
const handleCardClick = (product: ProductSummary) => {
  if (product.id) {
    goToProduct(product.id)
  } else {
    ElMessage.warning("商品 ID 缺失，无法跳转详情页")
  }
}

const formatPrice = (price: number | null) => price === null ? '价格待定' : `¥${price.toFixed(2)}`

watch(() => router.currentRoute.value.query, applyRouteState, {deep: true, immediate: true})

onBeforeUnmount(() => {
  cancelScheduledKeywordSearch()
  latestRequest++
})

onMounted(() => {
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
            @click="selectCategory(null)"
        >
          全部分类
        </li>
        <li
            v-for="cat in bookCategories"
            :key="cat.codes"
            :class="{ active: activeCategory === cat.label }"
            @click="selectCategory(cat.label)"
        >
          {{ cat.label }}
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
            @input="scheduleKeywordSearch"
            @keyup.enter="submitKeyword"
            @clear="submitKeyword"
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
      <div class="product-display" v-loading="loading">
        <div class="list-toolbar">
          <span class="result-count">共 {{ totalElements }} 本</span>
          <el-select
              :model-value="sort"
              class="sort-select"
              aria-label="商品排序"
              @change="changeSort"
          >
            <el-option
                v-for="option in sortOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
            />
          </el-select>
        </div>
        <template v-if="products.length">
          <el-space wrap :size="20" class="product-space">
            <el-card
                class="product-card"
                v-for="product in products"
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
                作者：{{ product.author || '未知' }}
              </div>
              <div class="product-price">{{ formatPrice(product.price) }}</div>
              <el-rate
                  :model-value="(product.rate || 0) / 2"
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
        <el-pagination
            v-if="totalPages > 0"
            class="product-pagination"
            background
            layout="total, sizes, prev, pager, next, jumper"
            :current-page="page"
            :page-size="pageSize"
            :page-sizes="allowedPageSizes"
            :total="totalElements"
            @current-change="changePage"
            @size-change="changePageSize"
        />
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

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.result-count {
  color: #666;
  font-size: 14px;
}

.sort-select {
  width: 160px;
}

.product-card {
  background: linear-gradient(180deg, #ffffff 80%, #fef1f0 100%);
  padding: 12px;
  border-radius: 12px;
  width: 200px;
  height: 335px;
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

.product-price {
  display: flex;
  justify-content: center;
  color: #e53935;
  font-size: 16px;
  font-weight: 600;
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

.product-pagination {
  justify-content: center;
  margin-top: 28px;
}
.card-tag {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 10;
  background-color: sandybrown;
}
</style>
