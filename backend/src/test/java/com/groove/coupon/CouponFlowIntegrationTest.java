package com.groove.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.coupon.dto.CouponIssueRequest;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.repository.CouponRepository;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.MemberRepository;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class CouponFlowIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	CouponRepository couponRepository;

	@Nested
	@DisplayName("발급 → 재발급 → 내 쿠폰함 → 적용 가능 쿠폰 조회 흐름")
	class IssueAndQueryFlow {

		@Test
		@DisplayName("전체 흐름을 정상적으로 완료한다")
		void completesFullCouponFlow() throws Exception {
			// given
			Member member = memberRepository.save(
					Member.create("coupon-flow-" + UUID.randomUUID() + "@groove.com", "encoded", "그루버"));
			String token = "Bearer " + jwtProvider.createAccessToken(member.getId(), MemberRole.USER);
			String code = "FLOW" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
			couponRepository.save(Coupon.create(code, "플로우 쿠폰", DiscountType.RATE, BigDecimal.TEN,
					BigDecimal.valueOf(10000), BigDecimal.valueOf(5000), 10, LocalDateTime.now().plusDays(7)));
			CouponIssueRequest issueRequest = new CouponIssueRequest(code);

			// when & then: 발급
			mockMvc.perform(post("/api/v1/coupons/issue")
							.header(HttpHeaders.AUTHORIZATION, token)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(issueRequest)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.couponCode", is(code)));

			// 재발급은 409
			mockMvc.perform(post("/api/v1/coupons/issue")
							.header(HttpHeaders.AUTHORIZATION, token)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(issueRequest)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("COUPON_ALREADY_ISSUED")));

			// 내 쿠폰함 usable
			mockMvc.perform(get("/api/v1/members/me/coupons")
							.header(HttpHeaders.AUTHORIZATION, token)
							.param("status", "usable"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].couponCode", is(code)));

			// 적용 가능 쿠폰 조회 - 예상 할인액 확인
			mockMvc.perform(get("/api/v1/coupons/available")
							.header(HttpHeaders.AUTHORIZATION, token)
							.param("orderAmount", "50000"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].couponCode", is(code)))
					.andExpect(jsonPath("$.data[0].expectedDiscount", is(5000.0)));

			// then
			Coupon reloaded = couponRepository.findByCode(code).orElseThrow();
			assertThat(reloaded.getIssuedCount()).isEqualTo(1);
		}
	}
}
