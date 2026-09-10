package com.groove.recommend.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 측정 결과를 마크다운 표로 렌더한다. 파일 저장은 {@link #write} 가 담당한다. */
public final class EvalReport {

	private EvalReport() {
	}

	/** 기존 리포트와 같은 모양. 경로를 유지해 이전 실행과 diff 가 되게 한다. */
	public static String renderLegacy(EvalMetrics.Summary summary) {
		return """
				# 추천 품질 측정 (precision@10)

				| 항목 | 값 |
				|---|---|
				| 측정 회원 | %d명 |
				| 후보 상품 | %d건 |
				| 홀드아웃 | %d건 |
				| 규칙 추천 적중 | %d건 |
				| 인기순 적중 | %d건 |

				| 지표 | 값 |
				|---|---|
				| recall@10 | %.3f |
				| precision@10 | %.3f |
				| hit-rate@10 | %.3f |
				| 인기순 대조군 recall@10 | %.3f |
				| 무작위 기준선 recall@10 | %.3f |
				| 무작위 대비 | %.1f배 |
				| 인기순 대비 | %.1f배 |
				""".formatted(summary.memberCount(), summary.candidateCount(), summary.holdoutTotal(),
				summary.hitTotal(), summary.popularityHitTotal(), summary.recallMicro(), summary.precisionAtK(),
				summary.hitRateAtK(), summary.popularityRecall(), summary.randomBaseline(), summary.vsRandom(),
				summary.vsPopularity());
	}

	/** 다변화된 지표 + 장르 df 분포 + 후속 이슈 착수 판단용 숫자까지 담은 전체 리포트. */
	public static String renderFull(EvalMetrics.Summary summary, List<EvalMetrics.GenreDf> genreDf,
			long soldQuantityPositiveCount) {
		StringBuilder report = new StringBuilder();
		report.append(renderLegacy(summary));
		report.append('\n');
		report.append(renderExtendedMetrics(summary));
		report.append('\n');
		report.append(renderGenreDf(genreDf));
		report.append('\n');
		report.append(renderFollowUpNumbers(summary, soldQuantityPositiveCount));
		return report.toString();
	}

	private static String renderExtendedMetrics(EvalMetrics.Summary summary) {
		EvalMetrics.Diversity diversity = summary.diversity();
		return """
				## 지표 다변화

				| 지표 | 값 |
				|---|---|
				| recall@10 micro | %.3f |
				| recall@10 macro | %.3f |
				| nDCG@10 | %.3f |
				| MAP@10 | %.3f |
				| catalog coverage@10 (상품) | %.3f |
				| catalog coverage@10 (앨범) | %.3f |
				| Gini | %.3f |
				| distinctArtists/10 | %.3f |
				| distinctLabels/10 | %.3f |
				| distinctDecades/10 | %.3f |
				| maxArtistShare | %.3f |
				| 평균 장르 Jaccard 거리 | %.3f |
				| tasteMatchRate | %.3f |
				""".formatted(summary.recallMicro(), summary.recallMacro(), summary.ndcg(), summary.map(),
				summary.productCoverage(), summary.albumCoverage(), summary.gini(), diversity.distinctArtistsRatio(),
				diversity.distinctLabelsRatio(), diversity.distinctDecadesRatio(), diversity.maxArtistShare(),
				diversity.genreJaccardDistance(), summary.tasteMatchRate());
	}

	private static String renderGenreDf(List<EvalMetrics.GenreDf> genreDf) {
		if (genreDf.isEmpty()) {
			return "## 장르 df 분포\n\n(후보 상품에 장르가 없다)\n";
		}
		long total = genreDf.stream().mapToLong(EvalMetrics.GenreDf::productCount).sum();
		double mean = (double)total / genreDf.size();
		double variance = genreDf.stream()
				.mapToDouble(row -> Math.pow(row.productCount() - mean, 2))
				.sum() / genreDf.size();
		double stdDev = Math.sqrt(variance);

		StringBuilder table = new StringBuilder();
		table.append("## 장르 df 분포\n\n");
		table.append("| 장르 | 상품 수 |\n|---|---|\n");
		for (EvalMetrics.GenreDf row : genreDf) {
			table.append("| %s | %d |\n".formatted(row.genreName(), row.productCount()));
		}
		table.append("\n장르 %d개, 평균 %.1f건, 표준편차 %.1f건.\n".formatted(genreDf.size(), mean, stdDev));
		return table.toString();
	}

	private static String renderFollowUpNumbers(EvalMetrics.Summary summary, long soldQuantityPositiveCount) {
		return """
				## 후속 이슈 판단용 숫자

				| 항목 | 값 |
				|---|---|
				| sold_quantity > 0 상품 수 | %d건 |
				| 인기순 폴백을 탄 회원 수 | %d명 |
				""".formatted(soldQuantityPositiveCount, summary.fallbackCount());
	}

	/**
	 * 폴드×시드 안정성 리포트. 기존 단일 폴드(HOLDOUT_EVERY=5) 수치를 참고선으로 남기고, kind 별로 mean±σ ·
	 * min/max · raw 측정값을 찍는다.
	 */
	public static String renderFolded(EvalMetrics.Summary legacySummary, EvalMetrics.FoldedRun wishRun,
			EvalMetrics.FoldedRun purchaseRun) {
		StringBuilder report = new StringBuilder();
		report.append("""
				# 추천 품질 측정 — 폴드 × 시드 안정성

				기존 단일 폴드(id 오름차순 5개마다 1개, HOLDOUT_EVERY=5) 기준 recall@10 micro = %.3f.
				이 값은 단일 측정이라 노이즈와 개선을 구분할 수 없다. 아래는 같은 회원·같은 랭커를 폴드 ×
				시드로 반복 측정해 표준편차를 낸 결과다.
				""".formatted(legacySummary.recallMicro()));
		report.append('\n');
		report.append(renderFoldedRunSection("위시 홀드아웃", wishRun));
		report.append('\n');
		report.append(renderFoldedRunSection("구매 홀드아웃", purchaseRun));
		return report.toString();
	}

	private static String renderFoldedRunSection(String title, EvalMetrics.FoldedRun run) {
		EvalMetrics.MeasurementStats recall = run.recallStats();
		EvalMetrics.MeasurementStats popularity = run.popularityRecallStats();
		EvalMetrics.MeasurementStats margin = run.marginOverPopularityStats();
		String seeds = run.randomSeeds().stream().map(String::valueOf).reduce((left, right) -> left + ", " + right)
				.orElse("");

		StringBuilder section = new StringBuilder();
		section.append("""
				## %s (kind=%s, foldCount=%d, 시드=%s)

				| 항목 | 값 |
				|---|---|
				| 측정 수 | %d |
				| recall@10 micro mean ± σ | %.3f ± %.3f |
				| min / max | %.3f / %.3f |
				| mean - 2σ (회귀 게이트 하한) | %.3f |
				| 무작위 기준선 | %.3f |
				| 인기순 대조군 mean ± σ | %.3f ± %.3f |
				| recall - 인기순 margin mean ± σ | %.3f ± %.3f |
				| 폴백 발생 횟수(측정 합) | %d |

				"""
				.formatted(title, run.kind(), run.foldCount(), seeds, recall.count(), recall.mean(),
						recall.stdDev(), recall.min(), recall.max(), recall.lowerBound(), run.randomBaseline(),
						popularity.mean(), popularity.stdDev(), margin.mean(), margin.stdDev(),
						run.totalFallbackOccurrences()));
		section.append(renderRawMeasurements(run));
		return section.toString();
	}

	private static String renderRawMeasurements(EvalMetrics.FoldedRun run) {
		StringBuilder table = new StringBuilder();
		table.append("| 시드 | fold | recall@10 | 인기순 recall@10 | 폴백 회원 |\n|---|---|---|---|---|\n");
		for (EvalMetrics.Measurement measurement : run.measurements()) {
			EvalMetrics.Summary summary = measurement.summary();
			table.append("| %d | %d | %.3f | %.3f | %d |\n".formatted(measurement.randomSeed(),
					measurement.foldIndex(), summary.recallMicro(), summary.popularityRecall(),
					summary.fallbackCount()));
		}
		return table.toString();
	}

	public static void write(Path path, String content) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
	}
}
