package com.groove.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ClientIpResolverTest {

	private final ClientIpResolver clientIpResolver = new ClientIpResolver();

	@AfterEach
	void tearDown() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Nested
	@DisplayName("resolve()")
	class Resolve {

		@Test
		@DisplayName("요청 스코프 밖에서 호출하면 null 을 반환한다")
		void returnsNullWhenNoRequest() {
			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).isNull();
		}

		@Test
		@DisplayName("X-Forwarded-For 가 있으면 첫 번째 값을 반환한다")
		void returnsFirstForwardedForValue() {
			// given
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1");
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).isEqualTo("1.2.3.4");
		}

		@Test
		@DisplayName("X-Forwarded-For 가 없으면 원격 주소를 반환한다")
		void returnsRemoteAddrWhenNoForwardedForHeader() {
			// given
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setRemoteAddr("192.168.0.1");
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).isEqualTo("192.168.0.1");
		}
	}
}
