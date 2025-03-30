import {axios} from '../utils/request';
import {API_MODULE} from './_prefix';

// 上传图片文件
export const uploadImage = async (payload: FormData) => {
    return axios.post(`${API_MODULE}/images`, payload); // ✅ 自动设置 content-type
};
