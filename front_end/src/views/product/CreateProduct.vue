<script setup lang="ts">
import { ref, watchEffect, onMounted } from 'vue';
import { ElMessage, UploadFile, UploadRawFile } from 'element-plus';
import { UploadFilled, Plus, Delete } from '@element-plus/icons-vue';
import { uploadImage } from '../../api/tools.ts';
import { createProduct, Product } from '../../api/product.ts';

// 分类选项
const categoryOptions = [
  { label: '文学小说', value: 'literature' },
  { label: '历史传记', value: 'biography' },
  { label: '哲学宗教', value: 'philosophy' },
  { label: '艺术设计', value: 'art' },
  { label: '科学技术', value: 'science' },
  { label: '计算机与互联网', value: 'computer' },
  { label: '医学与健康', value: 'medical' },
  { label: '教育考试', value: 'education' },
  { label: '经济管理', value: 'economics' },
  { label: '政治法律', value: 'politics' },
  { label: '社会科学', value: 'social' },
  { label: '旅行与地理', value: 'travel' },
  { label: '儿童读物', value: 'children' },
];

const fixedSpecifications = [
  { key: 'author', label: '作者', placeholder: '如 周志明' },
  { key: 'subtitle', label: '副标题', placeholder: '如 JVM高级特性与最佳实践' },
  { key: 'isbn', label: 'ISBN', placeholder: '如 9787111421900' },
  { key: 'binding', label: '装帧', placeholder: '如 平装' },
  { key: 'pages', label: '页数', placeholder: '如 540' },
  { key: 'publisher', label: '出版社', placeholder: '如 机械工业出版社' },
  { key: 'pub_date', label: '出版日期', placeholder: '如 2013-09-01' },
];

const specPlaceholderMap = Object.fromEntries(
    fixedSpecifications.map(spec => [spec.label, spec.placeholder])
);

// 表单数据
const product = ref<Product>({
  title: '',
  price: 0,
  rate: 0,
  description: '',
  cover: '',
  images: [],
  detail: '',
  specifications: [],
  category: '',
});

// 上传状态
const imageFileList = ref<UploadFile[]>([]);
const coverFileList = ref<UploadFile[]>([]);
const imgURLs = ref<string[]>([]);
const coverURL = ref('');
const loading = ref(false);
const rateStar = ref(0);

// 初始化默认规格
onMounted(() => {
  if (!product.value.specifications?.length) {
    product.value.specifications = fixedSpecifications.map(spec => ({
      item: spec.label,
      value: '',
    }));
  }
});

// 星级评分 -> 转换为 0~10 分
watchEffect(() => {
  rateStar.value = product.value.rate / 2;
});
const handleRateChange = (val: number) => {
  product.value.rate = Math.round(val * 2 * 10) / 10;
};

// 添加/删除规格项
const addSpecification = () => {
  product.value.specifications?.push({ item: '', value: '' });
};
const removeSpecification = (index: number) => {
  product.value.specifications?.splice(index, 1);
};

// 校验规格项
const validateSpecifications = () => {
  const specs = product.value.specifications ?? [];
  if (specs.length === 0) {
    ElMessage.warning('请填写至少一个规格信息');
    return false;
  }
  for (const spec of specs) {
    if (!spec.item.trim() || !spec.value.trim()) {
      ElMessage.warning(`请填写完整的规格项 "${spec.item || '未命名'}"`);
      return false;
    }
  }
  return true;
};

// 图片上传校验
const beforeUpload = (file: File) => {
  const isLt10M = file.size / 1024 / 1024 < 10;
  if (!isLt10M) ElMessage.error('上传的文件不能超过 10MB 哦~');
  return isLt10M;
};
const handleExceed = () => ElMessage.warning('当前限制选择 1 个文件');
const handleExceed_2 = () => ElMessage.warning('最多只能上传 5 张图片');
const handlePreview = (file: any) => window.open(file.url || file.response?.data || '', '_blank');

// 清空数据
const resetImgCache = () => {
  imgURLs.value = [];
  coverURL.value = '';
  imageFileList.value = [];
  coverFileList.value = [];
  product.value = {
    title: '',
    price: 0,
    rate: 0,
    description: '',
    cover: '',
    images: [],
    detail: '',
    specifications: product.value.specifications = fixedSpecifications.map(spec => ({
      item: spec.label,
      value: '',
    })),
    category: '',
  };
};

// 上传图片至后端并获取 URL
async function loopUpload() {
  imgURLs.value = [];
  for (const image of imageFileList.value) {
    const formData = new FormData();
    formData.append('file', image.raw as UploadRawFile);
    const res = await uploadImage(formData);
    imgURLs.value.push(res.data.data);
  }
  if (coverFileList.value.length > 0) {
    const formData = new FormData();
    formData.append('file', coverFileList.value[0].raw as UploadRawFile);
    const res = await uploadImage(formData);
    coverURL.value = res.data.data;
  }
}

