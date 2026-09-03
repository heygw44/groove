package com.groove.order.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

/** 주문번호 생성기. 형식 yyyyMMdd-XXXXXXXX (X 는 대문자 알파벳+숫자 8자리 난수). */
@Component
public class OrderNumberGenerator {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final String RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final int RANDOM_LENGTH = 8;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		String datePart = LocalDate.now().format(DATE_FORMAT);
		StringBuilder randomPart = new StringBuilder(RANDOM_LENGTH);
		for (int i = 0; i < RANDOM_LENGTH; i++) {
			randomPart.append(RANDOM_CHARS.charAt(secureRandom.nextInt(RANDOM_CHARS.length())));
		}
		return datePart + "-" + randomPart;
	}
}
