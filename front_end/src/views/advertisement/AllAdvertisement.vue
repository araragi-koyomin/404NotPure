<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {ElMessage, ElTag} from 'element-plus';
import {router} from '../../router';
import {AdvertisementInfo, deleteAdvertisement, getAdvertisements} from '../../api/advertisement.ts';

const advertisements = ref<AdvertisementInfo[]>([]);

// 用来存每个广告的 popover 显示状态
const deleteVisibleMap = ref<{ [key: number]: boolean }>({});

const loadAdvertisements = async () => {
  try {
    advertisements.value = await getAdvertisements();
  } catch (error) {
    console.error('获取广告失败:', error);
    ElMessage.error('加载广告失败，请稍后重试');
  }
};

const confirmDelete = async (id: number) => {
  try {
    await deleteAdvertisement(id);
    ElMessage.success('删除成功');
    // 删除本地的广告数据
    advertisements.value = advertisements.value.filter(item => item.id !== id);
  } catch (error) {
    console.error('删除失败:', error);
    ElMessage.error('删除失败，请稍后再试');
  } finally {
    deleteVisibleMap.value[id] = false; // 关闭 Popover
  }
};

const openDelete = (id: number) => {
  deleteVisibleMap.value[id] = true;
};

const cancelDelete = (id: number) => {
  deleteVisibleMap.value[id] = false;
};

const updateAdvertisement = (ad: AdvertisementInfo) => {
  try {
    const pureAd = JSON.parse(JSON.stringify(ad)); // 彻底变成纯净对象
    sessionStorage.setItem('advertisement', JSON.stringify(pureAd));
    router.push('/updateAdvertisement');
  } catch (error) {
    console.error('保存广告到SessionStorage失败:', error);
    ElMessage.error('保存失败，请重试');
  }
};


onMounted(() => {
  loadAdvertisements();
});
</script>

<template>
  <el-main class="main-container">
    <el-card class="page-card" shadow="hover">
      <div class="page-title">📢 广告列表</div>

      <div v-for="item in advertisements" :key="item.id" class="ad-item">
        <div class="ad-left">
          <el-image
              class="ad-image"
              :src="item.imgUrl"
              :alt="item.title"
              fit="cover"
          />
        </div>

        <div class="ad-right">
          <div class="ad-title">{{ item.title }}</div>
          <div class="ad-content">{{ item.content }}</div>
          <div class="ad-product">
            商品ID：<el-tag type="info" size="small">{{ item.productId }}</el-tag>
          </div>
        </div>

        <div class="ad-actions">
          <el-popover
              placement="top"
              :visible="false"
          >
            <template #reference>
              <el-button  type="primary" class="action-btn" @click="updateAdvertisement(item)">
                更新
              </el-button>
            </template>
          </el-popover>

          <el-popover
              :visible="deleteVisibleMap[item.id!]"
              placement="bottom-start"
              :width="200"
          >
            <p>确定要删除这个广告吗？</p>
            <div style="text-align: right; margin: 0">
              <el-button size="small" type="primary" @click="confirmDelete(item.id!)">确认</el-button>
              <el-button size="small" text @click="cancelDelete(item.id!)">取消</el-button>
            </div>

            <template #reference>
              <el-button class="action-btn" type="danger" @click="openDelete(item.id!)">删除</el-button>
            </template>
          </el-popover>
        </div>
      </div>

      <div v-if="advertisements.length === 0" class="empty-tip">
        暂无广告信息
      </div>
    </el-card>
  </el-main>
</template>

<style scoped>
.main-container {
  padding: 30px;
  background: #f7f9fb;
  display: flex;
  justify-content: center;
}

.page-card {
  padding: 30px;
  border-radius: 12px;
  width: 100%;
  max-width: 1000px;
}

.page-title {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 25px;
  text-align: center;
}

.ad-item {
  display: flex;
  align-items: center;
  background: #fff;
  padding: 20px;
  margin-bottom: 20px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: transform 0.3s;
}

.ad-item:hover {
  transform: translateY(-3px);
}

.ad-left {
  flex-shrink: 0;
  margin-right: 20px;
}

.ad-image {
  width: 300px;
  height: 180px;
  object-fit: cover;
  border-radius: 10px;
}

.ad-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
  gap: 8px;
}

.ad-title {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.ad-content {
  font-size: 14px;
  color: #666;
}

.ad-product {
  font-size: 13px;
  color: #999;
}

.ad-actions {
  margin-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-btn {
  width: 100%;
  display: block;
  margin: 0 auto;
}

.empty-tip {
  text-align: center;
  color: #999;
  font-size: 16px;
  margin-top: 30px;
}
</style>
