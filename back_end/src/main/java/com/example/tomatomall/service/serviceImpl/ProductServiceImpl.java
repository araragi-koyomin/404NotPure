package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.ProductContentImage;
import com.example.tomatomall.po.ProductSpecification;
import com.example.tomatomall.repository.ContentImageRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.SpecificationRepository;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.vo.ProductContentImageVO;
import com.example.tomatomall.vo.ProductVO;
import com.example.tomatomall.vo.SpecificationVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SpecificationRepository specificationRepository;

    @Autowired
    private ContentImageRepository contentImageRepository;

    @Override
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

        return productRepository.save(product);
    }

    @Override
    public List<ProductVO> getProductList() {
        List<Product> products = productRepository.findAll();
        return products.stream().map(Product::toVO).collect(Collectors.toList());
    }

    @Override
    public ProductVO getProductById(int id) {
        Product product = productRepository.findById(id);
        ProductVO productVO = product != null ? product.toVO() : null;
        if (productVO == null) {
            throw TomatoException.productNotExist();
        }
        return productVO;
    }

    @Override
    public String update(ProductVO productVO) {
        Product product = productRepository.findById(productVO.getId());
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
            Map<Integer, ProductContentImage> existingImagesMap = product.getContentImages().stream()
                .collect(Collectors.toMap(ProductContentImage::getId, image -> image));

            List<ProductContentImage> imagesToSave = new ArrayList<>();
            for (ProductContentImageVO imageVO : productVO.getContentImages()) {
                Integer imageId = imageVO.getId();
                if (imageId != null && existingImagesMap.containsKey(imageId)) {
                    ProductContentImage existingImage = existingImagesMap.get(imageId);
                    existingImage.setImageUrl(existingImage.getImageUrl());
                    imagesToSave.add(existingImage);
                } else {
                    throw TomatoException.productNotExist();
                }
            }

            contentImageRepository.saveAll(imagesToSave);
        }

        productRepository.save(product);
        return "更新成功";
    }
}