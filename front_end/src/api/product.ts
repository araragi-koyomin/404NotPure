import { axios } from "../utils/request.ts";
import { describeRequestError } from "../utils/safe-error.ts";
import { PRODUCT_MODULE } from "./_prefix.ts";

// 基于Lab2规范定义的商品类型，更新支持多图片
export type Product = {
    id?: string | number;
    title: string;
    price: number;
    rate: number;
    description?: string;
    cover?: string; // 主封面图
    contentImages?: { imageUrl: string }[]; // 新增：多张产品图片
    detail?: string;
    specifications?: Specification[];
    category?: string;
};

export type ProductSummary = {
    id: number;
    title: string;
    price: number | null;
    rate: number | null;
    cover?: string;
    category?: string;
    author: string | null;
};

export type ProductPageData = {
    items: ProductSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
};

export type ProductPageQuery = {
    page: number;
    size: number;
    keyword?: string;
    categories?: string;
    sort: string;
};

// 规格说明类型
export type Specification = {
    id?: string;
    item: string;
    value: string;
    productId?: string;
};

// 库存类型
export type Stockpile = {
    id?: string;
    productId: string;
    amount: number;
    frozen: number;
};

// 评论类型
export type Comment = {
    id: string;
    userId: string;
    userName: string; // 用户昵称
    productId: string;
    content: string; // 评论内容
    rate: number; // 用户打分
    createTime: string; // 创建时间
};

// 获取所有商品列表
export const getAllProducts = async () => {
    return axios
        .get(`${PRODUCT_MODULE}`)
        .then((res) => {
            return res;
        })
        .catch((err) => {
            return err.response;
        });
};

export const getProductPage = async (query: ProductPageQuery) => {
    return axios
        .get(`${PRODUCT_MODULE}/page`, { params: query })
        .then((res) => res)
        .catch((err) => err.response);
};

// 获取指定商品信息
export const getProductById = async (id: string | number) => {
    return axios
        .get(`${PRODUCT_MODULE}/${id}`)
        .then((res) => {
            return res;
        })
        .catch((err) => {
            return err.response;
        });
};

// 更新商品信息
export const updateProduct = async (product: Product) => {
    return axios
        .put(`${PRODUCT_MODULE}`, product)
        .then((res) => {
            return res;
        })
        .catch((err) => {
            return err.response;
        });
};

// 增加商品
export const createProduct = async (product: Product) => {
    return axios
        .post(`${PRODUCT_MODULE}`, product)
        .then((res) => {
            return res;
        })
        .catch((err) => {
            console.error('%c❌ 创建失败，错误信息：', 'color: red; font-weight: bold;', describeRequestError(err));
            return err.response;
        });
};


// 删除商品
export const deleteProduct = async (id: string | number) => {
    return axios
        .delete(`${PRODUCT_MODULE}/${id}`)
        .then((res) => {
            return res;
        })
        .catch((err) => {
            return err.response;
        });
};

// 调整指定商品的库存
export const updateProductStockpile = async (productId: string | number, numberStockpile: number) => {
    return axios.patch(
        `${PRODUCT_MODULE}/stockpile/${productId}`,
        { amount: numberStockpile } // 🔥 必须是对象！
    )
        .then(res => res)
        .catch(err => err.response);
};


// 查询指定商品的库存
export const getProductStockpile = async (productId: string | number) => {
    return axios
        .get(`${PRODUCT_MODULE}/stockpile/${productId}`)
        .then((res) => {
            return res;
        })
        .catch((err) => {
            return err.response;
        });
};

// 创建评论
export const createComment = async (comment: Omit<Comment, 'id' | 'createTime'>) => {
    return axios
        .post(`${PRODUCT_MODULE}/comments`, comment)
        .then((res) => {
            return res;
        })
        .catch((err) => {
            return err.response;
        });
};

// 删除评论
export const deleteComment = async (commentId: string) => {
    return axios
        .delete(`${PRODUCT_MODULE}/comments/${commentId}`)
        .then((res) => {
            return res;
        })
        .catch((err) => {
            return err.response;
        });
};

// 将商品添加到购物车
export const addToCart = async (productId: string | number, quantity: number) => {
    return axios
        .post(`/api/cart`, { productId, quantity })
        .then((res) => {
            return res;
        })
        .catch((err) => {
            console.error('%c❌ 添加到购物车失败：', 'color: red; font-weight: bold;', describeRequestError(err));
            return err.response;
        });
};
