package com.groove.product.entity;

/** 바코드 정규화(공백·하이픈 제거, 빈 값은 null). Product 저장·검색·Discogs 적재가 같은 규칙을 쓰도록 공용으로 뺐다. */
public final class BarcodeNormalizer {

	private BarcodeNormalizer() {
	}

	public static String normalize(String barcode) {
		if (barcode == null) {
			return null;
		}
		String normalized = barcode.replaceAll("[\\s-]", "");
		return normalized.isEmpty() ? null : normalized;
	}
}
