<script setup lang="ts">
import { router } from '../router';
import { ref, onMounted, onUnmounted, computed } from "vue";
import { Session, ChatMessage, question, send, getMessage, getSessionList } from "../api/chat.ts";
// import { useReactiveSessionStorage } from "../utils/storage.ts";
import {
  User,
  SwitchButton,
  Reading,
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

const peerPrefix = computed(() =>
    role.value === 'USER' ? '客服' : '用户'
)

const handleStorageChange = (e: StorageEvent) => {
  if (e.key == 'role') {
    updateRoleFromStorage();
  }
};

const showProfile = ref(false);

function handleProfileClick() {
  showProfile.value = true;
  if (role.value == 'USER') question();
}

const sessions = ref<Session[]>([])
const messages = ref<ChatMessage[]>([]);
const inputContent = ref('');
const selectedPeerId = ref(0);
const currentUserId = ref(sessionStorage.getItem('userId') || '');

function isSelf (msg: ChatMessage) {
  return msg.receiverId == Number(currentUserId.value);      // currentUid: number
}

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

function formatDate(ts: number) {
  const d = new Date(ts);        // 仍然用毫秒级时间戳
  const pad = (n: number) => String(n).padStart(2, '0');

  const Y = d.getFullYear();
  const M = pad(d.getMonth() + 1);
  const D = pad(d.getDate());
  const h = pad(d.getHours());
  const m = pad(d.getMinutes());
  const s = pad(d.getSeconds());

  return `${Y}-${M}-${D} ${h}:${m}:${s}`;   // 2025-06-21 23:55:08
}


onMounted(() => {
  loadSessions();
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

      <el-menu-item index="/dashboard">
        <el-icon :size="20">
          <User/>
        </el-icon>
        个人
      </el-menu-item>

      <div @click.stop.prevent="handleProfileClick" class="el-menu-item custom-menu-item">
        <el-icon :size="20">
          <Message />
        </el-icon>
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
              <div class="peer-id">{{ peerPrefix }} {{ session.peerId }}</div>
              <div class="last-message">{{ session.lastMessage }}</div>
            </div>
          </div>

          <!-- 右侧：消息 + 输入框 -->
          <div class="chat-main">
            <div class="chat-messages">
              <div
                  v-for="msg in messages"
                  :key="msg.id"
                  :class="{
                    'message-wrapper': true,
                    'message-other' : isSelf(msg),
                    'message-self'  : !isSelf(msg),
                  }"
              >
                <!-- 年-月-日时间戳 -->
                <div class="timestamp">{{ formatDate(msg.timestamp) }}</div>
                <div class="message-bubble">
                  {{ msg.content }}
                </div>
              </div>
            </div>

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

.chat-container {
  display: flex;
  height: 540px;            /* ↙︎ 让对话框内部可滚动，按需改 */
}

/* 左侧 25%，竖排显示会话列表 */
.chat-sessions {
  flex: 0 0 25%;            /* 绝对占 1/4；想要自适应改成 flex: 1; 再配 grid-template-columns: 1fr 3fr */
  border-right: 1px solid #e5e5e5;
  overflow-y: auto;
}

.chat-session {
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s;
}
.chat-session:hover {
  background: #f2f2f2;
}
.active-session {
  background: #e8f4ff;
}
.peer-id       { font-weight: 600; }
.last-message  { font-size: 12px; color: #888; }

/* 右侧 75%，再用 column-flex 把“消息区 / 输入区”上下分布 */
.chat-main {
  flex: 1;                  /* 剩余空间都给它 */
  display: flex;
  flex-direction: column;
}

/* 消息列表：占满剩余高度，可以滚动 */
.chat-messages {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  background: #fafafa;
}

/* 输入区：固定高度 */
.chat-input {
  display: flex;
  align-items: center;
  padding: 12px;
  border-top: 1px solid #e5e5e5;
}
.input-box {
  flex: 1;
  margin-right: 8px;
}

/* 每条消息外壳：纵向摆放气泡 + 时间戳 */
.message-wrapper {
  display: flex;
  flex-direction: column;
  max-width: 70%;
  margin: 6px 0;
}

/* 让“自己”的 wrapper 向右对齐 */
.message-self  { margin-left: auto; }
/* 让“对方”的 wrapper 向左对齐（默认） */
.message-other {
  align-self: flex-start;
}

/* 气泡配色 */
.message-self  .message-bubble { background: #5bd45e; color: #000; }  /* 右侧（自己）：绿色 */
.message-other .message-bubble { background: #ffffff; }  /* 左侧（对方）：白色 */
.message-bubble {
  padding: 8px 12px;
  border-radius: 6px;
  word-break: break-all;
  box-shadow: 0 1px 2px rgba(0,0,0,.04);
}

/* 时间戳：小号灰字 */
.timestamp {
  margin-top: 2px;
  font-size: 12px;
  color: #999;
  text-align: center;           /* 像微信一样居中 */
}


</style>
