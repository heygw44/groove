package com.groove.review.dto;

/** 별점별 리뷰 개수 집계 프로젝션. */
public record ReviewRatingCount(int rating, long count) {
}
