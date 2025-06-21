import { createRouter, createWebHashHistory } from 'vue-router';

const router = createRouter({
    history: createWebHashHistory(),
    routes: [{
        path: '/',
        redirect: '/login',
    }, {
        path: '/login',
        component: () => import('../views/user/Login.vue'),
        meta: { title: '用户登录' }
    }, {
        path: '/register',
        component: () => import('../views/user/Register.vue'),
        meta: {title: "用户注册"}
    }, {
        path: '/home',
        redirect: '/homePage',
        component: () => import('../views/Home.vue'),
        children: [
            {
                path: '/homePage',
                name: 'homePage',
                component: () => import('../views/HomePage.vue'),
                meta: {title: '首页'}
            },
            {
                path: '/dashboard',
                name: 'dashboard',
                component: () => import('../views/user/Dashboard.vue'),
                meta: {title: '个人信息'}
            }, {
                path: '/allProduct',
                name: 'allProduct',
                component: () => import('../views/product/AllProduct.vue'),
                meta: {title: '所有商品'}
            },  {
                path: '/order',
                name: 'order',
                component: () => import('../views/order/Order.vue'),
                meta: {title: '订单'}
            },{
                path: '/updateProduct',
                name: 'updateProduct',
                component: () => import('../views/product/UpdateProduct.vue'),
                meta: {title: '更新产品'}
            }, {
                path: '/product/:productId',
                name: 'product',
                component: () => import('../views/product/Product.vue'),
                meta: {title: '产品详情'}
            }, {
                path: '/createProduct',
                name: 'createProduct',
                component: () => import('../views/product/CreateProduct.vue'),
                meta: {title: '创建产品'}
            }, {
                path: '/allAdvertisement',
                name: 'allAdvertisement',
                component: () => import('../views/advertisement/AllAdvertisement.vue'),
                meta: {title: '所有广告'}
            }, {
                path: '/createAdvertisement',
                name: 'createAdvertisement',
                component: () => import('../views/advertisement/CreateAdvertisement.vue'),
                meta: {title: '创建广告'}
            }, {
                path: '/updateAdvertisement',
                name: 'updateAdvertisement',
                component: () => import('../views/advertisement/UpdateAdvertisement.vue'),
                meta: {title: '更新广告'}
            }, {
                path: '/cart',
                name: 'cart',
                component: () => import('../views/user/Cart.vue'),
                meta: {title: '购物车'}
            }
        ]
    }, {
        path: '/404',
        name: '404',
        component: () => import('../views/NotFound.vue'),
        meta: {title: '404'}
    }, {
        path: '/:catchAll(.*)',
        redirect: '/404'
    }]
});

// 处理支付宝支付返回
const handlePaymentReturn = (next: any) => {
    // 检查URL查询参数是否包含支付相关信息
    const urlParams = new URLSearchParams(window.location.search);
    const paymentSuccess = urlParams.has('payment_success');
    const orderId = urlParams.get('orderId');

    if (paymentSuccess && orderId) {
        // 恢复登录状态
        const savedToken = localStorage.getItem('payment_temp_token');
        if (savedToken) {
            sessionStorage.setItem('token', savedToken);
            localStorage.removeItem('payment_temp_token'); // 用完即删
        }
        // 从URL中删除查询参数，防止刷新页面时重复处理
        const cleanUrl = window.location.href.split('?')[0];
        window.history.replaceState({}, document.title, cleanUrl);

        // 设置会话存储标记，供订单页面检测
        sessionStorage.setItem('payment_success', 'true');
        sessionStorage.setItem('payment_order_id', orderId);

        // 重定向到订单页面
        next('/order');
        return true; // 表示处理了支付返回
    }
    return false; // 表示没有处理支付返回
};

router.beforeEach((to, _, next) => {
    // 首先检查是否是支付返回
    if (handlePaymentReturn(next)) {
        return; // 如果是支付返回，已经在handlePaymentReturn中处理了路由
    }

    // 原有的路由守卫逻辑
    const token: string | null = sessionStorage.getItem('token');
    const role: string | null = sessionStorage.getItem('role');

    if (to.meta.title) {
        document.title = to.meta.title;
    }

    if (token) {
        if (to.meta.permission) {
            if (to.meta.permission.includes(role!)) {
                next();
            } else {
                next('/404');
            }
        } else {
            next();
        }
    } else {
        if (to.path === '/login') {
            next();
        } else if (to.path === '/register') {
            next();
        } else {
            next('/login');
        }
    }
});

export {router};