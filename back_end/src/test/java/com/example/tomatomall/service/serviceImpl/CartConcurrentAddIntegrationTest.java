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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(CartConcurrentAddIntegrationTest.CartInsertBarrierConfiguration.class)
class CartConcurrentAddIntegrationTest {

    private static final CartInsertBarrier CART_INSERT_BARRIER = new CartInsertBarrier();

    @Autowired private CartsService cartsService;
    @Autowired private CartsRepository cartsRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StockPileRepository stockPileRepository;
    @Autowired private UserRepository userRepository;

    private Account account;
    private Product product;

    @BeforeEach
    void setUp() {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Account created = new Account();
        created.setUsername("cart-race-" + marker);
        created.setPassword("test-password");
        created.setName("cart-race");
        created.setRole("USER");
        created.setPoints(0);
        created.setTelephone("18" + String.format("%09d", Math.abs(marker.hashCode()) % 1_000_000_000));
        account = userRepository.saveAndFlush(created);

        product = Product.builder()
                .title("cart-race-" + marker)
                .price(new BigDecimal("19.99"))
                .rate(5.0)
                .description("test")
                .detail("test")
                .cover("test")
                .category("test")
                .specifications(new ArrayList<>())
                .contentImages(new ArrayList<>())
                .build();
        product = productRepository.saveAndFlush(product);
        stockPileRepository.saveAndFlush(StockPile.builder()
                .productId(product.getId())
                .amount(5)
                .frozen(0)
                .build());
    }

    @AfterEach
    void cleanUp() {
        if (account != null) {
            for (Carts cart : cartsRepository.findByAccount(account)) {
                cartsRepository.delete(cart);
            }
            cartsRepository.flush();
        }
        if (product != null) {
            stockPileRepository.findByProductId(product.getId()).ifPresent(stockPileRepository::delete);
            productRepository.deleteById(product.getId());
        }
        if (account != null) {
            userRepository.deleteById(account.getId());
        }
    }

    @Test
    void concurrentDuplicateAddCreatesOneRowAndReturnsStableConflictForTheOtherRequest() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CART_INSERT_BARRIER.arm();
        try {
            Future<String> first = executor.submit(() -> addOnce(ready, start));
            Future<String> second = executor.submit(() -> addOnce(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            String firstResult = first.get(10, TimeUnit.SECONDS);
            String secondResult = second.get(10, TimeUnit.SECONDS);

            assertTrue(("SUCCESS".equals(firstResult) && "409".equals(secondResult))
                    || ("409".equals(firstResult) && "SUCCESS".equals(secondResult)));
            assertEquals(2, CART_INSERT_BARRIER.attempts(),
                    "两个事务都必须通过普通重复查询并到达数据库插入，失败方才能证明来自唯一约束");
            assertEquals(1, cartsRepository.findByAccount(account).stream()
                    .filter(item -> item.getProduct().getId() == product.getId())
                    .count());
            StockPile stock = stockPileRepository.findByProductId(product.getId()).orElseThrow(AssertionError::new);
            assertEquals(5, stock.getAmount());
            assertEquals(0, stock.getFrozen());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            CART_INSERT_BARRIER.disarm();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void serviceRejectsNonPositiveQuantityEvenWhenControllerValidationIsBypassed(int invalidQuantity) {
        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> cartsService.addProductToCart(account.getId(), product.getId(), invalidQuantity)
        );

        assertEquals("400", exception.getCode());
        assertTrue(cartsRepository.findByAccount(account).isEmpty());
        StockPile stock = stockPileRepository.findByProductId(product.getId()).orElseThrow(AssertionError::new);
        assertEquals(5, stock.getAmount());
        assertEquals(0, stock.getFrozen());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void serviceRejectsNonPositiveProductIdEvenWhenControllerValidationIsBypassed(int invalidProductId) {
        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> cartsService.addProductToCart(account.getId(), invalidProductId, 1)
        );

        assertEquals("400", exception.getCode());
        assertTrue(cartsRepository.findByAccount(account).isEmpty());
        StockPile stock = stockPileRepository.findByProductId(product.getId()).orElseThrow(AssertionError::new);
        assertEquals(5, stock.getAmount());
        assertEquals(0, stock.getFrozen());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void serviceRejectsNonPositiveUpdateAndKeepsOriginalCartQuantity(int invalidQuantity) {
        int cartItemId = cartsService.addProductToCart(account.getId(), product.getId(), 1).getCartItemId();

        TomatoException exception = assertThrows(
                TomatoException.class,
                () -> cartsService.updateCartItemQuantity(account.getId(), cartItemId, invalidQuantity)
        );

        assertEquals("400", exception.getCode());
        assertEquals(1, cartsRepository.findById(cartItemId).orElseThrow(AssertionError::new).getQuantity());
        StockPile stock = stockPileRepository.findByProductId(product.getId()).orElseThrow(AssertionError::new);
        assertEquals(5, stock.getAmount());
        assertEquals(0, stock.getFrozen());
    }

    private String addOnce(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            cartsService.addProductToCart(account.getId(), product.getId(), 1);
            return "SUCCESS";
        } catch (TomatoException exception) {
            return exception.getCode();
        }
    }

    private static final class CartInsertBarrier {
        private volatile CountDownLatch arrivals = new CountDownLatch(0);
        private final AtomicInteger attempts = new AtomicInteger();
        private volatile boolean armed;

        void arm() {
            attempts.set(0);
            arrivals = new CountDownLatch(2);
            armed = true;
        }

        void beforeInsert() throws InterruptedException {
            if (!armed) {
                return;
            }
            attempts.incrementAndGet();
            CountDownLatch currentArrivals = arrivals;
            currentArrivals.countDown();
            assertTrue(currentArrivals.await(5, TimeUnit.SECONDS),
                    "两个并发事务必须都通过普通查询后到达数据库插入");
        }

        int attempts() {
            return attempts.get();
        }

        void disarm() {
            armed = false;
            arrivals.countDown();
            arrivals.countDown();
        }
    }

    @TestConfiguration
    static class CartInsertBarrierConfiguration {

        @Bean
        static BeanPostProcessor cartRepositoryBarrierPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof CartsRepository)) {
                        return bean;
                    }
                    ProxyFactory proxyFactory = new ProxyFactory();
                    proxyFactory.setTarget(bean);
                    proxyFactory.setInterfaces(CartsRepository.class);
                    proxyFactory.addAdvice((MethodInterceptor) invocation -> {
                        if ("saveAndFlush".equals(invocation.getMethod().getName())
                                && invocation.getArguments().length == 1
                                && invocation.getArguments()[0] instanceof Carts) {
                            CART_INSERT_BARRIER.beforeInsert();
                        }
                        return invocation.proceed();
                    });
                    return proxyFactory.getProxy();
                }
            };
        }
    }
}
