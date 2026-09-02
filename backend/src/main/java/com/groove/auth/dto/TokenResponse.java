package com.groove.auth.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

	private static final String TOKEN_TYPE = "Bearer";

	public static TokenResponse from(AuthTokens tokens) {
		return new TokenResponse(tokens.accessToken(), TOKEN_TYPE, tokens.expiresIn());
	}
}
