import {ElMessage} from "element-plus";

export const API_MODULE = '/api';
export const USER_MODULE = `${API_MODULE}/accounts`;
export const PRODUCT_MODULE = `${API_MODULE}/products`;
export const CART_MODULE = `${API_MODULE}/cart`;
export const ORDER_MODULE = '/api/orders';
export const ADVERTISEMENT_MODULE = `${API_MODULE}/advertisements`;
export const CHAT_MODULE = `${API_MODULE}/chat`;

export type BaseResponse<T = any> = {
    code: string;
    data: T;
    msg: string | null;
};

export const handleError = (code: string, msg: string) => {
    ElMessage.error(msg);
    const error = new Error(msg);
    (error as any).code = code;
    throw error;
};