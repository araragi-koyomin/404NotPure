package com.example.tomatomall.service;

import com.example.tomatomall.po.Advertisements;
import com.example.tomatomall.vo.AdvertisementsVO;

import java.util.List;

public interface AdvertisementsService {
    List<AdvertisementsVO> getAllAdvertisements();
    AdvertisementsVO createAdvertisement(AdvertisementsVO advertisementsVO);
    String deleteAdvertisement(int id);
    String updateAdvertisement(AdvertisementsVO advertisementsVO);
}