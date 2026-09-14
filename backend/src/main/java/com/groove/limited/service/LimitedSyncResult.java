package com.groove.limited.service;

/** stockBefore 는 키가 없었으면 -1. */
public record LimitedSyncResult(int stockBefore, int stockAfter, boolean buyersChanged, int leaked, int cleared) {

	public boolean changed() {
		return stockBefore != stockAfter || buyersChanged || leaked > 0 || cleared > 0;
	}
}
