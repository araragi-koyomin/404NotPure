package com.example.tomatomall.controller;

import com.example.tomatomall.service.AdvertisementsService;
import com.example.tomatomall.util.TokenUtil;
import com.example.tomatomall.vo.AdvertisementsVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 广告管理控制器
 * 提供广告的增删改查功能
 */
@RestController
@RequestMapping("/api/advertisements")
public class AdvertisementsController {

    @Autowired
    private AdvertisementsService advertisementsService;

    @Autowired
    private TokenUtil tokenUtil;
    /**
     * 获取所有广告
     * @return 广告列表
     */
    @GetMapping()
    public Response<List<AdvertisementsVO>> getAllAdvertisements() {
        List<AdvertisementsVO> advertisementsList = advertisementsService.getAllAdvertisements();
        return Response.buildSuccess(advertisementsList);
    }

    /**
     * 创建新广告
     * @param advertisementsVO 广告信息视图对象
     * @return 创建的广告信息
     */
    @PostMapping()
    public Response<AdvertisementsVO> createAdvertisement(@RequestBody AdvertisementsVO advertisementsVO, HttpServletRequest request) {
        tokenUtil.validateAdminRole(request);
        AdvertisementsVO createdAdvertisement = advertisementsService.createAdvertisement(advertisementsVO);
        return Response.buildSuccess(createdAdvertisement);
    }

    /**
     * 删除广告
     * @param id 广告ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Response<String> deleteAdvertisement(@PathVariable int id, HttpServletRequest request) {
        tokenUtil.validateAdminRole(request);
        String result = advertisementsService.deleteAdvertisement(id);
        return Response.buildSuccess(result);
    }

    @PutMapping()
    public Response<String> updateAdvertisement(@RequestBody AdvertisementsVO advertisementsVO, HttpServletRequest request) {
        tokenUtil.validateAdminRole(request);
        String result = advertisementsService.updateAdvertisement(advertisementsVO);
        return Response.buildSuccess(result);
    }
}