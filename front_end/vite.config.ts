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
        host: '0.0.0.0',
        port: 5173,
        open: false,
        proxy: {
            '/api': {
                target: process.env.VITE_API_TARGET || 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
    base: './',
    define: {
        // enable hydration mismatch details in production build
        __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: 'true'
    }
});


