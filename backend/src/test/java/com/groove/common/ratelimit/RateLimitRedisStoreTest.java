package com.groove.common.ratelimit;

import com.groove.support.TestcontainersConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

// 분산 rate-limit(groove.rate-limit.store=redis) — 버킷 상태가 Redis 에 살아 노드 간 한도가 공유됨을 박는다.
// 토큰 소진이 새 프록시 인스턴스(=타 노드 모사)에도 보이면 단일 인스턴스 N대에서도 실효 한도가 유지된다.
@SpringBootTest(properties = "groove.rate-limit.store=redis")
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("rate-limit 분산 저장소 — Redis(Lettuce) 노드 간 한도 공유")
class RateLimitRedisStoreTest {

    @Autowired
    private ProxyManager<String> proxyManager;

    @Autowired
    private StatefulRedisConnection<String, byte[]> connection;

    private static final String SHARED_KEY = "groove:rl:v1:testshared-key";

    // 공유 싱글턴 Redis 라 db 전체 flush 대신 이 테스트가 쓰는 키만 지운다(타 컨텍스트 캐시/버킷 비오염).
    @BeforeEach
    void clearOwnKey() {
        connection.sync().del(SHARED_KEY);
    }

    @Test
    @DisplayName("store=redis 면 Lettuce 백엔드가 선택된다(caffeine 아님)")
    void usesLettuceProxyManager() {
        assertThat(proxyManager).isInstanceOf(LettuceBasedProxyManager.class);
    }

    @Test
    @DisplayName("같은 키의 한도는 새 프록시 인스턴스에도 공유된다(상태가 Redis 에 있음)")
    void limitSharedAcrossProxyInstances() {
        Supplier<BucketConfiguration> config = () -> BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(3).refillGreedy(3, Duration.ofMinutes(1)))
                .build();

        Bucket nodeA = proxyManager.getProxy(SHARED_KEY, config);
        assertThat(nodeA.tryConsume(1)).isTrue();
        assertThat(nodeA.tryConsume(1)).isTrue();
        assertThat(nodeA.tryConsume(1)).isTrue();

        // 다른 노드를 모사 — 같은 키의 새 프록시. 상태가 로컬이면 한도가 초기화되지만 Redis 공유면 이미 소진돼 있다.
        Bucket nodeB = proxyManager.getProxy(SHARED_KEY, config);
        assertThat(nodeB.tryConsume(1)).isFalse();
    }
}
