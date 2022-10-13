package com.xperexpo.organizationservice.utils;

import java.util.Locale;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public class Utils {
	private Utils() {
	}

	public static boolean isNullOrEmpty(String value) {
		return value == null || value.isEmpty();
	}

	public static boolean isNullOrEmpty(Long value) {
		return value == null || value == 0;
	}

	public static boolean isNullOrEmpty(MultipartFile file) {
		return file == null || file.isEmpty();
	}

	public static boolean isNull(Object object) {
		return object == null;
	}

	public static double getMegaByteSize(long byteSize) {
		double kilobytes = (byteSize / 1024.0);
		return (kilobytes / 1024.0);
	}

	public static String checkEmailCharsAndWhiteSpaces(String email) {
		char[] turkishChars = { 'ı', 'ğ', 'İ', 'Ğ', 'ç', 'Ç', 'ş', 'Ş', 'ö', 'Ö', 'ü', 'Ü' };
		char[] englishChars = { 'i', 'g', 'I', 'G', 'c', 'C', 's', 'S', 'o', 'O', 'u', 'U' };

		// Match chars
		for (int i = 0; i < turkishChars.length; i++)
			email = email.replaceAll(String.valueOf(turkishChars[i]), String.valueOf(englishChars[i]));
		return email.replaceAll("\\s+", "").toLowerCase();
	}

	public static String formatControlLastName(String lastName, String countryOrLanguageCode) {
		if ("tr".equalsIgnoreCase(countryOrLanguageCode)) {
			lastName = lastName.toUpperCase(Locale.forLanguageTag("tr-TR"));
		} else {
			lastName = lastName.toUpperCase(Locale.ENGLISH);
		}
		return lastName;
	}

	public static String capitalizeName(String name, String countryOrLanguageCode) {
		String[] words = name.split("\\s");
		StringBuilder capitalizeWord = new StringBuilder();
		boolean isTurkish = "tr".equalsIgnoreCase(countryOrLanguageCode);
		Locale locale = isTurkish ? Locale.forLanguageTag("tr-TR") : Locale.ENGLISH;
		for (String w : words) {
			String first = w.substring(0, 1);
			capitalizeWord.append(first.toUpperCase(locale));
			if (w.length() > 1) {
				String afterFirst = w.substring(1);
				capitalizeWord.append(afterFirst.toLowerCase(locale)).append(" ");
			}
		}
		return capitalizeWord.toString().trim();
	}

	public static String generateUUIDForDeleteAction(){
		return "-PASSIVE-"+UUID.randomUUID().toString().substring(0,18);
	}

}
