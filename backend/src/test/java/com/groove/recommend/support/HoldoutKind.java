package com.groove.recommend.support;

/** 홀드아웃을 어떤 신호에서 뗄지. {@link HoldoutSplitter} 가 참조한다. */
public enum HoldoutKind {

	WISH,
	PURCHASE,
	BOTH
}
