package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.ContentImageDTO;
import com.example.tomatomall.dto.ProductDTO;
import com.example.tomatomall.dto.SpecificationDTO;
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

/**
 * 广告服务实现类
 * 提供广告的创建、更新、删除和查询功能
 */
@Service
public class AdvertisementsServiceImpl implements AdvertisementsService {

    @Autowired
    private AdvertisementsRepository advertisementsRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 获取所有广告
     * @return 广告视图对象列表
     */
    @Override
    public List<AdvertisementsVO> getAllAdvertisements() {
        List<Advertisements> advertisements = advertisementsRepository.findAll();
        return advertisements.stream()
            .map(Advertisements::toVO)
            .collect(Collectors.toList());
    }

    /**
     * 创建广告
     * @param advertisementsVO 广告视图对象
     * @return 创建的广告视图对象
     * @throws TomatoException 关联商品不存在时抛出
     */
    @Override
    public AdvertisementsVO createAdvertisement(AdvertisementsVO advertisementsVO) {
        Product product = productRepository.findById(advertisementsVO.getProductId())
            .orElseThrow(TomatoException::productNotExist);
        Advertisements advertisements = new Advertisements();
        BeanUtils.copyProperties(advertisementsVO, advertisements);
        advertisements.setImageUrl(advertisementsVO.getImgUrl());
        Advertisements savedAdvertisement = advertisementsRepository.save(advertisements);
        cacheProductInRedis(product);
        return savedAdvertisement.toVO();
    }

    /**
     * 删除广告
     * @param id 广告ID
     * @return 删除结果
     * @throws TomatoException 广告不存在时抛出
     */
    @Override
    @Transactional
    public String deleteAdvertisement(int id) {
        Advertisements advertisement = advertisementsRepository.findById(id)
            .orElseThrow(TomatoException::advertisementNotExist);
        String redisKey = "advertisement:product:" + advertisement.getProductId();
        redisTemplate.delete(redisKey);
        advertisementsRepository.deleteById(id);
        return "删除成功";
    }

    /**
     * 更新广告
     * @param advertisementsVO 广告视图对象
     * @return 更新结果
     * @throws TomatoException 广告或关联商品不存在时抛出
     */
    @Override
    @Transactional
    public String updateAdvertisement(AdvertisementsVO advertisementsVO) {
        Advertisements advertisement = advertisementsRepository.findById(advertisementsVO.getId())
            .orElseThrow(TomatoException::advertisementNotExist);

        Product product = productRepository.findById(advertisementsVO.getProductId())
            .orElseThrow(TomatoException::productNotExist);
        advertisement.setProductId(advertisementsVO.getProductId());

        advertisementsRepository.save(advertisement);
        cacheProductInRedis(product);

        return "更新成功";
    }

    private ProductDTO convertToDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setTitle(product.getTitle());
        dto.setPrice(product.getPrice());
        dto.setRate(product.getRate());
        dto.setDescription(product.getDescription());
        dto.setDetail(product.getDetail());
        dto.setCover(product.getCover());
        dto.setCategory(product.getCategory());

        dto.setSpecifications(product.getSpecifications().stream()
                .map(spec -> {
                    SpecificationDTO specDTO = new SpecificationDTO();
                    specDTO.setId(spec.getId());
                    specDTO.setItem(spec.getItem());
                    specDTO.setValue(spec.getValue());
                    specDTO.setProductId(spec.getProductId());
                    return specDTO;
                })
                .collect(Collectors.toList()));

        dto.setContentImages(product.getContentImages().stream()
                .map(image -> {
                    ContentImageDTO imageDTO = new ContentImageDTO();
                    imageDTO.setId(image.getId());
                    imageDTO.setProductId(image.getProductId());
                    imageDTO.setImageUrl(image.getImageUrl());
                    return imageDTO;
                })
                .collect(Collectors.toList()));

        return dto;
    }

    private void cacheProductInRedis(Product product) {
        String redisKey = "advertisement:product:" + product.getId();
        redisTemplate.opsForValue().set(redisKey, convertToDTO(product));
    }
}