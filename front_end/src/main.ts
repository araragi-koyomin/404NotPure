import {createApp} from 'vue';
import {router} from './router';
import App from './App.vue';
import axios from 'axios';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import './style.css';


// 使用当前页面的同源 /api，由 Vite 开发代理或部署层反向代理转发。
axios.defaults.baseURL = '';
axios.defaults.timeout = 30000;

//创建一个新的Vue应用实例，使用ElementPlus插件和路由，然后挂载到页面上id为'app'的元素上。
createApp(App).use(ElementPlus).use(router).mount('#app');
