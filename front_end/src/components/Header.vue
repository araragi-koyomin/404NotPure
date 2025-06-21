<script setup lang="ts">
import { router } from '../router';
import { ref, onMounted, onUnmounted } from "vue";
import { Session, ChatMessage, question, send, getMessage, getSessionList } from "../api/chat.ts";
// import { useReactiveSessionStorage } from "../utils/storage.ts";
import {
  User,
  SwitchButton,
  Reading,
  Tickets,
  Promotion,
  ShoppingCart,
  Message,
} from "@element-plus/icons-vue";

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

const showProfile = ref(false);

function handleProfileClick() {
  showProfile.value = true;
}

const sessions = ref<Session[]>([])
const messages = ref<ChatMessage[]>([]);
const inputContent = ref('');
const selectedPeerId = ref(0);
const currentUserId = ref(sessionStorage.getItem('userId') || '');

const loadSessions = async () => {
  sessions.value = await getSessionList()
}

const selectSession = async (peerId: number) => {
  selectedPeerId.value = peerId
  messages.value = await getMessage(peerId)
}

const sendMessage = async () => {
  if (inputContent.value.trim() === '') return;

  await send(selectedPeerId.value, inputContent.value);
  inputContent.value = '';

  // 重新从后端获取消息列表
  messages.value = await getMessage(selectedPeerId.value);
};


onMounted(() => {
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
  loadSessions();
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

      <div @click.stop.prevent="handleProfileClick" class="el-menu-item custom-menu-item">
        <el-icon :size="20"><Message /></el-icon>
        客服
      </div>

      <el-menu-item index="" @click="logout">
        <el-icon :size="20">
          <SwitchButton/>
        </el-icon>
      </el-menu-item>
    </el-menu>

    <el-dialog
          v-model="showProfile"
          align-center
          :draggable="true"
          :show-close="false"
          class="service-dialog"
          width="800px"
    >
        <template #default>
          <div class="chat-container">
            <!-- 左侧：会话列表 -->
            <div class="chat-sessions">
              <div
                  v-for="session in sessions"
                  :key="session.peerId"
                  class="chat-session"
                  :class="{ 'active-session': session.peerId === selectedPeerId }"
                  @click="selectSession(session.peerId)"
              >
                <div class="peer-id">用户 {{ session.peerId }}</div>
                <div class="last-message">{{ session.lastMessage }}</div>
              </div>
            </div>

            <!-- 右侧：消息和输入 -->
            <div class="chat-main">
              <!-- 消息区 -->
              <div class="chat-messages">
                <div
                    v-for="msg in messages"
                    :key="msg.id"
                    :class="msg.senderId === Number(currentUserId) ? 'message-self' : 'message-other'"
                >
                  <div class="message-bubble">
                    {{ msg.content }}
                  </div>
                </div>
              </div>

              <!-- 输入区 -->
              <div class="chat-input">
                <el-input
                    v-model="inputContent"
                    placeholder="请输入消息..."
                    class="input-box"
                    @keyup.enter="sendMessage"
                />
                <el-button type="primary" @click="sendMessage">发送</el-button>
              </div>
            </div>
          </div>
        </template>
      </el-dialog>
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

.service-dialog .el-dialog {
  width: 33vw;           /* 占 1/3 */   /* 占 2/3 */
  background: rgba(255,255,255,0.9);  /* 半透明 */
  backdrop-filter: blur(6px);
  border-radius: 12px;
  resize: both;          /* CSS 原生缩放 */
  overflow: auto;        /* 缩放后出现滚动条 */
}

</style>
