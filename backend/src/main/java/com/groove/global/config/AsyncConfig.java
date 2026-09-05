package com.groove.global.config;

import java.util.concurrent.RejectedExecutionHandler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * 비동기 실행기 설정. 상품 조회 로그처럼 유실돼도 되는 부가 작업을 요청 스레드와 분리하기 위해 쓴다.
 *
 * <p>Executor 빈을 직접 정의하면 Boot 의 TaskExecutionAutoConfiguration 이 백오프해 MVC 비동기 요청(@Async 가 아닌
 * Callable/StreamingResponseBody 등)의 기본 실행기가 SimpleAsyncTaskExecutor 로 폴백된다. 현재 그런 컨트롤러가 없어
 * 실질적인 영향은 없다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean
	public ThreadPoolTaskExecutor viewLogExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		// 워커가 여럿이면 근접한 두 조회의 LPUSH 순서가 뒤집혀 "최근 본 상품" 순서가 어긋난다.
		// 단일 워커 + FIFO 큐로 제출 순서를 그대로 지킨다. 작업이 INSERT 한 건과 Redis 한 콜뿐이라 처리량도 충분하다.
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(500);
		executor.setThreadNamePrefix("groove-view-log-");
		// @TransactionalEventListener(AFTER_COMMIT) 의 @Async 제출은 원 트랜잭션이 커밋된 요청 스레드에서 실행된다.
		// 기본 AbortPolicy 는 큐가 가득 차면 RejectedExecutionException 을 던지는데, 그게 그대로 전파되면
		// 상품 상세 응답이 500 이 된다. 조회 로그는 유실돼도 되는 데이터라 로그만 남기고 조용히 버린다.
		executor.setRejectedExecutionHandler(rejectedTaskLogger());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(5);
		executor.initialize();
		return executor;
	}

	private RejectedExecutionHandler rejectedTaskLogger() {
		return (runnable, executor) -> log.warn("viewLogExecutor 큐 포화로 작업을 버림. activeCount={}, queueSize={}",
				executor.getActiveCount(), executor.getQueue().size());
	}

	// AsyncConfigurer 를 구현해 getAsyncUncaughtExceptionHandler() 로 2차 방어선을 두는 대신,
	// 리스너(ProductViewLogWriter) 본문의 try/catch 로만 예외를 삼킨다. 여기서 다루는 비동기 작업이 하나뿐이라
	// 전역 핸들러를 별도로 두는 것보다 호출부에서 바로 처리하는 쪽이 더 단순하다.
}
