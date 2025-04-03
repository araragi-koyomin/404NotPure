package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.ProductContentImage;
import com.example.tomatomall.po.ProductSpecification;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.service.ProductService;
import com.example.tomatomall.vo.ProductVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Override
    public Product createProduct(ProductVO productVO) {
        Product product = new Product();
        product.setTitle(productVO.getTitle());
        product.setPrice(productVO.getPrice());
        product.setRate(productVO.getRate());
        product.setDescription(productVO.getDescription());
        product.setCover(productVO.getCover());
        product.setDetail(productVO.getDetail());

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
}