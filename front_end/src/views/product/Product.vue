<template>
  <el-container class="product-detail-container">
    <!-- 左侧信息面板 -->
    <el-aside width="33%" class="product-info-panel">
      <div class="product-header">
        <el-button @click="goBack" icon="el-icon-arrow-left" circle></el-button>
        <h1 class="product-title">{{ product.title }}</h1>
      </div>

      <!-- 产品评分 -->
      <div class="product-rating">
        <el-rate
            v-model="product.rate"
            disabled
            show-score
            text-color="#ff9900"
            score-template="{value} points"
        />
      </div>

      <!-- 产品描述 -->
      <div class="product-description">
        <p>{{ product.description }}</p>
      </div>

      <!-- 产品价格 -->
      <div class="product-price">
        <span>价格: ¥{{ product.price }}</span>
      </div>

      <!-- 产品库存 -->
      <div class="product-stock">
        <span>当前库存: {{ stockpile.amount }} 件</span>
        <span>冻结库存: {{ stockpile.frozen }} 件</span>
      </div>

      <!-- 产品规格 -->
      <div class="product-specifications">
        <h3>产品规格</h3>
        <el-descriptions :column="1" border>
          <el-descriptions-item v-for="spec in product.specifications" :key="spec.id" :label="spec.item">
            {{ spec.value }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 操作按钮区域 -->
      <div class="product-actions">
        <!-- 消费者视角按钮 -->
        <el-button
            v-if="userRole === 'USER'"
            type="primary"
            @click="createOrder"
        >创建订单</el-button>

        <!-- 管理员视角按钮 -->
        <template v-if="userRole === 'ADMIN'">
          <el-button type="primary" @click="createOrder">创建订单</el-button>
          <el-button type="warning" @click="updateProductInfo">更改产品信息</el-button>
          <el-button type="danger" @click="confirmDeleteProduct">删除产品</el-button>
          <el-button type="info" @click="showAdjustStockDialog">调整库存</el-button>
        </template>
      </div>
    </el-aside>

    <!-- 右侧内容区域 -->
    <el-main class="product-content-panel">
      <!-- 产品图片轮播 -->
      <div class="product-images-carousel">
        <el-carousel height="400px" indicator-position="outside" arrow="always">
          <el-carousel-item v-for="(image, index) in product.contentImages" :key="index">
            <el-image :src="image" fit="contain" class="carousel-image" />
          </el-carousel-item>
        </el-carousel>
      </div>

      <!-- 评论区域 -->
      <div class="product-comments-section">
        <div class="comments-header">
          <h2>用户评论</h2>
          <el-button
              v-if="userRole === 'USER'"
              type="primary"
              size="small"
              @click="showCreateCommentDialog"
          >创建评论</el-button>
        </div>

        <div class="comments-list">
          <el-card v-for="comment in comments" :key="comment.id" class="comment-item">
            <div class="comment-header">
              <span class="comment-user">{{ comment.userName }}</span>
              <span class="comment-date">{{ formatDate(comment.createTime) }}</span>
              <el-rate
                  v-model="comment.rate"
                  disabled
                  text-color="#ff9900"
              />
            </div>
            <div class="comment-content">
              <p>{{ comment.content }}</p>
            </div>
            <div class="comment-actions">
              <el-button
                  v-if="userRole === 'ADMIN' || (userRole === 'USER' && comment.userId === userId)"
                  type="danger"
                  size="small"
                  @click="confirmDeleteComment(comment.id)"
              >删除评论</el-button>
            </div>
          </el-card>
        </div>
      </div>
    </el-main>
  </el-container>

  <!-- 删除产品确认对话框 -->
  <el-dialog
      title="确认删除"
      v-model="deleteProductDialogVisible"
      width="30%"
  >
    <span>确定要删除该产品吗？此操作不可逆。</span>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="deleteProductDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="deleteProduct">确认删除</el-button>
      </span>
    </template>
  </el-dialog>

  <!-- 调整库存对话框 -->
  <el-dialog
      title="调整库存"
      v-model="adjustStockDialogVisible"
      width="30%"
  >
    <el-form :model="stockForm" label-width="100px">
      <el-form-item label="当前库存">
        <el-input-number v-model="stockForm.amount" :min="0" :step="1"></el-input-number>
      </el-form-item>
      <el-form-item label="冻结库存">
        <el-input-number v-model="stockForm.frozen" :min="0" :step="1"></el-input-number>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="adjustStockDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="updateStock">确认</el-button>
      </span>
    </template>
  </el-dialog>

  <!-- 创建评论对话框 -->
  <el-dialog
      title="创建评论"
      v-model="createCommentDialogVisible"
      width="40%"
  >
    <el-form :model="commentForm" label-width="100px">
      <el-form-item label="评分">
        <el-rate v-model="commentForm.rate" :max="5" show-score></el-rate>
      </el-form-item>
      <el-form-item label="评论内容">
        <el-input
            v-model="commentForm.content"
            type="textarea"
            :rows="4"
            placeholder="请输入您的评论"
        ></el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="createCommentDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitComment">提交评论</el-button>
      </span>
    </template>
  </el-dialog>

  <!-- 删除评论确认对话框 -->
  <el-dialog
      title="确认删除评论"
      v-model="deleteCommentDialogVisible"
      width="30%"
  >
    <span>确定要删除这条评论吗？此操作不可逆。</span>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="deleteCommentDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="deleteComment">确认删除</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  getProductById,
  getProductStockpile,
  updateProductStockpile,
  deleteProduct as apiDeleteProduct,
  createComment as apiCreateComment,
  deleteComment as apiDeleteComment,
  Product,
  Stockpile,
  Comment
} from '../../api/product';

