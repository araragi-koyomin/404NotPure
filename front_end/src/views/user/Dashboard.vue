<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue';
import { userInfo, userInfoUpdate } from "../../api/user.ts";
import { parseRole } from "../../utils";
import { router } from "../../router";
import { UploadFilled, InfoFilled } from "@element-plus/icons-vue";
import {uploadImage} from "../../api/tools.ts";

const username = sessionStorage.getItem("username") || ''
const name = ref(sessionStorage.getItem("name")|| '')
const avatar = ref(sessionStorage.getItem("avatar") || '')
const role = ref(sessionStorage.getItem("role") || '')
const telephone = ref(sessionStorage.getItem("telephone") || '')
const email = ref(sessionStorage.getItem("email") || '')
const location = ref(sessionStorage.getItem("location") || '')

const newPassword = ref('');
const newName = ref('');
const newRole = ref('');
const newTelephone = ref('');
const newEmail = ref('');
const newLocation = ref('');

const confirmPassword = ref('');
const displayInfoCard = ref('0');

const hasNewPasswordInput = computed(() => newPassword.value != '');
const hasNewNameInput = computed(() => newName.value != '');
const hasNewAvatarInput = computed(() => imageFileList.value.length > 0);
const hasNewRoleInput = computed(() => newRole.value != '');
const hasNewTelephoneInput = computed(() => newTelephone.value != '');
const hasNewEmailInput = computed(() => newEmail.value != '');
const hasNewLocationInput = computed(() => newLocation.value != '');
const hasConfirmPasswordInput = computed(() => confirmPassword.value != '');
const isPasswordIdentical = computed(() => newPassword.value == confirmPassword.value);

const chinaMobileRegex = /^1(3[0-9]|4[579]|5[0-35-9]|6[2567]|7[0-8]|8[0-9]|9[189])\d{8}$/
const telLegal = computed(() => chinaMobileRegex.test(newTelephone.value))
const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
const emailLegal = computed(() => emailRegex.test(newEmail.value))

const hasAnyFieldChanged = computed(() =>
    hasNewNameInput.value || hasNewRoleInput.value ||
    (hasNewEmailInput.value && emailLegal.value) ||
    (hasNewTelephoneInput.value && telLegal.value) ||
    hasNewLocationInput.value
);

const changeDisabled = computed(() => {
  if (hasNewPasswordInput.value) {
    return true; // 有密码就禁用按钮（防止误提交）
  }
  return !hasAnyFieldChanged.value; // 没填其他任何东西 → 禁用
});

const changePasswordDisabled = computed(() => {
  return (
      !hasNewPasswordInput.value ||             // 没有新密码输入
      !isPasswordIdentical.value ||             // 密码不一致
      hasAnyFieldChanged.value               // 有其他字段被修改
  );
})

const changeAvatarDisabled = computed(() => {
  return !hasNewAvatarInput.value;
})

function getUserInfo() {
  userInfo(username).then(res => {
    sessionStorage.setItem('name', res.data.data.name);
    sessionStorage.setItem('role', res.data.data.role);
    sessionStorage.setItem('avatar', res.data.data.avatar);
    sessionStorage.setItem('telephone', res.data.data.telephone);
    sessionStorage.setItem('email', res.data.data.email);
    sessionStorage.setItem('location', res.data.data.location);
    name.value = sessionStorage.getItem("name") || '';
    role.value = sessionStorage.getItem("role") || '';
    avatar.value = sessionStorage.getItem("avatar") || '';
    telephone.value = sessionStorage.getItem("telephone") || '';
    email.value = sessionStorage.getItem("email") || '';
    location.value = sessionStorage.getItem("location") || '';
  });
}

