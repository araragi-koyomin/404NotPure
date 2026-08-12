package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.dto.ProductDTO;
import com.example.tomatomall.dto.ProductPageQuery;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.ProductContentImage;
import com.example.tomatomall.po.ProductSpecification;
import com.example.tomatomall.po.StockPile;
import com.example.tomatomall.repository.ContentImageRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.ProductPageRepository;
import com.example.tomatomall.repository.SpecificationRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.service.cache.ProductDetailCache;
import com.example.tomatomall.service.cache.ProductCacheResilience;
import com.example.tomatomall.vo.ProductContentImageVO;
import com.example.tomatomall.vo.ProductPageVO;
import com.example.tomatomall.vo.ProductVO;
import com.example.tomatomall.vo.SpecificationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品服务实现类
 * 处理商品CRUD及相关业务逻辑
 */
@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductPageRepository productPageRepository;

    @Autowired
    private SpecificationRepository specificationRepository;

    @Autowired
    private ContentImageRepository contentImageRepository;

    @Autowired
    private StockPileRepository stockPileRepository;

    @Autowired
    private ProductDetailCache productDetailCache;

    @Autowired
    private ProductCacheResilience productCacheResilience;

    /**
     * 创建商品
     * @param productVO 商品视图对象
     * @return 创建的商品实体
     */
    @Override
    @Transactional
    public Product createProduct(ProductVO productVO) {
        Product product = new Product();
        product.setTitle(productVO.getTitle());
        product.setPrice(productVO.getPrice());
        product.setRate(productVO.getRate());
        product.setDescription(productVO.getDescription());
        product.setCover(productVO.getCover());
        product.setDetail(productVO.getDetail());
        product.setCategory(productVO.getCategory());

        if (productVO.getSpecifications() != null) {
            List<ProductSpecification> specifications = productVO.getSpecifications().stream()
                    .map(specificationVO -> {
                        ProductSpecification specification = specificationVO.toPO();
                        specification.setProduct(product);
                        return specification;
                    })
                    .collect(Collectors.toList());
            product.setSpecifications(specifications);
        }

        if (productVO.getContentImages() != null) {
            List<ProductContentImage> contentImages = productVO.getContentImages().stream()
                .map(contentImageVO -> {
                    ProductContentImage contentImage = contentImageVO.toPO();
                    contentImage.setProduct(product);
                    return contentImage;
                })
                .collect(Collectors.toList());
            product.setContentImages(contentImages);
        }

        // 保存产品
        Product savedProduct = productRepository.save(product);

        // 创建并保存对应的库存记录
        StockPile stockPile = StockPile.builder()
            .productId(savedProduct.getId())
            .amount(0)
            .frozen(0)
            .build();
        stockPileRepository.save(stockPile);

        productDetailCache.evictAfterCommit(savedProduct.getId());

        return savedProduct;
    }

    /**
     * 获取商品列表
     * @return 商品视图对象列表
     */
    @Override
    public List<ProductVO> getProductList() {
        List<Product> products = productRepository.findAll();
        return products.stream().map(Product::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductPageVO getProductPage(
            String page,
            String size,
            String keyword,
            String categories,
            String sort
    ) {
        return productPageRepository.findPage(ProductPageQuery.from(page, size, keyword, categories, sort));
    }

    /**
     * 根据ID获取商品信息
     * @param id 商品ID
     * @return 商品视图对象
     * @throws TomatoException 商品不存在时抛出
     */
    @Override
    @Transactional
    public ProductVO getProductById(int id) {
        ProductDetailCache.LookupResult lookupResult = productDetailCache.lookup(id);
        if (lookupResult.getProduct() != null) {
            return convertToVO(lookupResult.getProduct());
        }
        if (lookupResult.isMissing()) {
            throw TomatoException.productNotExist();
        }

        if (lookupResult.requiresDatabaseFallback()) {
            return productCacheResilience.executeDatabaseFallback(() -> loadProductFromDatabase(id));
        }
        return loadProductFromDatabase(id);
    }

    private ProductVO loadProductFromDatabase(int id) {
        Product product = productRepository.findByIdForUpdate(id).orElse(null);
        if (product == null) {
            productDetailCache.putMissing(id);
            throw TomatoException.productNotExist();
        }
        ProductDTO productDTO = ProductDTO.fromProduct(product);
        productDetailCache.putProduct(id, productDTO);
        return convertToVO(productDTO);
    }

    /**
     * 更新商品信息
     * @param productVO 商品视图对象
     * @return 操作结果
     * @throws TomatoException 商品不存在时抛出
     */
    @Override
    @Transactional
    public String update(ProductVO productVO) {
        Product product = productRepository.findByIdForUpdate(productVO.getId())
                .orElseThrow(TomatoException::productNotExist);
        if (productVO.getTitle() != null) product.setTitle(productVO.getTitle());
        if (productVO.getPrice() != null) product.setPrice(productVO.getPrice());
        if (productVO.getRate() != null) product.setRate(productVO.getRate());
        if (productVO.getDescription() != null) product.setDescription(productVO.getDescription());
        if (productVO.getCover() != null) product.setCover(productVO.getCover());
        if (productVO.getDetail() != null) product.setDetail(productVO.getDetail());
        if (productVO.getSpecifications() != null) {
            Map<Integer, ProductSpecification> existingSpecsMap = product.getSpecifications().stream()
                .collect(Collectors.toMap(ProductSpecification::getId, spec -> spec));

            List<ProductSpecification> specsToSave = new ArrayList<>();
            for (SpecificationVO specVO : productVO.getSpecifications()) {
                Integer specId = specVO.getId(); // 要求SpecificationVO.id类型为Integer
                if (specId != null && existingSpecsMap.containsKey(specId)) {
                    ProductSpecification existingSpec = existingSpecsMap.get(specId);
                    existingSpec.setItem(specVO.getItem());
                    existingSpec.setValue(specVO.getValue());
                    specsToSave.add(existingSpec);
                } else {
                    throw TomatoException.productNotExist();
                }
            }

            specificationRepository.saveAll(specsToSave);
        }
        if (productVO.getContentImages() != null) {
            contentImageRepository.deleteByProduct_Id(product.getId());

            List<ProductContentImage> newContentImages = new ArrayList<>();
            for (ProductContentImageVO imageVO : productVO.getContentImages()) {
                ProductContentImage contentImage = new ProductContentImage();
                contentImage.setProduct(product);
                contentImage.setImageUrl(imageVO.getImageUrl());
                newContentImages.add(contentImage);
            }

            // 保存新的contentImage
            contentImageRepository.saveAll(newContentImages);
        }

        productRepository.save(product);
        productDetailCache.evictAfterCommit(product.getId());
        return "更新成功";
    }

    /**
     * 删除商品
     * @param id 商品ID
     * @return 操作结果
     */
    @Override
    @Transactional
    public String delete(int id) {
        productRepository.findByIdForUpdate(id)
                .orElseThrow(TomatoException::productNotExist);
        stockPileRepository.deleteByProductId(id);

        productRepository.deleteById(id);

        productDetailCache.evictAfterCommit(id);

        return "删除成功";
    }

    private ProductVO convertToVO(ProductDTO dto) {
        ProductVO vo = new ProductVO();
        vo.setId(dto.getId());
        vo.setTitle(dto.getTitle());
        vo.setPrice(dto.getPrice());
        vo.setRate(dto.getRate());
        vo.setDescription(dto.getDescription());
        vo.setDetail(dto.getDetail());
        vo.setCover(dto.getCover());
        vo.setCategory(dto.getCategory());
        vo.setSpecifications(Optional.ofNullable(dto.getSpecifications()).orElse(Collections.emptyList()).stream()
                .map(specDTO -> {
                    SpecificationVO specVO = new SpecificationVO();
                    specVO.setId(specDTO.getId());
                    specVO.setItem(specDTO.getItem());
                    specVO.setValue(specDTO.getValue());
                    specVO.setProductId(specDTO.getProductId());
                    return specVO;
                })
                .collect(Collectors.toList()));
        vo.setContentImages(Optional.ofNullable(dto.getContentImages()).orElse(Collections.emptyList()).stream()
                .map(imageDTO -> {
                    ProductContentImageVO imageVO = new ProductContentImageVO();
                    imageVO.setId(imageDTO.getId());
                    imageVO.setProductId(imageDTO.getProductId());
                    imageVO.setImageUrl(imageDTO.getImageUrl());
                    return imageVO;
                })
                .collect(Collectors.toList()));
        return vo;
    }
}
