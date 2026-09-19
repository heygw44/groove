package com.groove.order.service;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.groove.global.lock.NamedLock;

import lombok.extern.slf4j.Slf4j;

/** 주문 만료 스케줄러의 동시 실행을 막는 MySQL named lock 래퍼. */
@Slf4j
@Component
public class OrderExpirationLock {

	static final String LOCK_NAME = "groove:order-expiration";

	private final NamedLock namedLock;

	@Autowired
	public OrderExpirationLock(DataSource dataSource) {
		this.namedLock = new NamedLock(dataSource, log);
	}

	/** 락 획득에 실패하면 task 를 실행하지 않고 false 를 반환한다. */
	public boolean runExclusively(Runnable task) {
		return namedLock.runExclusively(LOCK_NAME, task);
	}
}
