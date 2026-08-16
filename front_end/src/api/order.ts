import { axios } from "../utils/request.ts";
import { handleError, BaseResponse, API_MODULE, ORDER_MODULE } from "./_prefix.ts";

export type OrderItem = {
    productId: string;
    amount: number;
};

export type OrderRequest = {
    paymentMethod: string;
    items: OrderItem[];
};

export type Order = {
    orderId: string;
    username: string;
    totalAmount: number;
    paymentMethod: string;
    createTime: string;
    status: string;
};

export type PaymentResponse = {
    paymentForm: string;
    orderId: string;
    totalAmount: number;
    paymentMethod: string;
};

/**
 * 2.3.1提交订单进行结算
 * @param orderData Order data including payment method and items
 * @returns Created order
 */
export const submitOrder = async (orderData: OrderRequest, idempotencyKey: string): Promise<Order> => {
    const response = await axios.post<BaseResponse<Order>>(`${API_MODULE}/cart/checkout`, orderData, {
        headers: { 'Idempotency-Key': idempotencyKey }
    });

    if (response.data.code === '200') return response.data.data;

    return handleError(response.data.code, response.data.msg || '提交订单失败');
};

/**
 * 2.3.2 发起支付
 * @param orderId Order ID to pay
 * @returns Payment form HTML and related data
 */
export const initiatePayment = async (orderId: string): Promise<PaymentResponse> => {
    const response = await axios.post<BaseResponse<PaymentResponse>>(`${ORDER_MODULE}/${orderId}/pay`);

    if (response.data.code === '200') return response.data.data;

    return handleError(response.data.code, response.data.msg || '发起支付失败');
};

/**
 * 获取订单详情 by ID
 * @param orderId Order ID
 * @returns Order details
 */
export const getOrderById = async (orderId: string): Promise<Order> => {
    const response = await axios.get<BaseResponse<Order>>(`${ORDER_MODULE}/${orderId}`);

    if (response.data.code === '200') return response.data.data;

    return handleError(response.data.code, response.data.msg || '获取订单详情失败');
};

/**
 * 为当前用户获得所有订单
 * @returns List of user's orders
 */
export const getUserOrders = async (): Promise<Order[]> => {
    const response = await axios.get<BaseResponse<Order[]>>(`${ORDER_MODULE}`);

    if (response.data.code === '200') return response.data.data;

    return handleError(response.data.code, response.data.msg || '获取订单列表失败');
};
