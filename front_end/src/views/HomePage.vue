<script setup lang="ts">

import {ArrowRight, Goods} from "@element-plus/icons-vue";
import {router} from "../router";

function handleClick() {
  router.push({path: "/allProduct"});
}
</script>

<template>
  <el-main class="main-frame">

    <div style="height: 20%"></div>
    <el-row justify="center">
      <img
          style="width: 300px"
          src="../assets/img.png"
          alt="Element logo"
      />
    </el-row>
    <el-row justify="center">
      <el-text style="font-size: 50px;margin: 5px;font-family:fantasy; color: tomato">Tomato Mall</el-text>
    </el-row>
    <div style="height:5%"></div>
    <el-row justify="center">
      <el-button size="large" @click.prevent="handleClick">
        <el-icon size="16" style="margin-right: 5px;margin-left: 2px">
          <Goods/>
        </el-icon>
        商店
        <el-icon style="margin-right: 2px;margin-left: 2px">
          <ArrowRight/>
        </el-icon>
      </el-button>
    </el-row>

  </el-main>


</template>

<style scoped>
.main-frame {
  background-image: url("../assets/pexels-andreea-ch-371539-1166644.jpg");
  background-repeat: no-repeat;
  background-position: center center;
  background-size: cover;
}
</style>