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

        Product initializedProduct = product.get();
        ProductDTO productDTO = convertToDTO(initializedProduct);

        String redisKey = "advertisement:product:" + savedAdvertisement.getProductId();
        redisTemplate.opsForValue().set(redisKey, productDTO);
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

        // 生成 Redis 缓存的键
        String redisKey = "advertisement:product:" + advertisement.getProductId();
        // 将 Product 对象转换为 ProductDTO
        ProductDTO productDTO = convertToDTO(product.get());
        // 将 ProductDTO 存入 Redis 缓存
        redisTemplate.opsForValue().set(redisKey, productDTO);

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
}