import { axios } from "../utils/request.ts";
import { handleError, BaseResponse, ADVERTISEMENT_MODULE } from "./_prefix.ts";

export type AdvertisementInfo = {
    id?: number;
    title: string;
    content: string;
    imgUrl: string;
    productId: string;
}

export const getAdvertisements = async (): Promise<AdvertisementInfo[]> => {
    const response = await axios.get<BaseResponse<AdvertisementInfo[]>>(`${ADVERTISEMENT_MODULE}`);
    if (response.data.code === '200') return response.data.data;
    return handleError(response.data.code, response.data.msg || '获取广告失败');
}

export const updateAdvertisement = async (advertisement: AdvertisementInfo): Promise<string> => {
    const response = await axios.put<BaseResponse<string>>(`${ADVERTISEMENT_MODULE}`, advertisement);
    if (response.data.code === '200') return response.data.data;
    return handleError(response.data.code, response.data.msg || '更新广告失败');
}

export const createAdvertisement = async (advertisement: AdvertisementInfo): Promise<AdvertisementInfo> => {
    const response = await axios.post<BaseResponse<AdvertisementInfo>>(`${ADVERTISEMENT_MODULE}`, advertisement);
    if (response.data.code === '200') return response.data.data;
    return handleError(response.data.code, response.data.msg || '创建广告失败');
}

export const deleteAdvertisement = async (advertisementId: number): Promise<string> => {
    const response = await axios.delete<BaseResponse<string>>(`${ADVERTISEMENT_MODULE}/${advertisementId}`);
    if (response.data.code === '200') return response.data.data;
    return handleError(response.data.code, response.data.msg || '删除广告失败');
}