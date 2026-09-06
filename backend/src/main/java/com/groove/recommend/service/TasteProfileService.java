package com.groove.recommend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.recommend.dto.TasteProfileResponse;
import com.groove.recommend.dto.TasteProfileUpdateRequest;
import com.groove.recommend.entity.MemberTasteArtist;
import com.groove.recommend.entity.MemberTasteDecade;
import com.groove.recommend.entity.MemberTasteGenre;
import com.groove.recommend.entity.MemberTasteProfile;
import com.groove.recommend.repository.MemberTasteArtistRepository;
import com.groove.recommend.repository.MemberTasteDecadeRepository;
import com.groove.recommend.repository.MemberTasteGenreRepository;
import com.groove.recommend.repository.MemberTasteProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TasteProfileService {

	private final MemberTasteProfileRepository memberTasteProfileRepository;

	private final MemberTasteGenreRepository memberTasteGenreRepository;

	private final MemberTasteArtistRepository memberTasteArtistRepository;

	private final MemberTasteDecadeRepository memberTasteDecadeRepository;

	private final MemberRepository memberRepository;

	private final GenreRepository genreRepository;

	private final ArtistRepository artistRepository;

	private final Clock clock;

	public TasteProfileResponse getMyProfile(Long memberId) {
		MemberTasteProfile profile = memberTasteProfileRepository.findByMemberId(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RECOMMEND_PROFILE_NOT_FOUND));
		Long profileId = profile.getId();

		return TasteProfileResponse.of(
				memberTasteGenreRepository.findAllByProfileId(profileId),
				memberTasteArtistRepository.findAllByProfileId(profileId),
				memberTasteDecadeRepository.findAllByProfileId(profileId),
				profile.getUpdatedAt());
	}

	@Transactional
	public TasteProfileResponse update(Long memberId, TasteProfileUpdateRequest request) {
		List<Genre> genres = findGenres(request.genreIds());
		List<Artist> artists = findArtists(request.artistIds());

		MemberTasteProfile profile = memberTasteProfileRepository.findByMemberId(memberId)
				.orElseGet(() -> createProfile(memberId));
		Long profileId = profile.getId();

		List<MemberTasteGenre> tasteGenres = genres.stream()
				.map(genre -> MemberTasteGenre.of(profile, genre))
				.toList();
		List<MemberTasteArtist> tasteArtists = artists.stream()
				.map(artist -> MemberTasteArtist.of(profile, artist))
				.toList();
		List<MemberTasteDecade> tasteDecades = request.decades().stream()
				.map(decade -> MemberTasteDecade.of(profile, decade))
				.toList();

		memberTasteGenreRepository.replaceAll(profileId, tasteGenres);
		memberTasteArtistRepository.replaceAll(profileId, tasteArtists);
		memberTasteDecadeRepository.replaceAll(profileId, tasteDecades);

		LocalDateTime now = LocalDateTime.now(clock);
		memberTasteProfileRepository.touchUpdatedAt(profileId, now);

		return TasteProfileResponse.of(tasteGenres, tasteArtists, tasteDecades, now);
	}

	/** 조인 엔티티가 프로필 id 로 복합키를 만들기 때문에 여기서 flush 해 id 를 받아 둔다. */
	private MemberTasteProfile createProfile(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		return memberTasteProfileRepository.saveAndFlush(MemberTasteProfile.create(member));
	}

	private List<Genre> findGenres(List<Long> genreIds) {
		Set<Long> uniqueIds = new LinkedHashSet<>(genreIds);
		List<Genre> genres = genreRepository.findAllById(uniqueIds);
		if (genres.size() != uniqueIds.size()) {
			throw new BusinessException(ErrorCode.GENRE_NOT_FOUND);
		}
		return genres;
	}

	private List<Artist> findArtists(List<Long> artistIds) {
		if (artistIds.isEmpty()) {
			return List.of();
		}
		Set<Long> uniqueIds = new LinkedHashSet<>(artistIds);
		List<Artist> artists = artistRepository.findAllById(uniqueIds);
		if (artists.size() != uniqueIds.size()) {
			throw new BusinessException(ErrorCode.ARTIST_NOT_FOUND);
		}
		return artists;
	}
}
