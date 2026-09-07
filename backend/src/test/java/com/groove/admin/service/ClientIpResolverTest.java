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

		@Test
		@DisplayName("X-Forwarded-For 가 빈 값이면 원격 주소를 반환한다")
		void returnsRemoteAddrWhenForwardedForHeaderIsBlank() {
			// given
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.addHeader("X-Forwarded-For", "   ");
			request.setRemoteAddr("192.168.0.1");
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).isEqualTo("192.168.0.1");
		}

		@Test
		@DisplayName("X-Forwarded-For 도 원격 주소도 없으면 null 을 반환한다")
		void returnsNullWhenNeitherForwardedForNorRemoteAddrPresent() {
			// given
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.setRemoteAddr(null);
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).isNull();
		}

		@Test
		@DisplayName("주소가 45자를 넘으면 45자로 잘라 반환한다")
		void truncatesIpWhenLongerThanMaxLength() {
			// given
			String longIp = "1".repeat(50);
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.addHeader("X-Forwarded-For", longIp);
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

			// when
			String ip = clientIpResolver.resolve();

			// then
			assertThat(ip).hasSize(45);
			assertThat(ip).isEqualTo(longIp.substring(0, 45));
		}
	}
}