function updateUserInfo() {
  userInfoUpdate({
    username: username,
    password: undefined,
    name: newName.value === '' ? undefined : newName.value,
    avatar: imgURLs.value[0] === '' ? undefined : imgURLs.value[0],
    role: newRole.value === '' ? undefined : newRole.value,
    telephone: newTelephone.value === '' ? undefined : newTelephone.value,
    email: newEmail.value === '' ? undefined : newEmail.value,
    location: newLocation.value === '' ? undefined : newLocation.value,
  }).then(res => {
    if (res.data.code === '200') {
      ElMessage({
        customClass: 'customMessage',
        type: 'success',
        message: '更新成功！',
      });
      getUserInfo();
      newPassword.value = '';
      newName.value = '';
      imgURLs.value = [];
      imageFileList.value = [];
      newRole.value = '';
      newTelephone.value = '';
      newEmail.value = '';
      newLocation.value = '';
      confirmPassword.value = '';
    } else {
      imgURLs.value = [];
      ElMessage({
        customClass: 'customMessage',
        type: 'error',
        message: res.data.code + res.data.msg,
      });
    }
  }).finally(() => {
    loading.value = false;
  });
}

function updatePassword() {
  userInfoUpdate({
    username: username,
    password: newPassword.value === '' ? undefined : newPassword.value,
  }).then(res => {
    if (res.data.code === '200') {
      newPassword.value = '';
      confirmPassword.value = '';
      ElMessageBox.alert(
          `请重新登录`,
          '修改成功',
          {
            customClass: "customDialog",
            confirmButtonText: '跳转到登录',
            type: "success",
            showClose: false,
            roundButton: true,
            center: true
          }).then(() => router.push({path: "/login"}));
    } else {
      ElMessage({
        customClass: 'customMessage',
        type: 'error',
        message: res.data.code + res.data.msg,
      });
      newPassword.value = '';
      confirmPassword.value = '';
    }
  }).finally(() => {
    loading.value = false;
  });
}

const loading = ref(false);
const imageFileList = ref([] as any);
const imgURLs = ref([] as any);
function handleExceed() {
  ElMessage.warning(`当前限制选择 1 个文件`);
}
async function loopUpload() {
  for (let image of imageFileList.value) {
    let formData = new FormData();
    formData.append('file', image.raw);
    const res = await uploadImage(formData);
    imgURLs.value.push(res.data.data as string);
  }
}
async function handleChangeUltimate() {
  loading.value = true;
  await loopUpload();
  updateUserInfo();
}
async function handleChangePassword() {
  loading.value = true;
  updatePassword();
}

watchEffect(() => {
  console.log('--- 表单变动状态 ---');
  console.log('hasNewNameInput:', hasNewNameInput.value);
  console.log('hasNewAvatarInput:', hasNewAvatarInput.value);
  console.log('hasNewRoleInput:', hasNewRoleInput.value);
  console.log('hasNewEmailInput:', hasNewEmailInput.value, '| email合法:', emailLegal.value);
  console.log('hasNewTelephoneInput:', hasNewTelephoneInput.value, '| tel合法:', telLegal.value);
  console.log('hasNewLocationInput:', hasNewLocationInput.value);

  const result = hasNewNameInput.value ||
      hasNewAvatarInput.value ||
      hasNewRoleInput.value ||
      (hasNewEmailInput.value && emailLegal.value) ||
      (hasNewTelephoneInput.value && telLegal.value) ||
      hasNewLocationInput.value;

  console.log('总判断结果 hasAnyFieldChanged:', result);
});
</script>

