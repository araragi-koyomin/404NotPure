import { axios } from '../utils/request.ts';
import { PRODUCT_MODULE } from './_prefix';
import { ElMessage } from 'element-plus';

export type specificationInfo = {
    id: string | number;
    item: string;
    value: string;
    productId: string;
};

export type productInfo = {
    id: string;
    title: string;
    price: number;
    rate: number;
    description: string;
    cover: string;
    detail: string;
    specifications: specificationInfo[];
};

export type updateProductInfo = {
    id: string;
    title?: string;
    price?: number;
    rate?: number;
    description?: string;
    cover?: string;
    detail?: string;
    specifications?: specificationInfo[];
};

export type createProductInfo = {
    id: string;
    title: string;
    price: number;
    rate?: number;
    description?: string;
    cover?: string;
    detail?: string;
    specifications?: specificationInfo[];
};

export type BaseResponse<T = any> = {
    code: number;
    data: T;
    msg: string | null;
};

export type Stockpile = {
    id: string;
    productId: string;
    amount: number;
    frozen: number;
};

const handleError = (code: number, msg: string): never => {
    ElMessage.error(msg);
    const error = new Error(msg);
    (error as any).code = code;
    throw error;
};

export const getProductList = async (): Promise<productInfo[]> => {
    const response = await axios.get<BaseResponse<productInfo[]>>(PRODUCT_MODULE);
    if (response.data.code === 200) return response.data.data;
    return handleError(response.data.code, response.data.msg || '获取商品列表失败');
};

export const getProductById = async (id: string): Promise<productInfo> => {
    const response = await axios.get<BaseResponse<productInfo>>(`${PRODUCT_MODULE}/${id}`);
    if (response.data.code === 200) return response.data.data;
    return handleError(response.data.code, response.data.msg || '获取商品信息失败');
};

export const updateProduct = async (product: updateProductInfo): Promise<string> => {
    const response = await axios.put<BaseResponse<string>>(PRODUCT_MODULE, product);
    if (response.data.code === 200) return response.data.data;
    return handleError(response.data.code, response.data.msg || '更新商品失败');
};

export const deleteProduct = async (id: string): Promise<string> => {
    const response = await axios.delete<BaseResponse<string>>(`${PRODUCT_MODULE}/${id}`);
    if (response.data.code === 200) return response.data.data;
    return handleError(response.data.code, response.data.msg || '删除商品失败');
};

export const createProduct = async (product: createProductInfo): Promise<productInfo> => {
    const response = await axios.post<BaseResponse<productInfo>>(PRODUCT_MODULE, product);
    if (response.data.code === 200) return response.data.data;
    return handleError(response.data.code, response.data.msg || '新增商品失败');
};

export const updateProductStock = async (
    productId: string,
    amount: number
): Promise<string> => {
    const response = await axios.patch<BaseResponse<string>>(
        `${PRODUCT_MODULE}/stockpile/${productId}`,
        { amount }
    );
    if (response.data.code === 200) return response.data.data;
    return handleError(response.data.code, response.data.msg || '调整库存失败');
};

export const getProductStock = async (productId: string): Promise<Stockpile> => {
    const response = await axios.get<BaseResponse<Stockpile>>(
        `${PRODUCT_MODULE}/stockpile/${productId}`
    );
    if (response.data.code === 200) return response.data.data;
    return handleError(response.data.code, response.data.msg || '查询库存失败');
};