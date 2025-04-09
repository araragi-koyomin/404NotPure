package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.Account;
import com.example.tomatomall.po.Carts;
import com.example.tomatomall.po.Product;
import com.example.tomatomall.po.StockPile;
import com.example.tomatomall.repository.CartsRepository;
import com.example.tomatomall.repository.ProductRepository;
import com.example.tomatomall.repository.StockPileRepository;
import com.example.tomatomall.repository.UserRepository;
import com.example.tomatomall.service.CartsService;
import com.example.tomatomall.vo.CartItemVO;
import com.example.tomatomall.vo.CartsListVO;
import com.example.tomatomall.vo.CartsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CartsServiceImpl implements CartsService {

    @Autowired
    private CartsRepository cartsRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository accountRepository;

    @Autowired
    private StockPileRepository stockPileRepository;

    @Override
    public CartsVO addProductToCart(int userId, int productId, int quantity) {

        Product product = productRepository.findById(productId);
        if (product == null) {
            throw TomatoException.productNotExist();
        }
        Account account = accountRepository.findById(userId)
                .orElseThrow(TomatoException::notLogin);

        Carts cartItem = new Carts();
        cartItem.setAccount(account);
        cartItem.setProduct(product);
        cartItem.setQuantity(quantity);

        Carts savedCartItem = cartsRepository.save(cartItem);

        return savedCartItem.toVO();
    }

    @Override
    public String deleteCartItem(int cartItemId) {
        Optional<Carts> cartItemOptional = cartsRepository.findById(cartItemId);
        if (cartItemOptional.isPresent()) {
            cartsRepository.deleteById(cartItemId);
            return "删除成功";
        } else {
            throw TomatoException.productNotExist();
        }
    }

    @Override
    public String updateCartItemQuantity(int cartItemId, int quantity) {
        Carts cartItem = cartsRepository.findById(cartItemId)
                .orElseThrow(TomatoException::productNotExist);
        Product product = cartItem.getProduct();
        StockPile stockPile = stockPileRepository.findByProductId(product.getId())
                .orElseThrow(TomatoException::productNotExist);

        if (quantity > stockPile.getAmount()) {
            throw new TomatoException("修改数量超出库存", "400");
        }

        cartItem.setQuantity(quantity);
        cartsRepository.save(cartItem);
        return "修改数量成功";
    }

    @Override
    public CartsListVO getCartItems(int userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(TomatoException::notLogin);

        List<Carts> cartItems = cartsRepository.findByAccount(account);
        List<CartItemVO> cartItemVOs = cartItems.stream()
                .map(CartItemVO::new)
                .collect(Collectors.toList());

        int total = cartItemVOs.size();
        BigDecimal totalAmount = cartItemVOs.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        CartsListVO cartsListVO = new CartsListVO();
        cartsListVO.setItems(cartItemVOs);
        cartsListVO.setTotal(total);
        cartsListVO.setTotalAmount(totalAmount);

        return cartsListVO;
    }
}