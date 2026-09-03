package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderNumberGeneratorTest {

	private final OrderNumberGenerator orderNumberGenerator = new OrderNumberGenerator();

	@Nested
	@DisplayName("generate()")
	class Generate {

		@Test
		@DisplayName("yyyyMMdd-XXXXXXXX 형식이고 오늘 날짜로 시작한다")
		void matchesFormatWithTodayAsPrefix() {
			// when
			String orderNumber = orderNumberGenerator.generate();

			// then
			assertThat(orderNumber).matches("\\d{8}-[A-Z0-9]{8}");
			assertThat(orderNumber).startsWith(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
		}

		@Test
		@DisplayName("1000회 생성해도 중복되지 않는다")
		void generatesUniqueValuesRepeatedly() {
			// given
			int attempts = 1000;
			Set<String> orderNumbers = new HashSet<>();

			// when
			for (int i = 0; i < attempts; i++) {
				orderNumbers.add(orderNumberGenerator.generate());
			}

			// then
			assertThat(orderNumbers).hasSize(attempts);
		}
	}
}
