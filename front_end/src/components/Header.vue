<script setup lang="ts">
import {router} from '../router';
// import {parseRole} from "../utils";
import {
  User,
  SwitchButton,
  Reading,
  Tickets,
  Promotion,
  ShoppingCart
} from "@element-plus/icons-vue";

//const role = sessionStorage.getItem('role');    //登录的时候插入的
const role = 'admin'


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
  ).then(() => {
    sessionStorage.setItem('token', '');
    router.push({path: "/login"});
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
            src="/src/assets/img.png"
            alt="Element logo"
        />&nbsp;&nbsp; 首页&nbsp;
      </el-menu-item>

      <div class="flex-grow"/>

      <el-sub-menu index="product" v-if="role=='admin'">
        <template #title>
          <el-icon :size="20">
            <reading />
          </el-icon>
          <span>商品</span>
        </template>

        <el-menu-item index="/createProduct">创建商品</el-menu-item>
        <el-menu-item index="/allProduct">所有商品</el-menu-item>
      </el-sub-menu>

      <el-menu-item index="/Cart">
        <el-icon :size="21">
          <ShoppingCart/>
        </el-icon>
        购物车
      </el-menu-item>

      <el-sub-menu index="advertisement" v-if="role=='admin'">
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