<template>
  <el-main class="main-container">
    <el-card class="aside-card radius1" shadow="never">
      <div class="avatar-area" @click="displayInfoCard= '5'">
        <el-avatar :src="avatar" fit="cover" :size="80">
        </el-avatar>
        <span class="avatar-text"> {{ username }}的主页</span>
      </div>

      <el-divider></el-divider>

      <el-descriptions :column="1" border class="user-info">

        <template #title>
          个人信息
          <el-tooltip content="点击字段可修改该字段。密码不可与其他字段一同修改" placement="top">
            <el-icon class="ml-1 cursor-pointer">
              <InfoFilled />
            </el-icon>
          </el-tooltip>
        </template>

        <template #extra>
          <el-button type="primary" size="small"
                     @click="displayInfoCard = '0'">
            <span>修改密码</span>
          </el-button>
        </template>

        <el-descriptions-item label="类型">
          <el-tag class="full-tag" @click="displayInfoCard = '6'">{{ parseRole(role) }}</el-tag>
        </el-descriptions-item>

        <el-descriptions-item label="姓名">
          <el-tag class="full-tag" type="success" @click="displayInfoCard = '1'">{{ name }}</el-tag>
        </el-descriptions-item>

        <el-descriptions-item label="手机号">
          <el-tag class="full-tag" type="info" @click="displayInfoCard = '2'">{{ telephone }}</el-tag>
        </el-descriptions-item>

        <el-descriptions-item label="邮箱">
          <el-tag class="full-tag" type="warning" @click="displayInfoCard = '3'">{{ email }}</el-tag>
        </el-descriptions-item>

        <el-descriptions-item label="地址">
          <el-tag class="full-tag" type="danger" @click="displayInfoCard = '4'">{{ location }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card v-if="displayInfoCard === '0'" class="change-card radius1" shadow="never">
      <template #header>
        <div class="card-header">
          <span>修改密码</span>
          <el-button @click="handleChangePassword" :disabled="changePasswordDisabled" :loading="loading">修改</el-button>
        </div>
      </template>

      <el-form>
        <el-form-item>
          <label for="newPassword">密码</label>
          <el-input type="password" id="newPassword" v-model="newPassword" placeholder="•••••••••" required/>
        </el-form-item>
        <el-form-item>
          <label v-if="!hasConfirmPasswordInput" for="confirm_password">确认密码</label>
          <label v-else-if="!isPasswordIdentical" for="confirm_password" class="error-warn">密码不一致</label>
          <label v-else for="confirm_password">确认密码</label>

          <el-input type="password" id="confirm_password" v-model="confirmPassword"
                    :class="{'error-warn-input' :(hasConfirmPasswordInput && !isPasswordIdentical)}"
                    placeholder="•••••••••" required/>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="displayInfoCard === '1'" class="change-card radius1" shadow="never">
      <template #header>
        <div class="card-header">
          <span>修改姓名</span>
          <el-button @click="handleChangeUltimate" :disabled="changeDisabled" :loading="loading">更新</el-button>
        </div>
      </template>

      <el-form>
        <el-form-item>
          <label for="newName">姓名</label>
          <el-input type="text" id="newName" v-model="newName"/>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="displayInfoCard === '2'" class="change-card radius1" shadow="never">
      <template #header>
        <div class="card-header">
          <span>修改手机号</span>
          <el-button @click="handleChangeUltimate" :disabled="changeDisabled" :loading="loading">更新</el-button>
        </div>
      </template>

      <el-form>
        <el-form-item>
          <label for="newTelephone">手机号</label>
          <el-input type="text" id="newTelephone" v-model="newTelephone"/>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="displayInfoCard === '3'" class="change-card radius1" shadow="never">
      <template #header>
        <div class="card-header">
          <span>修改邮箱</span>
          <el-button @click="handleChangeUltimate" :disabled="changeDisabled" :loading="loading">更新</el-button>
        </div>
      </template>

      <el-form>
        <el-form-item>
          <label for="newEmail">邮箱</label>
          <el-input type="text" id="newEmail" v-model="newEmail"/>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="displayInfoCard === '4'" class="change-card radius1" shadow="never">
      <template #header>
        <div class="card-header">
          <span>修改地址</span>
          <el-button @click="handleChangeUltimate" :disabled="changeDisabled" :loading="loading">更新</el-button>
        </div>
      </template>

      <el-form>
        <el-form-item>
          <label for="newLocation">地址</label>
          <el-input type="text" id="newLocation" v-model="newLocation"/>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="displayInfoCard === '5'" class="change-card radius1" shadow="never">
      <template #header>
        <div class="card-header">
          <span>修改头像</span>
          <el-button @click="handleChangeUltimate" :disabled="changeAvatarDisabled" :loading="loading">更新</el-button>
        </div>
      </template>

      <el-form>
        <el-form-item>
          <el-upload
              v-model:file-list="imageFileList"
              :limit="1"
              :on-exceed="handleExceed"
              class="upload-demo input"
              list-type="picture"
              :auto-upload="false"
              drag
              style="width: 100%;"
          >
            <el-icon class="el-icon--upload">
              <upload-filled/>
            </el-icon>
            <div class="el-upload__text">
              将头像文件拖到此处或单击此处上传。仅允许上传一份文件。
            </div>
          </el-upload>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="displayInfoCard === '6'" class="change-card radius1" shadow="never">
      <template #header>
        <div class="card-header">
          <span>修改类型</span>
          <el-button @click="handleChangeUltimate" :disabled="changeDisabled" :loading="loading">更新</el-button>
        </div>
      </template>

      <el-form>
        <el-form-item>
          <label for="newRole">类型</label>
          <el-select id="newRole"
                     v-model="newRole"
                     placeholder="请选择"
                     style="width: 100%;"
          >
            <el-option value="ADMIN" label="管理员"/>
            <el-option value="USER" label="用户"/>
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>
  </el-main>
</template>

<style scoped>
.error-warn {
  color: red;
}

.error-warn-input {
  --el-input-focus-border-color: red;
}

.main-container {
  display: flex;
  flex-direction: row;
  padding: 15px;
  gap: 5px;
  justify-content: center;
  background-image: url("../../assets/pexels-padrinan-19670.jpg");
  background-repeat: no-repeat;
  background-position: center center;
  background-size: cover;
}

.aside-card{
  width: 31%;
  height: 90%;
  margin: 20px 0;
  background-color: rgba(255, 255, 255, 0.8); /* 半透明白色 */
  backdrop-filter: blur(10px);                /* 毛玻璃模糊效果 */
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);   /* 柔和阴影 */
  border: 1px solid rgba(255, 255, 255, 0.3);  /* 淡淡的边框 */
}

