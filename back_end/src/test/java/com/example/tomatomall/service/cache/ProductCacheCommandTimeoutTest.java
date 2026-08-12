package com.example.tomatomall.service.cache;

import io.lettuce.core.ClientOptions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCacheCommandTimeoutTest {

    @Test
    void acceptedConnectionThatNeverAnswersFallsBackWithinBoundedCommandTimeout() throws Exception {
        CountDownLatch releaseServer = new CountDownLatch(1);
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            serverExecutor.submit(() -> holdAcceptedConnection(serverSocket, releaseServer));
            LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(200))
                    .shutdownTimeout(Duration.ofMillis(100))
                    .clientOptions(ClientOptions.builder().autoReconnect(false).build())
                    .build();
            LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration("127.0.0.1", serverSocket.getLocalPort()),
                    clientConfiguration
            );
            connectionFactory.afterPropertiesSet();
            RedisTemplate<String, Object> redisTemplate = redisTemplate(connectionFactory);
            ProductCacheResilience resilience = new ProductCacheResilience(
                    new ProductCacheResilienceProperties(Duration.ofSeconds(5), 4, Duration.ofMillis(50), 100),
                    new SimpleMeterRegistry(),
                    Clock.systemUTC()
            );
            ProductDetailCache cache = new ProductDetailCache(redisTemplate, true, resilience, 100);

            long started = System.nanoTime();
            ProductDetailCache.LookupResult result = cache.lookup(91);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertTrue(result.requiresDatabaseFallback());
            assertTrue(elapsedMillis >= 150 && elapsedMillis < 1500, "elapsedMillis=" + elapsedMillis);
            releaseServer.countDown();
            connectionFactory.destroy();
        } finally {
            releaseServer.countDown();
            serverExecutor.shutdownNow();
        }
    }

    private RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashValueSerializer(valueSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    private void holdAcceptedConnection(ServerSocket serverSocket, CountDownLatch releaseServer) {
        try (Socket ignored = serverSocket.accept()) {
            releaseServer.await();
        } catch (IOException exception) {
            if (!serverSocket.isClosed()) {
                throw new IllegalStateException(exception);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
