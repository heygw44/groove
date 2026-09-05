package com.groove.file.storage;

import org.springframework.web.multipart.MultipartFile;

/** 파일 저장소 추상화. 로컬 디스크 구현체 외에 S3 등으로 확장할 수 있는 지점. */
public interface FileStorage {

	/**
	 * 파일을 저장하고 상대 키를 반환한다. 예: {@code "2026/09/02/3f2a1c9e-....jpg"}
	 */
	String store(MultipartFile file, String extension);
}
