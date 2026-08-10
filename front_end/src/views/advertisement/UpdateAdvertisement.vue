<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, UploadFile, UploadRawFile } from 'element-plus';
import { UploadFilled } from '@element-plus/icons-vue';
import { uploadImage } from '../../api/tools.ts';
import { updateAdvertisement, AdvertisementInfo } from '../../api/advertisement.ts';
import { router } from "../../router";
import { describeRequestError } from '../../utils/safe-error.ts';

// 表单和校验规则
const formRef = ref();
const rules = {
  title: [
    { required: true, message: '请输入广告标题', trigger: 'blur' },
  ],
  content: [
    { required: true, message: '请输入广告内容', trigger: 'blur' },
  ],
  productId: [
    { required: true, message: '请输入商品ID', trigger: 'blur' },
    {
      pattern: /^[0-9]+$/,
      message: '商品ID只能是纯数字',
      trigger: 'blur'
    }
  ]
};

// 表单数据
const storedAdvertisement = sessionStorage.getItem('advertisement');
const advertisement = ref<AdvertisementInfo>(
    storedAdvertisement
    ? JSON.parse(storedAdvertisement)
    : {
      id: '',
      title: '',
      content: '',
      imgUrl: '',
      productId: '',
    }
);

// 上传状态
const coverFileList = ref<UploadFile[]>([]);
const coverURL = ref('');
const loading = ref(false);

// 图片上传校验
const beforeUpload = (file: File) => {
  const isLt10M = file.size / 1024 / 1024 < 10;
  if (!isLt10M) ElMessage.error('上传的文件不能超过 10MB 哦~');
  return isLt10M;
};
const handleExceed = () => ElMessage.warning('当前限制选择 1 个文件');
const handlePreview = (file: any) => window.open(file.url || file.response?.data || '', '_blank');

// 上传图片至后端并获取 URL
async function loopUpload() {
  if (coverFileList.value.length > 0) {
    // 如果是新上传的封面
    if (coverFileList.value[0].raw) {
      const formData = new FormData();
      formData.append('file', coverFileList.value[0].raw as UploadRawFile);
      const res = await uploadImage(formData, 'ADVERTISEMENT');
      const url = res.data.data?.imageUrl || res.data.data;
      if (typeof url === 'string') {
        coverURL.value = url;
      } else {
        console.warn('封面图上传返回格式不合法');
      }
    }
    // 如果是保留了现有封面
    else if (coverFileList.value[0].url) {
      coverURL.value = coverFileList.value[0].url;
    }
  }
}

// 清空表单
const resetImgCache = () => {
  advertisement.value = {
    title: '',
    content: '',
    imgUrl: '',
    productId: ''
  };
  coverURL.value = '';
  coverFileList.value = [];
  formRef.value?.resetFields(); // 清除表单校验状态
};

// 表单提交
const handleSubmit = async () => {
  if (!formRef.value) return;

  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) {
      ElMessage.warning('请完善广告信息再提交');
      return;
    }

    loading.value = true;
    try {
      await loopUpload();

      const payload: AdvertisementInfo = {
        ...advertisement.value,
        imgUrl: coverURL.value,
      };
      await updateAdvertisement(payload);
      ElMessage.success('更新成功');
      resetImgCache();
      await router.push({ name: 'allProduct' });
    } catch (err) {
      console.error('更新广告失败', describeRequestError(err));
      ElMessage.error('更新失败，请稍后重试');
    } finally {
      loading.value = false;
    }
  });
};

onMounted(() => {
  if (advertisement.value.imgUrl) {
    coverURL.value = advertisement.value.imgUrl;
    coverFileList.value = [
      {
        name: '现有广告图片',
        url: advertisement.value.imgUrl,
      }as UploadFile
    ];
  }
})
</script>

<template>
  <el-main class="main-container">
    <el-card shadow="hover" class="form-card">
      <div class="form-title">📢 更新广告</div>
      <el-form
          ref="formRef"
          :model="advertisement"
          :rules="rules"
          label-width="100px"
          class="product-form"
      >
        <el-form-item label="产品标题" prop="title">
          <el-input v-model="advertisement.title" placeholder="请输入产品名称" />
        </el-form-item>

        <el-form-item label="封面图">
          <el-upload
              v-model:file-list="coverFileList"
              :limit="1"
              :on-exceed="handleExceed"
              list-type="picture"
              drag
              :auto-upload="false"
              :before-upload="beforeUpload"
              :on-preview="handlePreview"
          >
            <el-icon><upload-filled /></el-icon>
            <div class="el-upload__text">拖拽或点击上传封面图（仅1张）</div>
          </el-upload>
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
              v-model="advertisement.content"
              type="textarea"
              placeholder="描述广告内容"
              :rows="3"
          />
        </el-form-item>

        <el-form-item label="对应商品ID" prop="productId">
          <el-input
              v-model="advertisement.productId"
              type="textarea"
              placeholder="输入对应商品ID"
              :rows="1"
          />
        </el-form-item>

        <div class="form-footer">
          <el-button type="primary" :loading="loading" :disabled="loading" @click="handleSubmit">
            提交
          </el-button>
          <el-button :disabled="loading" @click="resetImgCache">
            重置
          </el-button>
        </div>
      </el-form>
    </el-card>
  </el-main>
</template>

<style scoped>
.main-container {
  padding: 40px 20px;
  background-color: #f7f9fb;
}

.form-card {
  max-width: 800px;
  margin: 0 auto;
  padding: 30px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
}

.form-title {
  font-size: 22px;
  font-weight: 600;
  color: #333;
  margin-bottom: 25px;
  text-align: center;
}

.product-form .el-form-item {
  flex: 1;
  margin-bottom: 20px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 30px;
  padding-right: 4px;
}

.el-upload__text {
  font-size: 14px;
  color: #777;
  line-height: 20px;
}
</style>
