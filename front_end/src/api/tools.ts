import {axios} from '../utils/request';
import {API_MODULE} from './_prefix';

export type ImageUsage = 'AVATAR' | 'PRODUCT' | 'ADVERTISEMENT';

// 上传图片文件
export const uploadImage = async (payload: FormData, usage: ImageUsage) => {
    return axios.post(`${API_MODULE}/images`, payload, {params: {usage}}); // ✅ 自动设置 content-type
};

export const callTomatoAssistant = async (prompt: string) => {
    const payload = {
        prompt
    };

    const response = await axios.post(`${API_MODULE}/assistant/chat`, payload);
    return response.data; // 返回 { answer: string }
};
