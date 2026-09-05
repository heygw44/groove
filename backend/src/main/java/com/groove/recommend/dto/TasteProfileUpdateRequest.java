package com.groove.recommend.dto;

import java.util.List;
import java.util.Set;

import com.groove.recommend.entity.Decade;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 취향 프로필 생성/수정 요청. 부분 갱신이 없는 전체 교체라 세 목록 모두 필수이며, 비우려면 빈 배열을 보낸다. */
public record TasteProfileUpdateRequest(
		@NotNull
		@Size(min = 1, max = 5, message = "선호 장르는 1~5개여야 합니다.")
		List<@NotNull Long> genreIds,

		@NotNull
		@Size(max = 5, message = "선호 아티스트는 5개 이하여야 합니다.")
		List<@NotNull Long> artistIds,

		@NotNull
		@Size(max = 3, message = "선호 연대는 3개 이하여야 합니다.")
		List<@NotNull Decade> decades
) {

	@AssertTrue(message = "선호 장르에 중복이 있습니다.")
	public boolean isGenreIdsDistinct() {
		return isDistinct(this.genreIds);
	}

	@AssertTrue(message = "선호 아티스트에 중복이 있습니다.")
	public boolean isArtistIdsDistinct() {
		return isDistinct(this.artistIds);
	}

	@AssertTrue(message = "선호 연대에 중복이 있습니다.")
	public boolean isDecadesDistinct() {
		return isDistinct(this.decades);
	}

	private boolean isDistinct(List<?> values) {
		return values == null || Set.copyOf(values).size() == values.size();
	}
}
