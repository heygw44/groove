package com.groove.fixture;

import com.groove.product.entity.Label;

public final class LabelFixture {

	private LabelFixture() {
	}

	public static Label create() {
		return Label.create("Blue Note", "US");
	}
}