/* 穿透 Element Plus 内部样式 */
::v-deep(.user-info .el-descriptions__header),
::v-deep(.user-info .el-descriptions__body),
::v-deep(.user-info .el-descriptions__table),
::v-deep(.user-info .el-descriptions-item__cell),
::v-deep(.user-info .el-descriptions__label),
::v-deep(.user-info .el-descriptions__content) {
  background-color: transparent !important;
  border: none !important;
  box-shadow: none !important;
}

/* 移除 table 线框 */
::v-deep(.user-info .el-descriptions__table) {
  border-collapse: collapse;
}

/* 加一点毛玻璃/圆角 */
.user-info {
  backdrop-filter: blur(6px);
  border-radius: 10px;
  padding: 12px;
}

/* 鼠标移上去变化指针 */
::v-deep(.user-info .el-tag) {
  cursor: pointer;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.change-card {
  width: 50%;
  margin: 20px;
  height: 90%;
  background-color: rgba(255, 255, 255, 0.8); /* 半透明白色 */
  backdrop-filter: blur(10px);                /* 毛玻璃模糊效果 */
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);   /* 柔和阴影 */
  border: 1px solid rgba(255, 255, 255, 0.3);  /* 淡淡的边框 */
}

.avatar-area {
  display: flex;
  justify-content: space-around;
  align-items: center;
  gap: 30px;
}

.avatar-text {
  font-size: x-large;
  font-weight: bolder;
  margin-right: auto;
}

.full-tag {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: 100%;
  min-height: 32px;        /* 确保高度 */
  padding-left: 12px;      /* 左侧内边距 */
  box-sizing: border-box;
  color: #666;
  font-weight: 500;
  background-color: var(--el-tag-bg-color, #f0f0f0); /* 兜底背景色 */
  border: none !important; /* 去掉边框，避免空字段时只剩框线 */
  border-radius: 4px;
}
</style>