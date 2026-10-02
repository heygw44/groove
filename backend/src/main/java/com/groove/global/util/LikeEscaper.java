package com.groove.global.util;

/** LIKE 와일드카드를 이스케이프한다. 백슬래시는 MySQL 기본 sql_mode 에서 닫는 따옴표를 이스케이프해 쓰지 않는다. */
public final class LikeEscaper {

	public static final char ESCAPE_CHAR = '!';

	private LikeEscaper() {
	}

	public static String escape(String raw) {
		if (raw == null) {
			return null;
		}
		return raw.replace("!", "!!")
				.replace("%", "!%")
				.replace("_", "!_");
	}
}
