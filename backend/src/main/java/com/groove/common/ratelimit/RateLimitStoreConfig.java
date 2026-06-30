package com.groove.common.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.Bucket4jCaffeine;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SslOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.time.Duration;

/**
 * rate-limit 버킷 저장소({@link ProxyManager})를 {@code groove.rate-limit.store} 로 토글한다.
 * 단일 인스턴스/로컬/테스트는 인메모리 Caffeine(기본), 다중 인스턴스(docker/prod)는 Redis(Lettuce CAS)로 버킷을
 * 노드 간 공유한다 — 그래야 동일 IP/회원 한도가 인스턴스 수와 무관하게 유지된다. 카탈로그 캐시의
 * {@code spring.cache.type} caffeine/redis 토글과 동형이다.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitStoreConfig {

    private static final long MAX_BUCKETS = 50_000L;
    /** 버킷이 가득 리필된 뒤 저장소에서 만료될 때까지의 유휴 시간(Redis 키 TTL·Caffeine 만료). */
    private static final Duration BUCKET_TTL = Duration.ofHours(1);

    /** 단일 인스턴스/로컬/테스트 기본. 버킷을 인메모리 Caffeine 에 둔다 — 노드 간 공유 없음. */
    @Bean
    @ConditionalOnProperty(name = "groove.rate-limit.store", havingValue = "caffeine", matchIfMissing = true)
    public ProxyManager<String> caffeineRateLimitProxyManager() {
        return Bucket4jCaffeine.<String>builderFor(Caffeine.newBuilder().maximumSize(MAX_BUCKETS))
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(BUCKET_TTL))
                .build();
    }

    /**
     * 다중 인스턴스(docker/prod). 버킷 상태를 Redis 에 두어 CAS 로 노드 간 한도를 공유한다.
     * bucket4j 는 {@code String} 키 / {@code byte[]} 값 코덱의 전용 Lettuce 연결을 요구하므로 캐시용 풀과
     * 별개로 연결을 연다. 단 접속 파라미터는 {@link DataRedisConnectionDetails}(=캐시와 동일한
     * {@code spring.data.redis.*} 단일 소스)에서 host·port·database·username·password·SSL 을 그대로 가져와
     * 캐시 연결과 설정이 갈라지지 않게 한다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "groove.rate-limit.store", havingValue = "redis")
    static class LettuceRateLimitStoreConfig {

        @Bean(destroyMethod = "shutdown")
        public RedisClient rateLimitRedisClient(DataRedisConnectionDetails connectionDetails) {
            DataRedisConnectionDetails.Standalone standalone = connectionDetails.getStandalone();
            if (standalone == null) {
                // sentinel/cluster 는 host/port 한 쌍으로 표현 불가 — 조용히 잘못된 노드에 붙는 대신 부팅에서 막는다.
                throw new IllegalStateException(
                        "rate-limit Redis 저장소는 standalone 토폴로지만 지원한다(sentinel/cluster 미지원)");
            }
            SslBundle sslBundle = connectionDetails.getSslBundle();
            RedisURI.Builder uri = RedisURI.builder()
                    .withHost(standalone.getHost())
                    .withPort(standalone.getPort())
                    .withDatabase(standalone.getDatabase())
                    .withSsl(sslBundle != null);
            String password = connectionDetails.getPassword();
            if (password != null) {
                String username = connectionDetails.getUsername();
                if (username != null) {
                    uri.withAuthentication(username, password.toCharArray());
                } else {
                    uri.withPassword(password.toCharArray());
                }
            }
            RedisClient client = RedisClient.create(uri.build());
            // withSsl(true)는 TLS 사용만 알릴 뿐 trust material 을 싣지 않는다 — 사설 CA 번들이면
            // RedisClient 에 SslBundle 의 key/trust 매니저를 직접 배선해야 공인 CA 밖 인증서가 검증된다.
            if (sslBundle != null) {
                client.setOptions(ClientOptions.builder().sslOptions(toLettuceSsl(sslBundle)).build());
            }
            return client;
        }

        /** Spring {@link SslBundle} 의 key/trust 매니저·프로토콜·암호스위트를 Lettuce {@link SslOptions} 로 옮긴다. */
        private static SslOptions toLettuceSsl(SslBundle bundle) {
            SslManagerBundle managers = bundle.getManagers();
            SslOptions.Builder ssl = SslOptions.builder()
                    .keyManager(managers.getKeyManagerFactory())
                    .trustManager(managers.getTrustManagerFactory());
            org.springframework.boot.ssl.SslOptions options = bundle.getOptions();
            if (options.getEnabledProtocols() != null) {
                ssl.protocols(options.getEnabledProtocols());
            }
            if (options.getCiphers() != null) {
                ssl.cipherSuites(options.getCiphers());
            }
            return ssl.build();
        }

        // connect()는 빈 생성(부팅) 시점에 Redis 에 실제로 연결한다 — 분산 저장소가 필수인 redis 모드에서
        // Redis 미가용 시 런타임 fail-open 으로 조용히 새는 대신 부팅에서 fail-fast 해 배포 시점에 오설정을 드러낸다.
        @Bean(destroyMethod = "close")
        public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(RedisClient client) {
            return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        }

        // 분산 경로엔 Caffeine 의 maximumSize(50_000) 같은 인-앱 키 상한이 없다 — 버킷 키 수의 바운드는
        // Redis 의 maxmemory + eviction 정책(운영 전제)이 담당한다. 고cardinality 키(IP 정책) 공격 대비 필요.
        @Bean
        public ProxyManager<String> lettuceRateLimitProxyManager(StatefulRedisConnection<String, byte[]> connection) {
            return Bucket4jLettuce.casBasedBuilder(connection)
                    .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(BUCKET_TTL))
                    .build();
        }
    }

    /**
     * prod 는 store=redis 고정이지만 env/CLI 가 yaml 값을 덮을 수 있다. node-local(caffeine) 버킷으로 내려가면
     * 노드 간 한도 공유가 조용히 깨지므로(보안 약화), prod 에선 resolved 값이 redis 가 아니면 부팅에서 막는다.
     */
    @Configuration(proxyBeanMethods = false)
    @Profile("prod")
    static class RateLimitStoreProdGuard {

        RateLimitStoreProdGuard(Environment environment) {
            String store = environment.getProperty("groove.rate-limit.store");
            if (!"redis".equals(store)) {
                throw new IllegalStateException(
                        "prod 에서는 groove.rate-limit.store=redis 여야 한다(현재=" + store
                                + ") — node-local 버킷은 노드 간 한도 공유가 깨진다");
            }
        }
    }
}
