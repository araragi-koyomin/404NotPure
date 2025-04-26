package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Advertisements;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.repository.AdvertisementsRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.service.AdvertisementsService;
import com.example.tomatomall.vo.AdvertisementsVO;
import org.hibernate.Hibernate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AdvertisementsServiceImpl implements AdvertisementsService {

    @Autowired
    private AdvertisementsRepository advertisementsRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public List<AdvertisementsVO> getAllAdvertisements() {
        List<Advertisements> advertisements = advertisementsRepository.findAll();
        return advertisements.stream().map(Advertisements::toVO).collect(Collectors.toList());
    }

    @Override
    public AdvertisementsVO createAdvertisement(AdvertisementsVO advertisementsVO) {
        Optional<Product> product = productRepository.findById(advertisementsVO.getProductId());
        if (!product.isPresent()) {
            throw TomatoException.productNotExist();
        }
        Advertisements advertisements = new Advertisements();
        BeanUtils.copyProperties(advertisementsVO, advertisements);
        advertisements.setImageUrl(advertisementsVO.getImgUrl());
        Advertisements savedAdvertisement = advertisementsRepository.save(advertisements);
        //取消 Hibernate 代理
        Hibernate.initialize(product);
        String redisKey = "advertisement:product:" + savedAdvertisement.getProductId();
        redisTemplate.opsForValue().set(redisKey, product.get());
        return savedAdvertisement.toVO();
    }

    @Override
    public String deleteAdvertisement(int id) {
        Advertisements advertisements = advertisementsRepository.findById(id).orElse(null);
        if (advertisements == null) {
            throw TomatoException.advertisementNotExist();
        }
        String redisKey = "advertisement:product:" + advertisements.getProductId();
        redisTemplate.delete(redisKey);
        advertisementsRepository.deleteById(id);
        return "删除成功";
    }

    @Override
    @Transactional
    public String updateAdvertisement(AdvertisementsVO advertisementsVO) {
        Advertisements advertisement = advertisementsRepository.findById(advertisementsVO.getId()).orElseThrow(TomatoException::advertisementNotExist);
        Optional<Product> product = productRepository.findById(advertisementsVO.getProductId());
        if (!product.isPresent()) {
            throw TomatoException.productNotExist();
        }
        if (advertisementsVO.getTitle() != null) {
            advertisement.setTitle(advertisementsVO.getTitle());
        }
        if (advertisementsVO.getContent() != null) {
            advertisement.setContent(advertisementsVO.getContent());
        }
        if (advertisementsVO.getImgUrl() != null) {
            advertisement.setImageUrl(advertisementsVO.getImgUrl());
        }
        advertisement.setProductId(advertisementsVO.getProductId());

        advertisementsRepository.save(advertisement);

        String redisKey = "advertisement:product:" + advertisement.getProductId();
        redisTemplate.opsForValue().set(redisKey, product);

        return "更新成功";
    }
}