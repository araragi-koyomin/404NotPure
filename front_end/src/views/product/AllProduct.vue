<script setup lang="ts">
// Vue & Element Plus
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { DArrowLeft, DArrowRight, Search, ShoppingCart } from '@element-plus/icons-vue'
import { router } from '../../router';
import { getAllProducts, Product } from "../../api/product.ts";
import { parseBookCategory } from "../../utils";

// Assets
import logo from '../../assets/img.png'

// Reactive states
const searchKeyword = ref('')
const activeCategory = ref<string | null>(null)
const showAssistant = ref(true)
const sidebarOpen = ref(true)

// Static book categories
const bookCategories = [
  "文学小说", "历史传记", "哲学宗教", "艺术设计", "科学技术",
  "计算机与互联网", "医学与健康", "教育考试", "经济管理",
  "政治法律", "社会科学", "旅行与地理", "儿童读物"
]

// Product list
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
const addToCart = (product: Product) => {
  ElMessage.success(`${product.title} 已加入购物车！`)
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
          <img src="../../assets/img_1.png" alt="book icon" class="title-icon" />
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

      <!-- Logo + Assistant + Carousel -->
      <el-row class="logo-carousel-row" align="top" :gutter="20">
        <el-col :span="1"></el-col>

        <!-- Logo -->
        <el-col :span="4" class="logo-box">
          <router-link to="/homePage" class="logo-link">
            <img :src="logo" alt="logo" class="logo animated-logo" />
          </router-link>
        </el-col>

        <!-- Assistant -->
        <el-col :span="8" class="assistant-column">
          <div class="assistant-wrapper">
            <div class="assistant-toggle" @click="showAssistant = !showAssistant">
              🍅 番茄助手
            </div>
            <transition name="fade">
              <div v-if="showAssistant" class="assistant-box">
                <p>你好，我是番茄助手 🍅</p>
                <p>今天不如读一读《Educated》？</p>
                <p>或是来点轻松的《Gone Girl》～</p>
                <p>（可以添加更多内容来测试滚动效果）</p>
                <p>……</p>
              </div>
            </transition>
          </div>
        </el-col>

        <!-- Carousel -->
        <el-col :span="10" class="carousel-wrapper">
          <el-carousel height="200px" indicator-position="outside">
            <el-carousel-item>
              <img src="../../assets/pexels-padrinan-19670.jpg" class="carousel-image" alt="轮播图1" />
            </el-carousel-item>
            <el-carousel-item>
              <img src="../../assets/alipay.svg" class="carousel-image" alt="轮播图2" />
            </el-carousel-item>
          </el-carousel>
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
</template>

<style scoped>
.layout {
  width: 100%;
  min-height: 100vh;
  background: url("../../assets/pexels-andreea-ch-371539-1166644.jpg") no-repeat center center / cover;
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
  padding: 0 20px;
  margin-bottom: 20px;
}

.logo-box {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}

.logo-link {
  display: block;
}

.logo {
  width: 100%;
  height: 100%;
  object-fit: contain;
  animation: float 3s ease-in-out infinite;
  cursor: pointer;
}

.carousel-wrapper {
  height: 200px;
}

.carousel-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
  border-radius: 8px;
}

.assistant-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  position: relative;
  height: 200px;
}
.assistant-box {
  background: #ffeeee;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #ccc transparent;
}
.assistant-box::-webkit-scrollbar {
  width: 6px;
}
.assistant-box::-webkit-scrollbar-thumb {
  background-color: #ccc;
  border-radius: 4px;
}
.assistant-toggle {
  cursor: pointer;
  font-weight: bold;
  color: #f44336;
  background-color: #fff8f0;
  padding: 10px 16px;
  border-radius: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
  margin-bottom: 8px;
  line-height: 1.2;
  display: flex;
  align-items: center;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.4s ease, transform 0.4s ease;
}
.fade-enter-from {
  opacity: 0;
  transform: translateY(20px); /* 初始位置：下方 */
}
.fade-enter-to {
  opacity: 1;
  transform: translateY(0);
}
.fade-leave-from {
  opacity: 1;
  transform: translateY(0);
}
.fade-leave-to {
  opacity: 0;
  transform: translateY(20px); /* 离开时往下滑 */
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
</style>
