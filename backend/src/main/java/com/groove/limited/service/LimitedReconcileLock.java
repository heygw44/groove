package com.groove.limited.service;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.groove.global.alert.AlertNotifier;
import com.groove.global.lock.NamedLock;

import lombok.extern.slf4j.Slf4j;

/** 한정반 대사 스케줄러의 동시 실행을 막는 MySQL named lock 래퍼. */
@Slf4j
@Component
public class LimitedReconcileLock {

	static final String LOCK_NAME = "groove:limited-reconcile";

	private final NamedLock namedLock;

	@Autowired
	public LimitedReconcileLock(DataSource dataSource, AlertNotifier alertNotifier) {
		this.namedLock = new NamedLock(dataSource, log, alertNotifier);
	}

	/** 락 획득에 실패하면 task 를 실행하지 않고 false 를 반환한다. */
	public boolean runExclusively(Runnable task) {
		return namedLock.runExclusively(LOCK_NAME, task);
	}
}
