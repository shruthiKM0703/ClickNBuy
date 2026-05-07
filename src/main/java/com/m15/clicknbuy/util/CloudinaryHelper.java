package com.m15.clicknbuy.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CloudinaryHelper {

	@Value("${upload.dir:uploads/products}")
	private String uploadDir;

	@PostConstruct
	public void init() {
		try {
			Path uploadPath = Paths.get(uploadDir);
			if (!Files.exists(uploadPath)) {
				Files.createDirectories(uploadPath);
				log.info("Upload directory created: {}", uploadPath.toAbsolutePath());
			} else {
				log.info("Upload directory ready: {}", uploadPath.toAbsolutePath());
			}
		} catch (IOException e) {
			log.error("Failed to create upload directory: {}", e.getMessage());
		}
	}

	/**
	 * Saves an image to local storage and returns the accessible URL path.
	 *
	 * @param image The image file to save.
	 * @return The relative URL to access the image (e.g. /uploads/products/xyz.jpg)
	 */
	public String saveToCloudinary(MultipartFile image) {
		if (image == null || image.isEmpty()) {
			return "https://placehold.co/600x400?text=No+Image";
		}

		try {
			String originalFilename = image.getOriginalFilename();
			String extension = (originalFilename != null && originalFilename.contains("."))
					? originalFilename.substring(originalFilename.lastIndexOf("."))
					: ".jpg";

			String uniqueFilename = UUID.randomUUID().toString() + extension;
			Path uploadPath = Paths.get(uploadDir);
			Path filePath = uploadPath.resolve(uniqueFilename);

			Files.copy(image.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
			log.info("Image saved locally: {}", filePath.toAbsolutePath());

			return "/uploads/products/" + uniqueFilename;
		} catch (IOException e) {
			log.error("Failed to save image locally: {}", e.getMessage());
			return "https://placehold.co/600x400?text=Upload+Failed";
		}
	}

	/**
	 * Deletes an image from local storage.
	 *
	 * @param imagePath The relative URL of the image (e.g. /uploads/products/xyz.jpg)
	 */
	public void deleteFromCloudinary(String imagePath) {
		if (imagePath == null || imagePath.isEmpty() || imagePath.contains("placehold.co")) {
			return;
		}

		try {
			// Strip leading slash and resolve against upload dir
			String filename = imagePath.startsWith("/uploads/products/")
					? imagePath.substring("/uploads/products/".length())
					: imagePath;

			Path filePath = Paths.get(uploadDir).resolve(filename);
			if (Files.exists(filePath)) {
				Files.delete(filePath);
				log.info("Image deleted: {}", filePath.toAbsolutePath());
			}
		} catch (IOException e) {
			log.error("Failed to delete image: {}", e.getMessage());
		}
	}
}
