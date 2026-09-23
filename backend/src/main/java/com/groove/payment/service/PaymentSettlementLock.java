package com.groove.payment.service;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.groove.global.alert.AlertNotifier;
import com.groove.global.lock.NamedLock;

import lombok.extern.slf4j.Slf4j;

/** 정산 스케줄러의 동시 실행을 막는 MySQL named lock 래퍼. */
@Slf4j
@Component
public class PaymentSettlementLock {

	static final String LOCK_NAME = "groove:payment-settlement";

	private final NamedLock namedLock;

	@Autowired
	public PaymentSettlementLock(DataSource dataSource, AlertNotifier alertNotifier) {
		this.namedLock = new NamedLock(dataSource, log, alertNotifier);
	}

	/** 테스트 전용. AggregationLock 과 같은 방식으로 JDBC 커넥션 레이어만 갈아끼운다(NamedLock 자체는 모킹하지 않는다). */
	PaymentSettlementLock(LockConnectionProvider connectionProvider, AlertNotifier alertNotifier) {
		this.namedLock = new NamedLock(connectionProvider::open, log, alertNotifier);
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
