import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';

// Element UI 自动导入支持
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import {ElementPlusResolver} from 'unplugin-vue-components/resolvers';

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [
        vue(),
        AutoImport({
            resolvers: [ElementPlusResolver()],
        }),
        Components({
            resolvers: [ElementPlusResolver()],
        })],
    server: {
        port: 3000,   //设定前端运行的端口
        open: true,
        allowedHosts: ['.natappfree.cc'] // 允许所有 natapp 的免费域名
    },
    base: './',
    define: {
        // enable hydration mismatch details in production build
        __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: 'true'
    }
});


