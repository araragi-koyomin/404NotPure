<script setup lang="ts">
import { router } from '../router';
import { userLogout } from '../api/user.ts';
import { performLogout } from '../utils/logout.ts';
import { ref, onMounted, onUnmounted } from "vue";
// import { useReactiveSessionStorage } from "../utils/storage.ts";
import {
  User,
  SwitchButton,
  Reading,
  Tickets,
  Promotion,
  ShoppingCart
} from "@element-plus/icons-vue";

// const role = ref(sessionStorage.getItem('role') || '');
const role = ref(sessionStorage.getItem('role') || '');

const updateRoleFromStorage = () => {
  const newVal = sessionStorage.getItem('role') || '';
  if (role.value !== newVal) {
    role.value = newVal;
  }
};

const handleStorageChange = (e: StorageEvent) => {
  if (e.key === 'role') {
    updateRoleFromStorage();
  }
};

onMounted(() => {
  updateRoleFromStorage();
  window.addEventListener('storage', handleStorageChange);

  // 使用类型断言确保类型安全
  window.addEventListener(
      'sessionstorage-local-update',
      (e: CustomEvent<{ key: string; value: string }>) => {
        if (e.detail.key === 'role') {
          role.value = e.detail.value;
        }
      }
  );
});

onUnmounted(() => {
  window.removeEventListener('storage', handleStorageChange);
  // clearInterval(pollInterval);
});

//退出登录
function logout() {
  ElMessageBox.confirm(
      '是否要退出登录？',
      '提示',
      {
        customClass: "customDialog",
        confirmButtonText: '是',
        cancelButtonText: '否',
        type: "warning",
        showClose: false,
        roundButton: true,
        center: true
      }
  ).then(async () => {
    await performLogout({
      requestLogout: userLogout,
      clearLocalState: () => {
        sessionStorage.clear();
        localStorage.removeItem('payment_temp_token');
        role.value = '';
      },
      navigateToLogin: () => router.push({path: "/login"}),
      notifyFailure: () => ElMessage.error('退出请求失败，服务器 Cookie 可能仍然有效，请重试。'),
    });
  });
}

</script>


<template>
  <el-header class="header">
    <el-menu
        :default-active="$route.path"
        class="el-menu"
        mode="horizontal"
        router
        :ellipsis="false"
    >
      <el-menu-item index="/homePage">
        <img
            class="bw-icon"
            src="/src/assets/title.png"
            alt="Element logo"
        />&nbsp;&nbsp; 首页&nbsp;
      </el-menu-item>

      <div class="flex-grow"/>

      <el-sub-menu index="product" v-if="role=='ADMIN'">
        <template #title>
          <el-icon :size="20">
            <reading />
          </el-icon>
          <span>商品</span>
        </template>

        <el-menu-item index="/createProduct">创建商品</el-menu-item>
        <el-menu-item index="/allProduct">所有商品</el-menu-item>
      </el-sub-menu>

      <el-menu-item index="/allProduct" v-if="role=='USER'">
        <el-icon :size="20">
          <reading />
        </el-icon>
        商品
      </el-menu-item>

      <el-menu-item index="/Cart">
        <el-icon :size="20">
          <ShoppingCart/>
        </el-icon>
        购物车
      </el-menu-item>

      <el-sub-menu index="advertisement" v-if="role=='ADMIN'">
        <template #title>
          <el-icon :size="20">
            <promotion />
          </el-icon>
          <span>广告</span>
        </template>

        <el-menu-item index="/createAdvertisement">创建广告</el-menu-item>
        <el-menu-item index="/allAdvertisement">管理广告</el-menu-item>
      </el-sub-menu>

      <el-menu-item index="/allOrder">
        <el-icon :size="20">
          <Tickets/>
        </el-icon>
        订单
      </el-menu-item>

      <el-menu-item index="/dashboard">
        <el-icon :size="20">
          <User/>
        </el-icon>
        个人
      </el-menu-item>


      <el-menu-item index="" @click="logout">
        <el-icon :size="20">
          <SwitchButton/>
        </el-icon>
      </el-menu-item>
    </el-menu>
  </el-header>
</template>


<style scoped>
.el-menu {
  height: 50px;
}

.flex-grow {
  flex-grow: 1;
}

.header {
  display: flex;
  flex-direction: column;
  position: relative;
  height: 50px;
}

.bw-icon{
  width: 30px;
}

</style>
