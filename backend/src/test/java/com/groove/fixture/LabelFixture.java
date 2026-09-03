package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.product.entity.Label;

public final class LabelFixture {

	private LabelFixture() {
	}

	public static Label create() {
		return Label.create("Blue Note", "US");
	}

	public static Label create(String name) {
		return Label.create(name, "US");
	}

	public static Label withId(Label label, Long id) {
		ReflectionTestUtils.setField(label, "id", id);
		return label;
	}
}
