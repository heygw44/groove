package com.groove.payment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 가상계좌 입금 webhook 의 secret 을 SHA-256 해시로 저장·비교한다. 평문 secret 은 토스 승인 응답에서만 받을 수
 * 있고 조회 API 는 null 을 주므로 DB 에는 해시만 남긴다.
 */
final class VirtualAccountSecretHasher {

	private VirtualAccountSecretHasher() {
	}

	static String hash(String secret) {
		if (secret == null) {
			return null;
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException ex) {
			// 모든 JVM 구현체가 지원을 강제하는 표준 알고리즘이라(JCA 표준 이름) 실제로는 발생하지 않는다.
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", ex);
		}
	}

	/** 상수시간 비교로 타이밍 사이드채널을 막는다. */
	static boolean matches(String secret, String storedHash) {
		if (secret == null || storedHash == null) {
			return false;
		}
		String secretHash = hash(secret);
		return MessageDigest.isEqual(secretHash.getBytes(StandardCharsets.UTF_8),
				storedHash.getBytes(StandardCharsets.UTF_8));
	}
}
