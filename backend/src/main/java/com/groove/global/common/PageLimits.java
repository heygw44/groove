package com.groove.global.common;

/** 요청 page 상한. page * size 가 int 를 넘어 오프셋이 음수가 되는 것을 막는다. */
public final class PageLimits {

	public static final int MAX_PAGE = 10_000;

	private PageLimits() {
	}
}
