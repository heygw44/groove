package com.groove;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.limited.service.LimitedDropRedisService;
import com.groove.recommend.service.BoughtTogetherRedisService;
import com.groove.support.IntegrationTestSupport;

import io.lettuce.core.event.connection.ConnectionActivatedEvent;
import io.lettuce.core.resource.ClientResources;
import reactor.core.Disposable;

/**
 * 배치 조회가 파이프라인 전용 커넥션을 매번 새로 열지 않고 공유 native 커넥션을 재사용하는지 검증한다.
 * Lettuce 는 executePipelined 를 전용 커넥션으로 보내고, 커넥션 풀이 없어 close 마다 TCP 연결이 새로 생긴다.
 */
class RedisDedicatedConnectionRegressionTest extends IntegrationTestSupport {

	@Autowired
	private ClientResources clientResources;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private LimitedDropRedisService limitedDropRedisService;

	@Autowired
	private BoughtTogetherRedisService boughtTogetherRedisService;

	@Test
	@DisplayName("findMissingStock()/getAttempts(Collection)/findScores(Collection) 는 전용 커넥션을 새로 열지 않는다")
	void reusesSharedConnectionForBatchReads() throws InterruptedException {
		// given: 공유 native 커넥션을 미리 연다
		redisTemplate.opsForValue().get("redis-dedicated-connection-regression:warmup");
		Long dropId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
		Long productId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);

		AtomicInteger activationCount = new AtomicInteger();
		Disposable subscription = clientResources.eventBus().get()
				.filter(event -> event instanceof ConnectionActivatedEvent)
				.subscribe(event -> activationCount.incrementAndGet());

		try {
			// when
			for (int i = 0; i < 5; i++) {
				limitedDropRedisService.findMissingStock(List.of(dropId));
				limitedDropRedisService.getAttempts(List.of(dropId));
				boughtTogetherRedisService.findScores(List.of(productId));
			}
			Thread.sleep(500);

			// then
			assertThat(activationCount.get()).isZero();
		} finally {
			subscription.dispose();
		}
	}
}
