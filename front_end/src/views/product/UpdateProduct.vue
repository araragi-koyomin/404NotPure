<script setup lang="ts">
import { ref, watchEffect, onMounted, computed } from 'vue';
import { ElMessage, UploadFile, UploadRawFile } from 'element-plus';
import { UploadFilled, Plus, Delete } from '@element-plus/icons-vue';
import { uploadImage } from '../../api/tools.ts';
import { updateProduct, Product } from '../../api/product.ts';

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

// 固定规格项（作为默认规格项）
const fixedSpecifications = [
  { key: 'author', label: '作者', placeholder: '如 周志明' },
  { key: 'subtitle', label: '副标题', placeholder: '如 JVM高级特性与最佳实践' },
  { key: 'isbn', label: 'ISBN', placeholder: '如 9787111421900' },
  { key: 'binding', label: '装帧', placeholder: '如 平装' },
  { key: 'pages', label: '页数', placeholder: '如 540' },
  { key: 'publisher', label: '出版社', placeholder: '如 机械工业出版社' },
  { key: 'pub_date', label: '出版日期', placeholder: '如 2013-09-01' },
];

const currentCategoryLabel = computed(() => {
  if (!product.value.category) return '请选择图书分类';
  const found = categoryOptions.find(opt => opt.value === product.value.category);
  return found?.label || '无效分类';
});

// 从 sessionStorage 获取当前待更新的产品数据
const storedProductStr = sessionStorage.getItem('product');
const product = ref<Product>(
    storedProductStr
        ? JSON.parse(storedProductStr)
        : {
          title: '',
          price: 0,
          rate: 0,
          description: '',
          cover: '',
          contentImages: [],
          detail: '',
          specifications: [],
          category: '',
        }
);

// 动态生成规格提示文本映射，使用实际产品值作为提示
const specPlaceholderMap = ref<Record<string, string>>({});

// 上传状态
const imageFileList = ref<UploadFile[]>([]);
const coverFileList = ref<UploadFile[]>([]);
const imgURLs = ref<string[]>([]);
const coverURL = ref('');
const loading = ref(false);
const rateStar = ref(0);

// 根据当前产品数据初始化页面
onMounted(() => {
  // 如果规格信息为空，则使用默认规格项填充
  if (!product.value.specifications?.length) {
    product.value.specifications = fixedSpecifications.map(spec => ({
      item: spec.label,
      value: '',
    }));
  }

  // 生成以当前产品规格值为提示的占位符映射
  updateSpecPlaceholderMap();

  // 初始化封面图
  if (product.value.cover) {
    coverURL.value = product.value.cover;
    coverFileList.value = [
      {
        name: '现有封面',
        url: product.value.cover,
      }as UploadFile     // 类型断言确保符合类型
    ];
  }

  // 初始化内容图片
  if (product.value.contentImages && product.value.contentImages.length > 0) {
    imgURLs.value = product.value.contentImages.map(img => img.imageUrl);
    imageFileList.value = product.value.contentImages.map((img, index) => ({
      name: `现有图片${index + 1}`,
      url: img.imageUrl,
    }as UploadFile ));    // 类型断言确保符合类型
  }
});

// 更新规格提示映射
const updateSpecPlaceholderMap = () => {
  const map: Record<string, string> = {};
  if (product.value.specifications && product.value.specifications.length > 0) {
    product.value.specifications.forEach(spec => {
      if (spec.item && spec.value) {
        map[spec.item] = spec.value;
      }
    });
  }

  // 将没有值的项使用默认提示
  fixedSpecifications.forEach(spec => {
    if (!map[spec.label]) {
      map[spec.label] = spec.placeholder;
    }
  });

  specPlaceholderMap.value = map;
};

// 根据当前评分转换成 0~5 星显示
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

// 删除已上传的图片
const handleRemove = (file: UploadFile) => {
  // 从 imgURLs 中移除
  const index = imgURLs.value.indexOf(file.url || '');
  if (index !== -1) {
    imgURLs.value.splice(index, 1);
  }
};

// 删除已上传的封面图
const handleCoverRemove = () => {
  coverURL.value = '';
};

// 清空图片上传缓存（注意：此处不清空 product 数据，以便保留当前未更新的值）
const resetImgCache = () => {
  imgURLs.value = [];
  coverURL.value = '';
  imageFileList.value = [];
  coverFileList.value = [];

  // 重新从原始产品数据初始化图片
  if (product.value.cover) {
    coverURL.value = product.value.cover;
    coverFileList.value = [
      {
        name: '现有封面',
        url: product.value.cover,
      }as UploadFile     // 类型断言确保符合类型
    ];
  }

  if (product.value.contentImages && product.value.contentImages.length > 0) {
    imgURLs.value = product.value.contentImages.map(img => img.imageUrl);
    imageFileList.value = product.value.contentImages.map((img, index) => ({
      name: `现有图片${index + 1}`,
      url: img.imageUrl,
    }as UploadFile ));    // 类型断言确保符合类型
  }
};

