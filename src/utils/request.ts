import axios from 'axios'
axios.defaults.withCredentials = true;
const service = axios.create()

//当前实例的拦截器，对所有要发送给后端的请求进行处理，在其中加入token
service.interceptors.request.use(
    config => {
        // ✅ 让请求默认携带 cookie（支持方案 A）
        config.withCredentials = true;

        // ✅ 支持方案 B：如果 sessionStorage 中有 token，就加到 headers 中
        const token = sessionStorage.getItem('token'); // 或 localStorage
        if (token) {
            config.headers['token'] = token;
        }

        return config;
    },
    error => {
        console.log(error);
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
        console.log(error)
        return Promise.reject(error)
    }
)

//设置为全局变量
export {
    service as axios
}
