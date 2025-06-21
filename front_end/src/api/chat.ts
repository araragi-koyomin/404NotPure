import { axios } from "../utils/request.ts";
import { BaseResponse, CHAT_MODULE } from "./_prefix.ts";

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

export const question = async (): Promise<void> => {
    await axios.post<BaseResponse<string>>(`${CHAT_MODULE}/question`, {
        withCredentials: true
    });
    return;
}

export const send = async (receiverId: number, content: string): Promise<void> => {
    const message = {
        receiverId,
        content,
        // 其他字段如果后端不自动补齐，前端要显式传，如 timestamp 等
    };

    await axios.post<BaseResponse<string>>(`${CHAT_MODULE}/send`, message, {
        withCredentials: true,
    });
};

export const getSessionList = async (): Promise<Session[]> => {
    const response = await axios.get<Session[]>(`${CHAT_MODULE}/sessions`, {
        withCredentials: true
    });
    return response.data;
}

export const getMessage = async (peerId: number): Promise<ChatMessage[]> => {
    const response = await axios.get<ChatMessage[]>(`${CHAT_MODULE}/messages`, {
        params: { peerId },
        withCredentials: true
    });
    return response.data;
}