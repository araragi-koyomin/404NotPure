import axios from 'axios'
import { describeRequestError } from './safe-error.ts';
const service = axios.create({
    timeout: 10000,
    withCredentials: true,
});

//当前实例的拦截器，对所有要发送给后端的请求进行处理，在其中加入token
service.interceptors.request.use(
    config => {
        const token = sessionStorage.getItem('token'); // 或 localStorage
        if (token) {
            config.headers['token'] = token;
        }
        return config;
    },
    error => {
        console.error('请求拦截器出错：', describeRequestError(error));
        return Promise.reject(error);
    }
);

//当前实例的拦截器，对所有从后端收到的请求进行处理，检验http的状态码
service.interceptors.response.use(
    response => {
        if (response.status === 200) {
            return response
        } else {
            return Promise.reject()
        }
    },
    error => {
        console.error('HTTP 请求失败：', describeRequestError(error));
        return Promise.reject(error)
    }
)

//设置为全局变量
export {
    service as axios
}
