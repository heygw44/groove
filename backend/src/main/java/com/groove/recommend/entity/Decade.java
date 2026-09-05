package com.groove.recommend.entity;

/** 발매 연대. 취향 프로필과 상품 매칭에 쓴다. */
public enum Decade {
	D1960, D1970, D1980, D1990, D2000, D2010, D2020;

	/** 발매 연도를 연대로 변환한다. 1960 미만이거나 없으면 매칭 불가로 null. */
	public static Decade fromYear(Integer year) {
		if (year == null || year < 1960) {
			return null;
		}
		if (year >= 2020) {
			return D2020;
		}
		int decadeStart = (year / 10) * 10;
		return Decade.valueOf("D" + decadeStart);
	}
}
