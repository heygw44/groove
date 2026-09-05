package com.groove.product.dto;

import com.groove.product.entity.Label;

public record LabelResponse(Long id, String name, String country) {

	public static LabelResponse from(Label label) {
		return new LabelResponse(label.getId(), label.getName(), label.getCountry());
	}
}