const route = useRoute();
const router = useRouter();
const productId = computed(() => route.params.id as string);

// 用户信息
const userRole = ref(localStorage.getItem('userRole') || sessionStorage.getItem('role') || 'USER');
const userId = ref(localStorage.getItem('userId') || sessionStorage.getItem('userId') || '');

// 产品数据
const product = ref<Product>({
  id: '',
  title: '',
  price: 0,
  rate: 0,
  description: '',
  cover: '',
  contentImages: [],
  detail: '',
  specifications: []
});

// 库存数据
const stockpile = ref<Stockpile>({
  productId: '',
  amount: 0,
  frozen: 0
});

// 评论数据
const comments = ref<Comment[]>([]);

// 对话框状态
const deleteProductDialogVisible = ref(false);
const adjustStockDialogVisible = ref(false);
const createCommentDialogVisible = ref(false);
const deleteCommentDialogVisible = ref(false);

// 表单数据
const stockForm = ref({
  amount: 0,
  frozen: 0
});
const commentForm = ref({
  rate: 5,
  content: ''
});
const currentCommentId = ref('');

// 加载产品数据
async function loadProductData() {
  try {
    const response = await getProductById(productId.value);
    if (response.data.code === 200) {
      product.value = response.data.data;

      // 如果产品没有图片数组，将cover图片作为第一张
      if (!product.value.contentImages || product.value.contentImages.length === 0) {
        product.value.contentImages = [product.value.cover!];
      }

      // 加载评论数据（假设评论在产品数据中）
      if (response.data.data.comments) {
        comments.value = response.data.data.comments;
      }

      // 加载库存数据
      loadStockpileData();
    } else {
      ElMessage.error('加载产品信息失败：' + response.data.msg);
    }
  } catch (error) {
    ElMessage.error('系统错误：' + error);
  }
}

// 加载库存数据
async function loadStockpileData() {
  try {
    const response = await getProductStockpile(productId.value);
    if (response.data.code === 200) {
      stockpile.value = response.data.data;
      // 初始化表单数据
      stockForm.value.amount = stockpile.value.amount;
      stockForm.value.frozen = stockpile.value.frozen;
    } else {
      ElMessage.error('加载库存信息失败：' + response.data.msg);
    }
  } catch (error) {
    ElMessage.error('系统错误：' + error);
  }
}

// 返回上一页
function goBack() {
  router.back();
}

// 创建订单
function createOrder() {
  router.push({
    path: '/order',
    query: {
      product: JSON.stringify(product.value)
    }
  });
}

// 更新产品信息
function updateProductInfo() {
  router.push({
    path: '/updateProduct',
    query: {
      product: JSON.stringify(product.value)
    }
  });
}

// 确认删除产品
function confirmDeleteProduct() {
  deleteProductDialogVisible.value = true;
}

