package com.groove.stats.service;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.groove.global.lock.NamedLock;

import lombok.extern.slf4j.Slf4j;

/**
 * 야간 재집계·15분 증분·관리자 수동 백필의 동시 실행을 막는 MySQL named lock 래퍼.
 * 1순위 방어는 집계 자체의 멱등성이고, 이 락은 두 실행이 같은 날짜를 동시에 써서 생기는 불필요한 경합만 막는다.
 *
 * <p>실제 락 매커니즘(전용 커넥션, 연속 실패 카운터)은 {@link NamedLock} 에 위임한다. 왜 풀 밖 전용
 * 커넥션이어야 하는지는 그쪽 javadoc 을 참고 - 여기서는 락 이름만 고정해서 넘길 뿐이다.</p>
 */
@Slf4j
@Component
public class AggregationLock {

	static final String LOCK_NAME = "groove:sales-agg";

	private final NamedLock namedLock;

	@Autowired
	public AggregationLock(DataSource dataSource) {
		this.namedLock = new NamedLock(dataSource, log);
	}

	AggregationLock(LockConnectionProvider connectionProvider) {
		this.namedLock = new NamedLock(connectionProvider::open, log);
	}

	/** 락 획득에 실패하면 task 를 실행하지 않고 false 를 반환한다. */
	public boolean runExclusively(Runnable task) {
		return namedLock.runExclusively(LOCK_NAME, task);
	}

	@FunctionalInterface
	interface LockConnectionProvider {

		Connection open() throws SQLException;
	}
}