// 表单提交
const handleSubmit = async () => {
  if (!product.value.title.trim()) return ElMessage.warning('请输入商品标题');
  if (product.value.price < 0) return ElMessage.warning('价格不能为负数');
  if (product.value.rate < 0 || product.value.rate > 10) return ElMessage.warning('评分需在 0 ~ 10 之间');
  if (!validateSpecifications()) return;

  loading.value = true;
  try {
    await loopUpload();
    const payload: Product = {
      ...product.value,
      images: [...imgURLs.value],
      cover: coverURL.value,
      specifications: (product.value.specifications ?? []).map(s => ({ item: s.item, value: s.value })),
    };
    const res = await createProduct(payload);
    if (res.code === 200) {
      ElMessage.success('创建成功！产品ID：' + res.data.id);
      resetImgCache();
    } else {
      ElMessage.error('创建失败：' + (res.msg || '未知错误'));
    }
  } catch (err) {
    ElMessage.error('创建失败，请稍后再试');
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <el-main class="main-container">
    <el-card shadow="hover" class="form-card">
      <div class="form-title">📦 创建新商品</div>
      <el-form label-width="100px" class="product-form">

        <el-form-item label="商品标题">
          <el-input v-model="product.title" placeholder="请输入商品名称" />
        </el-form-item>

        <el-form-item label="价格">
          <el-input
              v-model.number="product.price"
              type="number"
              :min="0"
              placeholder="￥"
          />
        </el-form-item>

        <el-form-item label="评分">
          <el-rate
              v-model="rateStar"
              :max="5"
              allow-half
              show-score
              :score-template="`${product.rate} 分`"
              @change="handleRateChange"
          />
        </el-form-item>

        <el-form-item label="分类">
          <el-select
              v-model="product.category"
              placeholder="请选择图书分类"
              filterable
              style="width: 100%"
          >
            <el-option
                v-for="option in categoryOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
            />
          </el-select>
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
          >
            <el-icon><upload-filled /></el-icon>
            <div class="el-upload__text">拖拽或点击上传封面图（仅1张）</div>
          </el-upload>
        </el-form-item>

        <el-form-item label="商品图片">
          <el-upload
              v-model:file-list="imageFileList"
              multiple
              :limit="5"
              :on-exceed="handleExceed_2"
              :on-preview="handlePreview"
              list-type="picture-card"
              drag
              :auto-upload="false"
              :before-upload="beforeUpload"
          >
            <el-icon><upload-filled /></el-icon>
            <div class="el-upload__text">上传多张图片（最多5张）</div>
          </el-upload>
        </el-form-item>

        <el-form-item label="描述">
          <el-input
              v-model="product.description"
              type="textarea"
              placeholder="简要描述产品特点"
              :rows="3"
          />
        </el-form-item>

        <el-form-item label="详情">
          <el-input
              v-model="product.detail"
              type="textarea"
              placeholder="详细介绍内容"
              :rows="5"
          />
        </el-form-item>

        <el-divider content-position="left">规格信息</el-divider>
        <!-- 循环展示规格项 -->
        <div
            v-for="(spec, index) in product.specifications"
            :key="index"
            class="spec-item"
        >
          <el-row :gutter="10">
            <el-col :span="6">
              <el-input v-model="spec.item" placeholder="规格名，如 作者/出版社" />
            </el-col>
            <el-col :span="14">
              <el-input v-model="spec.value" :placeholder="specPlaceholderMap[spec.item] || '请输入规格值'" />
            </el-col>
            <el-col :span="4" class="flex-center">
              <el-button
                  type="danger"
                  circle
                  size="small"
                  @click="removeSpecification(index)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </el-col>
          </el-row>
        </div>
        <el-row :gutter="10" class="add-spec-row">
          <el-col :span="6">
            <el-button
                type="primary"
                plain
                size="small"
                @click="addSpecification"
            >
              <el-icon><Plus /></el-icon>
              添加规格
            </el-button>
          </el-col>
        </el-row>
        <!-- 将按钮组移出 <el-form-item>，放到表单外部的 footer 区域 -->
        <div class="form-footer">
          <el-button type="primary" :loading="loading" @click="handleSubmit">提交</el-button>
          <el-button @click="resetImgCache">重置</el-button>
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

.spec-item {
  margin-bottom: 15px;
}

.add-spec-row {
  margin-top: 8px;
  margin-bottom: 15px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 30px;
  padding-right: 4px;
}

.el-upload-dragger {
  max-height: 140px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.el-upload__text {
  font-size: 14px;
  color: #777;
  line-height: 20px;
}

.upload-picture {
  margin-bottom: 16px;
}
.spec-label {
  display: flex;
  align-items: center;
  font-weight: 500;
}

.el-icon--upload {
  font-size: 28px;
  color: #409EFF;
  margin-bottom: 10px;
}

</style>
