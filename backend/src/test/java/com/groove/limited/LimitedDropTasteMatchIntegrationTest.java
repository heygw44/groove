package com.groove.limited;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.auth.jwt.JwtProvider;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.recommend.dto.TasteProfileUpdateRequest;
import com.groove.recommend.service.TasteProfileService;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class LimitedDropTasteMatchIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	ArtistRepository artistRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	GenreRepository genreRepository;

	@Autowired
	LimitedDropRepository limitedDropRepository;

	@Autowired
	TasteProfileService tasteProfileService;

	@Nested
	@DisplayName("취향 매칭 tasteMatch")
	class TasteMatch {

		@Test
		@DisplayName("선호 장르가 일치하는 회원이 조회하면 목록과 상세 모두 matched 가 true 다")
		void returnsMatchedTrueForMemberWithMatchingGenre() throws Exception {
			// given
			Genre genre = genreRepository.save(Genre.create("Jazz-" + UUID.randomUUID()));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = ProductFixture.create(artist);
			product.addGenre(genre);
			productRepository.save(product);
			LimitedDrop drop = limitedDropRepository.save(LimitedDropFixture.scheduled(product));

			Member member = memberRepository.save(
					Member.create("taste-" + UUID.randomUUID() + "@groove.com", "encoded", "회원"));
			tasteProfileService.update(member.getId(),
					new TasteProfileUpdateRequest(List.of(genre.getId()), List.of(), List.of()));
			String token = "Bearer " + jwtProvider.createAccessToken(member.getId(), MemberRole.USER);

			// when & then
			mockMvc.perform(get("/api/v1/limited-drops").header(HttpHeaders.AUTHORIZATION, token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.drops[?(@.id == " + drop.getId() + ")].tasteMatch.matched",
							hasItem(true)))
					.andExpect(jsonPath("$.data.drops[?(@.id == " + drop.getId() + ")].tasteMatch.reasons",
							hasItem(hasItem("TASTE_GENRE"))));

			mockMvc.perform(get("/api/v1/limited-drops/{id}", drop.getId())
							.header(HttpHeaders.AUTHORIZATION, token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.tasteMatch.matched", is(true)))
					.andExpect(jsonPath("$.data.tasteMatch.reasons", hasItem("TASTE_GENRE")));
		}

		@Test
		@DisplayName("취향 프로필이 없는 회원이 조회하면 matched 가 false 이고 reasons 가 비어 있다")
		void returnsUnmatchedForMemberWithoutProfile() throws Exception {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			LimitedDrop drop = limitedDropRepository.save(LimitedDropFixture.scheduled(product));

			Member member = memberRepository.save(
					Member.create("noprofile-" + UUID.randomUUID() + "@groove.com", "encoded", "회원"));
			String token = "Bearer " + jwtProvider.createAccessToken(member.getId(), MemberRole.USER);

			// when & then
			mockMvc.perform(get("/api/v1/limited-drops/{id}", drop.getId())
							.header(HttpHeaders.AUTHORIZATION, token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.tasteMatch.matched", is(false)))
					.andExpect(jsonPath("$.data.tasteMatch.reasons").isEmpty());
		}

		@Test
		@DisplayName("비로그인 조회 응답에는 tasteMatch 키가 없다")
		void omitsTasteMatchKeyForAnonymous() throws Exception {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			LimitedDrop drop = limitedDropRepository.save(LimitedDropFixture.scheduled(product));

			// when & then
			mockMvc.perform(get("/api/v1/limited-drops/{id}", drop.getId()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.tasteMatch").doesNotExist());

			mockMvc.perform(get("/api/v1/limited-drops"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.drops[?(@.id == " + drop.getId() + ")].tasteMatch").doesNotExist());
		}
	}
}
