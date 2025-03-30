import { axios } from "../utils/request.ts";
import { PRODUCT_MODULE } from "./_prefix.ts";

// 基于Lab2规范定义的商品类型
export type Product = {
    id: string;
    title: string;
    price: number;
    rate: number;
    description: string;
    cover: string;
    detail: string;
    specifications?: Specification[];
};

// 规格说明类型
export type Specification = {
    id: string;
    item: string;
    value: string;
    productId: string;
};

// 库存类型
export type Stockpile = {
    id?: string;
    productId: string;
    amount: number;
    frozen: number;
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

// 获取指定商品信息
export const getProductById = async (id: string) => {
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
            return err.response;
        });
};

// 删除商品
export const deleteProduct = async (id: string) => {
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
export const updateProductStockpile = async (productId: string, stockpile: Stockpile) => {
    return axios
        .patch(`${PRODUCT_MODULE}/stockpile/${productId}`, stockpile)
        .then((res) => {
            return res;
        })
        .catch((err) => {
            return err.response;
        });
};

// 查询指定商品的库存
export const getProductStockpile = async (productId: string) => {
    return axios
        .get(`${PRODUCT_MODULE}/stockpile/${productId}`)
        .then((res) => {
            return res;
        })
        .catch((err) => {
            return err.response;
        });
};