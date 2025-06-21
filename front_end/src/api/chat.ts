import { axios } from "../utils/request.ts";
import { handleError, BaseResponse, CHAT_MODULE } from "./_prefix.ts";

export type Session = {
    id: number;
    userId: number;
    peerId: number;
    lastMessage: string;
    lastMessageTime: number;
}

export type ChatMessage = {
    id: number;
    senderId: number;
    receiverId: number;
    content: string;
    timestamp: number;
    roomId: number;
    isRead: boolean;
}

export const question = async (): Promise<string> => {
    const response = await axios.post<BaseResponse<string>>(`${CHAT_MODULE}/question`, {
        withCredentials: true
    });
    if (response.data.code === '200') return response.data.data;
    return handleError(response.data.code, response.data.msg || '请求客服失败');
}

export const send = async (receiverId: number, content: string): Promise<string> => {
    const message = {
        receiverId,
        content,
        // 其他字段如果后端不自动补齐，前端要显式传，如 timestamp 等
    };

    const response = await axios.post<BaseResponse<string>>(`${CHAT_MODULE}/send`, message, {
        withCredentials: true,
    });

    if (response.data.code === '200') return response.data.data;
    return handleError(response.data.code, response.data.msg || '发送消息失败');
};

export const getSessionList = async (): Promise<Session[]> => {
    const response = await axios.get<BaseResponse<Session[]>>(`${CHAT_MODULE}/sessions`, {
        withCredentials: true
    });
    if (response.data.code === '200') return response.data.data;
    return handleError(response.data.code, response.data.msg || '获取会话列表失败');
}

export const getMessage = async (peerId: number): Promise<ChatMessage[]> => {
    const response = await axios.get<BaseResponse<ChatMessage[]>>(`${CHAT_MODULE}/messages`, {
        params: { peerId },
        withCredentials: true
    });
    if (response.data.code === '200') return response.data.data;
    return handleError(response.data.code, response.data.msg || '获取消息列表失败');
}