// 删除产品
async function deleteProduct() {
  try {
    const response = await apiDeleteProduct(productId.value);
    if (response.data.code === 200) {
      ElMessage.success('删除产品成功');
      deleteProductDialogVisible.value = false;
      router.push('/product'); // 假设产品列表页路径
    } else {
      ElMessage.error('删除产品失败：' + response.data.msg);
    }
  } catch (error) {
    ElMessage.error('系统错误：' + error);
  }
}

// 显示调整库存对话框
function showAdjustStockDialog() {
  stockForm.value.amount = stockpile.value.amount;
  stockForm.value.frozen = stockpile.value.frozen;
  adjustStockDialogVisible.value = true;
}

// 更新库存
async function updateStock() {
  try {
    const newStockpile: Stockpile = {
      productId: productId.value,
      amount: stockForm.value.amount,
      frozen: stockForm.value.frozen
    };

    const response = await updateProductStockpile(productId.value, newStockpile);

    if (response.data.code === 200) {
      ElMessage.success('调整库存成功');
      adjustStockDialogVisible.value = false;
      // Reload the entire product data to refresh the page
      loadProductData();
    } else {
      ElMessage.error('调整库存失败：' + response.data.msg);
    }
  } catch (error) {
    ElMessage.error('系统错误：' + error);
  }
}

// 显示创建评论对话框
function showCreateCommentDialog() {
  commentForm.value = {
    rate: 5,
    content: ''
  };
  createCommentDialogVisible.value = true;
}

// 提交评论
async function submitComment() {
  if (!commentForm.value.content.trim()) {
    ElMessage.warning('评论内容不能为空');
    return;
  }

  try {
    const newComment = {
      userId: userId.value,
      userName: 'Current User', // 假设从用户状态获取，可以根据实际情况调整
      productId: productId.value,
      content: commentForm.value.content,
      rate: commentForm.value.rate
    };

    const response = await apiCreateComment(newComment);
    if (response.data.code === 200) {
      ElMessage.success('评论提交成功');
      createCommentDialogVisible.value = false;
      // 重新加载评论数据或将新评论添加到列表
      loadProductData();
    } else {
      ElMessage.error('评论提交失败：' + response.data.msg);
    }
  } catch (error) {
    ElMessage.error('系统错误：' + error);
  }
}

// 确认删除评论
function confirmDeleteComment(commentId: string) {
  currentCommentId.value = commentId;
  deleteCommentDialogVisible.value = true;
}

// 删除评论
async function deleteComment() {
  try {
    const response = await apiDeleteComment(currentCommentId.value);
    if (response.data.code === 200) {
      ElMessage.success('删除评论成功');
      deleteCommentDialogVisible.value = false;
      // 从列表中移除该评论
      comments.value = comments.value.filter(item => item.id !== currentCommentId.value);
    } else {
      ElMessage.error('删除评论失败：' + response.data.msg);
    }
  } catch (error) {
    ElMessage.error('系统错误：' + error);
  }
}

// 格式化日期
function formatDate(dateString: string) {
  const date = new Date(dateString);
  return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
}

onMounted(() => {
  loadProductData();
});
</script>

<style scoped>
.product-detail-container {
  height: 100vh;
  width: 100%;
}

.product-info-panel {
  padding: 20px;
  border-right: 1px solid #eee;
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.product-header {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.product-title {
  margin-left: 15px;
  font-size: 22px;
}

.product-rating {
  margin-bottom: 20px;
}

.product-description {
  margin-bottom: 20px;
  color: #666;
}

.product-price {
  font-size: 24px;
  color: #f56c6c;
  margin-bottom: 15px;
}

.product-stock {
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
}

.product-specifications {
  margin-bottom: 30px;
}

.product-actions {
  margin-top: auto;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.product-content-panel {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.product-images-carousel {
  flex: 2;
  margin-bottom: 20px;
}

.carousel-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.product-comments-section {
  flex: 1;
  overflow-y: auto;
}

.comments-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.comments-list {
  max-height: 100%;
  overflow-y: auto;
}

.comment-item {
  margin-bottom: 15px;
}

.comment-header {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.comment-user {
  font-weight: bold;
  margin-right: 10px;
}

.comment-date {
  color: #999;
  margin-right: 10px;
}

.comment-content {
  margin-bottom: 10px;
}

.comment-actions {
  display: flex;
  justify-content: flex-end;
}

/* Make the carousel arrows more visible */
:deep(.el-carousel__arrow) {
  background-color: rgba(31, 45, 61, 0.5);
  width: 40px;
  height: 40px;
}
</style>