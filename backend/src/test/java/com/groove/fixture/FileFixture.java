package com.groove.fixture;

import org.springframework.mock.web.MockMultipartFile;

public final class FileFixture {

	private static final long OVER_MAX_SIZE = 5 * 1024 * 1024L + 1;

	private FileFixture() {
	}

	public static MockMultipartFile image(String originalFilename, String contentType, byte[] content) {
		return new MockMultipartFile("file", originalFilename, contentType, content);
	}

	public static MockMultipartFile image() {
		return image("cover.jpg", "image/jpeg", "image-content".getBytes());
	}

	public static MockMultipartFile largeImage() {
		byte[] content = new byte[(int) OVER_MAX_SIZE];
		return image("cover.jpg", "image/jpeg", content);
	}

	public static MockMultipartFile emptyImage() {
		return new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[0]);
	}
}
