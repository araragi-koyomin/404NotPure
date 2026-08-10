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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 购物车服务实现类
 * 提供购物车商品的增删改查功能
 */
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

    /**
     * 添加商品到购物车
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 数量
     * @return 购物车商品视图对象
     * @throws TomatoException 商品不存在或已在购物车中时抛出
     */
    @Override
    public CartsVO addProductToCart(int userId, int productId, int quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(TomatoException::productNotExist);

        Account account = accountRepository.findById(userId)
            .orElseThrow(TomatoException::notLogin);

        List<Carts> cartItems = cartsRepository.findByAccount(account);
        for (Carts cartItem : cartItems) {
            if (cartItem.getProduct().getId() == productId) {
                throw TomatoException.existInCart();
            }
        }
        Carts cartItem = new Carts();
        cartItem.setAccount(account);
        cartItem.setProduct(product);
        cartItem.setQuantity(quantity);
        Carts savedCartItem = cartsRepository.save(cartItem);

        return savedCartItem.toVO();
    }

    /**
     * 从购物车删除商品
     * @param cartItemId 购物车商品ID
     * @return 删除结果
     * @throws TomatoException 购物车商品不存在时抛出
     */
    @Override
    @Transactional
    public String deleteCartItem(int userId, int cartItemId) {
        Carts cartItem = ownedCartItem(userId, cartItemId);
        cartsRepository.delete(cartItem);
        return "删除成功";
    }

    /**
     * 更新购物车商品数量
     * @param cartItemId 购物车商品ID
     * @param quantity 新数量
     * @return 更新结果
     * @throws TomatoException 购物车商品不存在或库存不足时抛出
     */
    @Override
    @Transactional
    public String updateCartItemQuantity(int userId, int cartItemId, int quantity) {
        Carts cartItem = ownedCartItem(userId, cartItemId);
        Product product = cartItem.getProduct();
        StockPile stockPile = stockPileRepository.findByProductId(product.getId())
                .orElseThrow(TomatoException::productNotExist);

        if (quantity > stockPile.getAmount()) {
            throw TomatoException.spillStock();
        }

        cartItem.setQuantity(quantity);
        cartsRepository.save(cartItem);
        return "修改数量成功";
    }

    private Carts ownedCartItem(int userId, int cartItemId) {
        Carts cartItem = cartsRepository.findById(cartItemId)
                .orElseThrow(TomatoException::productNotExist);
        if (cartItem.getAccount() == null || cartItem.getAccount().getId() == null
                || cartItem.getAccount().getId() != userId) {
            throw TomatoException.noPermission();
        }
        return cartItem;
    }

    /**
     * 获取购物车列表
     * @param userId 用户ID
     * @return 购物车列表视图对象
     * @throws TomatoException 用户不存在时抛出
     */
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
