import { axios } from "../utils/request.ts";
import { CART_MODULE } from "./_prefix.ts";
import { ElMessage } from "element-plus";

export type BaseResponse<T = any> = {
    code: number;
    data: T;
    msg: string | null;
};

export type AddCartRequest = {
    productId: string;
    quantity: number;
};

export type CartItem = {
    cartItemId: string;
    productId: string;
    title: string;
    price: number;
    description: string;
    cover: string;
    detail: string;
    quantity: number;
};

export type CartList = {
    items: CartItem[];
    total: number;
    totalAmount: number;
};

const handleError = (code: number, msg: string) => {
    ElMessage.error(msg);
    const error = new Error(msg);
    (error as any).code = code;
    throw error;
};

export const addToCart = async (params: AddCartRequest): Promise<CartItem> => {
    const response = await axios.post<BaseResponse<CartItem>>(`${CART_MODULE}`, params);
    if (response.data.code === 200) return response.data.data;
    return handleError(response.data.code, response.data.msg || '加入购物车失败');
};

export const removeFromCart = async (cartItemId: string): Promise<string> => {
    const response = await axios.delete<BaseResponse<string>>(`${CART_MODULE}/${cartItemId}`);
    if (response.data.code === 200) return response.data.data;
    return handleError(response.data.code, response.data.msg || '删除购物车商品失败');
};

export const updateCartQuantity = async (
    cartItemId: string,
    quantity: number
): Promise<string> => {
    const response = await axios.patch<BaseResponse<string>>(
        `${CART_MODULE}/${cartItemId}/quantity`,
        { quantity }
    );
    if (response.data.code === 200) return response.data.data;
    return handleError(response.data.code, response.data.msg || '修改商品数量失败');
};

export const getCartList = async (): Promise<CartList> => {
    const response = await axios.get<BaseResponse<CartList>>(`${CART_MODULE}`);
    if (response.data.code === 200) return response.data.data;
    return handleError(response.data.code, response.data.msg || '获取购物车失败');
};