package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Advertisements;
import com.example.tomatomall.repository.AdvertisementsRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.service.AdvertisementsService;
import com.example.tomatomall.service.cache.ProductDetailCache;
import com.example.tomatomall.service.cache.ProductDetailCacheWarmer;
import com.example.tomatomall.vo.AdvertisementsVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private ProductDetailCache productDetailCache;

    @Autowired
    private ProductDetailCacheWarmer productDetailCacheWarmer;

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
    @Transactional
    public AdvertisementsVO createAdvertisement(AdvertisementsVO advertisementsVO) {
        productRepository.findById(advertisementsVO.getProductId())
                .orElseThrow(TomatoException::productNotExist);
        Advertisements advertisements = new Advertisements();
        BeanUtils.copyProperties(advertisementsVO, advertisements);
        advertisements.setImageUrl(advertisementsVO.getImgUrl());
        Advertisements savedAdvertisement = advertisementsRepository.save(advertisements);
        int productId = savedAdvertisement.getProductId();
        productDetailCache.runAfterCommit(
                () -> productDetailCacheWarmer.warmLatestProduct(productId)
        );
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
    public String deleteAdvertisement(int id){
        Advertisements advertisements = advertisementsRepository.findById(id).orElse(null);
        if (advertisements == null) {
            throw TomatoException.advertisementNotExist();
        }
        advertisementsRepository.deleteById(id);
        productDetailCache.evictAfterCommit(advertisements.getProductId());
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
        int previousProductId = advertisement.getProductId();

        productRepository.findById(advertisementsVO.getProductId())
                .orElseThrow(TomatoException::productNotExist);
        advertisement.setProductId(advertisementsVO.getProductId());

        advertisementsRepository.save(advertisement);
        if (previousProductId != advertisement.getProductId()) {
            productDetailCache.evictAfterCommit(previousProductId);
        }
        int productId = advertisement.getProductId();
        productDetailCache.runAfterCommit(
                () -> productDetailCacheWarmer.warmLatestProduct(productId)
        );

        return "更新成功";
    }

}
