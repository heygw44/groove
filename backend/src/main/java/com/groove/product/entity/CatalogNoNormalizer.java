package com.groove.product.entity;

import java.util.Locale;

/** 카탈로그 번호 정규화(대문자화, 공백·하이픈 제거). Product 저장과 검색 조건 판별이 같은 규칙을 쓰도록 공용으로 뺐다. */
public final class CatalogNoNormalizer {

	private CatalogNoNormalizer() {
	}

	public static String normalize(String catalogNo) {
		if (catalogNo == null) {
			return null;
		}
		return catalogNo.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
	}
}