// 上传图片到后端并获取 URL
async function loopUpload() {
  // 处理内容图片上传
  for (const image of imageFileList.value) {
    // 如果是新上传的图片（有raw属性）
    if (image.raw) {
      const formData = new FormData();
      formData.append('file', image.raw as UploadRawFile);
      const res = await uploadImage(formData);
      const url = res.data.data?.imageUrl || res.data.data;
      if (typeof url === 'string') {
        imgURLs.value.push(url);
      } else {
        console.warn('上传返回格式不合法:', res.data.data);
      }
    }
    // 如果是已有的图片（有url属性但没有raw属性）
    else if (image.url && !imgURLs.value.includes(image.url)) {
      imgURLs.value.push(image.url);
    }
  }

  // 处理封面图上传
  if (coverFileList.value.length > 0) {
    // 如果是新上传的封面
    if (coverFileList.value[0].raw) {
      const formData = new FormData();
      formData.append('file', coverFileList.value[0].raw as UploadRawFile);
      const res = await uploadImage(formData);
      const url = res.data.data?.imageUrl || res.data.data;
      if (typeof url === 'string') {
        coverURL.value = url;
      } else {
        console.warn('封面图上传返回格式不合法:', res.data.data);
      }
    }
    // 如果是保留了现有封面
    else if (coverFileList.value[0].url) {
      coverURL.value = coverFileList.value[0].url;
    }
  }
}

// 表单提交，调用更新接口
const handleSubmit = async () => {
  if (!product.value.title.trim()) return ElMessage.warning('请输入商品标题');
  if (product.value.price < 0) return ElMessage.warning('价格不能为负数');
  if (product.value.rate < 0 || product.value.rate > 10) return ElMessage.warning('评分需在 0 ~ 10 之间');
  if (!validateSpecifications()) return;

  loading.value = true;

  try {
    // 重置 imgURLs，以便只包含当前要保留的图片
    imgURLs.value = [];
    await loopUpload();

    const payload: Product = {
      ...product.value,
      contentImages: imgURLs.value.map(url => ({ imageUrl: url })),
      cover: coverURL.value,
      specifications: (product.value.specifications ?? []).map(s => ({
        id: s.id,
        item: s.item,
        value: s.value,
        productId: s.productId
      })),
    };

    const res = await updateProduct(payload);
    if (res.data.code === '200') {
      ElMessage.success('更新成功！产品ID：' + res.data.data.productId);
      // 根据需要，可清理 sessionStorage 中存储的 product 数据
      // sessionStorage.removeItem('product');
    } else {
      ElMessage.error('更新失败：' + (res.data.msg || '未知错误'));
    }
  } catch (err) {
    ElMessage.error('更新失败，请稍后再试');
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <el-main class="main-container">
    <el-card shadow="hover" class="form-card">
      <div class="form-title">📦 更新商品</div>
      <el-form label-width="100px" class="product-form">
        <el-form-item label="商品标题">
          <el-input v-model="product.title" placeholder="商品标题" />
        </el-form-item>

        <el-form-item label="价格">
          <el-input v-model.number="product.price" type="number" :min="0" placeholder="￥" />
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
          <el-select v-model="product.category" 
          :placeholder="currentCategoryLabel" 
          filterable style="width: 100%">
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
              :on-remove="handleCoverRemove"
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
              :on-remove="handleRemove"
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
        <div v-for="(spec, index) in product.specifications" :key="index" class="spec-item">
          <el-row :gutter="11">
            <el-col :span="3" class="flex-center">
<!--              <el-button type="danger" circle size="small" @click="removeSpecification(index)">-->
<!--                <el-icon><Delete /></el-icon>-->
<!--              </el-button>-->
            </el-col>
            <el-col :span="7">
              <el-input v-model="spec.item" placeholder="规格名，如 作者/出版社" />
            </el-col>
            <el-col :span="13">
              <el-input
                  v-model="spec.value"
                  :placeholder="specPlaceholderMap[spec.item] || '请输入规格值'"
              />
            </el-col>
          </el-row>
        </div>

        <el-row :gutter="10" class="add-spec-row">
          <el-col :span="3" class="flex-center">
            <!--              <el-button type="danger" circle size="small" @click="removeSpecification(index)">-->
            <!--                <el-icon><Delete /></el-icon>-->
            <!--              </el-button>-->
          </el-col>
          <el-col :span="6">
            <el-button type="primary" plain size="small" @click="addSpecification">
              <el-icon><Plus /></el-icon>
              添加规格
            </el-button>
          </el-col>
        </el-row>

        <div class="form-footer">
          <el-button type="primary" :loading="loading" @click="handleSubmit">更新</el-button>
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

.flex-center {
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>