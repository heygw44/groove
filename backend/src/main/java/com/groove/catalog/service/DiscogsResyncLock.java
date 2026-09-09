package com.groove.catalog.service;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.groove.global.lock.NamedLock;

import lombok.extern.slf4j.Slf4j;

/**
 * 우선순위 재검증과 야간 스윕의 동시 실행을 막는 MySQL named lock 래퍼.
 * 집계 배치({@code groove:sales-agg})와 락 이름을 공유하지 않는다 - 시간 단위로 도는 재검증이
 * 15분 증분 집계를 굶기면 안 되기 때문이다.
 */
@Slf4j
@Component
public class DiscogsResyncLock {

	static final String LOCK_NAME = "groove:discogs-resync";

	private final NamedLock namedLock;

	@Autowired
	public DiscogsResyncLock(DataSource dataSource) {
		this.namedLock = new NamedLock(dataSource, log);
	}

	/** 락 획득에 실패하면 task 를 실행하지 않고 false 를 반환한다. */
	public boolean runExclusively(Runnable task) {
		return namedLock.runExclusively(LOCK_NAME, task);
	}
}
