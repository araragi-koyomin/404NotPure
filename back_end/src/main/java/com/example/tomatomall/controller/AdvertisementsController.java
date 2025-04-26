package com.example.tomatomall.controller;

import com.example.tomatomall.service.AdvertisementsService;
import com.example.tomatomall.vo.AdvertisementsVO;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/advertisements")
public class AdvertisementsController {

    @Autowired
    private AdvertisementsService advertisementsService;

    @GetMapping()
    public Response<List<AdvertisementsVO>> getAllAdvertisements() {
        List<AdvertisementsVO> advertisementsList = advertisementsService.getAllAdvertisements();
        return Response.buildSuccess(advertisementsList);
    }

    @PostMapping()
    public Response<AdvertisementsVO> createAdvertisement(@RequestBody AdvertisementsVO advertisementsVO) {
        AdvertisementsVO createdAdvertisement = advertisementsService.createAdvertisement(advertisementsVO);
        return Response.buildSuccess(createdAdvertisement);
    }

    @DeleteMapping("/{id}")
    public Response<String> deleteAdvertisement(@PathVariable int id) {
        String result = advertisementsService.deleteAdvertisement(id);
        return Response.buildSuccess(result);
    }

    @PutMapping()
    public Response<String> updateAdvertisement(@RequestBody AdvertisementsVO advertisementsVO) {
        String result = advertisementsService.updateAdvertisement(advertisementsVO);
        return Response.buildSuccess(result);
    }
}