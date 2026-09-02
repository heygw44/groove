package com.groove.file.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.groove.file.config.FileProperties;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalFileStorage implements FileStorage {

	private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	private final FileProperties fileProperties;

	private Path rootPath;

	@PostConstruct
	public void init() {
		this.rootPath = Path.of(fileProperties.uploadPath()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(rootPath);
		} catch (IOException e) {
			log.error("업로드 루트 디렉터리 생성 실패: {}", rootPath, e);
			throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
		}
	}

	@Override
	public String store(MultipartFile file, String extension) {
		String datePath = LocalDate.now().format(DATE_PATH_FORMATTER);
		String fileName = UUID.randomUUID() + "." + extension;
		String key = datePath + "/" + fileName;
		Path target = rootPath.resolve(key);

		try {
			Files.createDirectories(target.getParent());
			file.transferTo(target);
		} catch (IOException e) {
			log.error("파일 저장 실패: {}", key, e);
			throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
		}

		return key;
	}
}
