package com.groove.global.init;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.groove.inventory.service.StockService;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Genre;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.LabelRepository;
import com.groove.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class LocalDataInitializerTest {

	@Mock
	MemberRepository memberRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	GenreRepository genreRepository;

	@Mock
	LabelRepository labelRepository;

	@Mock
	ArtistRepository artistRepository;

	@Mock
	ProductRepository productRepository;

	@Mock
	StockService stockService;

	@Nested
	@DisplayName("run()")
	class Run {

		@Test
		@DisplayName("상품 데이터가 이미 있으면 카탈로그 시딩을 건너뛴다")
		void skipsCatalogWhenProductsExist() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(productRepository.count()).willReturn(1L);

			// when
			initializer.run(null);

			// then
			verify(productRepository, never()).save(any());
			verify(artistRepository, never()).saveAll(any());
		}

		@Test
		@DisplayName("회원 이메일이 이미 있으면 해당 회원 생성을 건너뛴다")
		void skipsMemberWhenEmailExists() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(memberRepository.existsByEmail(anyString())).willReturn(true);
			given(productRepository.count()).willReturn(1L);

			// when
			initializer.run(null);

			// then
			verify(memberRepository, never()).save(any());
		}

		@Test
		@DisplayName("데이터가 비어 있으면 회원 3명과 앨범 50개를 시딩한다")
		void seedsFiftyProductsWhenEmpty() {
			// given
			LocalDataInitializer initializer = newInitializer();
			given(productRepository.count()).willReturn(0L);
			given(genreRepository.findByName(anyString())).willReturn(Optional.empty());
			given(genreRepository.save(any(Genre.class))).willAnswer(invocation -> invocation.getArgument(0));
			given(labelRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
			given(artistRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
			given(productRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

			// when
			initializer.run(null);

			// then
			verify(productRepository, times(50)).save(any());
			verify(stockService, times(50)).create(any(), anyInt());
			verify(memberRepository, times(3)).save(any());
		}
	}

	private LocalDataInitializer newInitializer() {
		return new LocalDataInitializer(memberRepository, passwordEncoder, genreRepository, labelRepository,
				artistRepository, productRepository, stockService);
	}
}
