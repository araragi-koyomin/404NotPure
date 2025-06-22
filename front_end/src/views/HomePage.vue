<script setup lang="ts">
import "../style/fade.css"
import { ArrowRight, Goods, User } from "@element-plus/icons-vue";
import { router } from "../router";
import { ref, onMounted } from 'vue'

const showImage = ref(false)
const showTitle = ref(false)
const showButtons = ref(false)

function handleClick() {
  router.push({ path: "/allProduct" });
}

function handleClick_2() {
  router.push({ path: "/dashboard" });
}

onMounted(() => {
  // 顺序触发显示
  setTimeout(() => {
    showImage.value = true;
  }, 0); // 马上显示Logo

  setTimeout(() => {
    showTitle.value = true;
  }, 300); // 400ms后显示标题

  setTimeout(() => {
    showButtons.value = true;
  }, 600); // 800ms后显示按钮
})
</script>

<template>
  <el-main class="main-frame">
    <div class="spacer-large"></div>

    <!-- Logo 图片 -->
    <transition name="fade">
      <el-row v-if="showImage" justify="center">
        <img class="logo-image" src="../assets/title.png" alt="Element logo" />
      </el-row>
    </transition>

    <!-- 标题文字 -->
    <transition name="fade">
      <el-row v-if="showTitle" justify="center">
        <el-text class="mall-title">Tomato Mall</el-text>
      </el-row>
    </transition>

    <div class="spacer-small"></div>

    <!-- 按钮区域 -->
    <transition name="fade">
      <el-row v-if="showButtons" justify="center">
        <el-button size="large" @click.prevent="handleClick" class="button-with-icon">
          <el-icon size="16" class="icon-spacing">
            <Goods />
          </el-icon>
          书城
          <el-icon class="icon-spacing">
            <ArrowRight />
          </el-icon>
        </el-button>

        <el-button size="large" @click.prevent="handleClick_2" class="button-with-icon">
          <el-icon size="16" class="icon-spacing">
            <User />
          </el-icon>
          个人
          <el-icon class="icon-spacing">
            <ArrowRight />
          </el-icon>
        </el-button>
      </el-row>
    </transition>
  </el-main>
</template>

<!-- scoped 样式 -->
<style scoped>
.spacer-large {
  height: 20%;
}

.spacer-small {
  height: 5%;
}

.logo-image {
  width: 300px;
}

.mall-title {
  font-size: 50px;
  margin: 5px;
  font-family: fantasy;
  color: tomato;
}

.button-with-icon {
  margin: 0 10px;
}

.icon-spacing {
  margin: 0 2px;
}
.icon-spacing:first-child {
  margin-right: 5px;
  margin-left: 2px;
}
</style>
