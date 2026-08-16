<script setup lang="ts">
import {ElForm, ElFormItem} from "element-plus";
import {ref, computed} from 'vue';
import {router} from '../../router';
import {userInfo, userLogin} from "../../api/user.ts";
import imgUrl from '../../assets/title.png'
// 输入框值（需要在前端拦截不合法输入：是否为空+额外规则）
const username = ref('');
const password = ref('');

// 电话号码是否为空
const hasUsernameInput = computed(() => username.value != '');
// 密码是否为空
const hasPasswordInput = computed(() => password.value != '');
// 密码不设置特殊规则
// 登录按钮可用性
const loginDisabled = computed(() => {
  return !(hasUsernameInput.value && hasPasswordInput.value);
});

//登录按钮触发
async function handleLogin() {
  const loginResponse = await userLogin({
    username: username.value,
    password: password.value
  });
  if (loginResponse.data.code === '200') {
      ElMessage({
        message: "登录成功",
        type: 'success',
      });
      const token = loginResponse.data.data;
      sessionStorage.setItem('token', token);
      const accountResponse = await userInfo(username.value);
      sessionStorage.setItem('username', accountResponse.data.data.username);
      sessionStorage.setItem('name', accountResponse.data.data.name);
      sessionStorage.setItem('role', accountResponse.data.data.role);
      sessionStorage.setItem('avatar', accountResponse.data.data.avatar);
      sessionStorage.setItem('telephone', accountResponse.data.data.telephone);
      sessionStorage.setItem('email', accountResponse.data.data.email);
      sessionStorage.setItem('location', accountResponse.data.data.location);
      window.dispatchEvent(new CustomEvent('sessionstorage-local-update', {
        detail: { key: 'role', value: accountResponse.data.data.role },
      }));
      await router.push({path: "/home"});
    } else  {
      ElMessage({
        message: loginResponse.data.code + loginResponse.data.msg,
        type: 'error',
      });
      password.value = '';
    }
}
</script>

<template>
  <el-main class="main-frame bg-image">
    <el-image
        class="main-frame-img"
        :src="imgUrl"
    >
      <template #placeholder>
        <div>加载中...</div>
      </template>
      <template #error>
        <div>加载失败</div>
      </template>
    </el-image>
    <el-card class="login-card">
      <div>
        <h1>登入您的<span class="tomato-logo">TomatoMall</span>账户</h1>
        <el-form>
          <el-form-item>
            <label v-if="!hasUsernameInput" for="username">用户名</label>
            <label v-else for="username">用户名/手机号</label>
            <el-input id="username" type="text" v-model="username"
                      required :class="{'error-warn-input' :(!hasUsernameInput)}"
                      placeholder="请输入用户名"/>
          </el-form-item>

          <el-form-item>
            <label for="password">账户密码</label>
            <el-input id="password" type="password" v-model="password"
                      required
                      placeholder="••••••••"
                      show-password/>
          </el-form-item>

          <span class="button-group">
              <el-button @click.prevent="handleLogin" :disabled="loginDisabled"
                         type="primary">登入</el-button>
              <router-link to="/register" v-slot="{navigate}">
                <el-button @click="navigate">去注册</el-button>
              </router-link>
          </span>
        </el-form>
      </div>
    </el-card>
  </el-main>
</template>


<style scoped>
.main-frame {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.bg-image {
  background-image: url("../../assets/pexels-andreea-ch-371539-1166644.jpg");
  background-repeat: no-repeat;
  background-position: center center;
  background-size: cover;
}

.main-frame-img {
  width: 10%;
}

.login-card {
  width: 40%;
  padding: 20px;
  border-radius: 10px;
  background-color: rgba(255, 255, 255, 0.5); /* 半透明白色 */
  backdrop-filter: blur(10px);                /* 毛玻璃模糊效果 */
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);   /* 柔和阴影 */
  border: 1px solid rgba(255, 255, 255, 0.3);  /* 淡淡的边框 */
}

.tomato-logo {
  color: tomato; /* 番茄红 */
  font-weight: bold;
}

.error-warn-input {
  --el-input-focus-border-color: red;
}

.button-group {
  padding-top: 10px;
  display: flex;
  flex-direction: row;
  gap: 30px;
  align-items: center;
  justify-content: right;
}

:deep(.el-input__wrapper) {
  background-color: rgba(255, 255, 255, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  backdrop-filter: blur(4px);
}

</style>
