package com.xperexpo.organizationservice.utils;


import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

import com.xperexpo.organizationservice.payload.ZoneDateTime;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TimeUtils {

    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";
    private static final String YYYY_MM_DD_HH_MM = "yyyy-MM-dd HH:mm";

    public static ZoneDateTime getGMTFormatter(LocalDateTime time, String sourceLocalTimeZone) {
		if (time != null && !Utils.isNullOrEmpty(sourceLocalTimeZone)) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM_SS);

        ZonedDateTime atZone = time.atZone(TimeZone.getTimeZone(sourceLocalTimeZone).toZoneId());
        return new ZoneDateTime(time.format(dateTimeFormatter), atZone.getOffset().toString());
	}
	return null;

    }

	public static LocalDateTime getDateTimeZone(LocalDateTime time, String sourceLocalTimeZone) {

		return time.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of(sourceLocalTimeZone)).toLocalDateTime();
	}

	public static LocalDateTime parseDateTime(String date, String sourceLocalTimeZone) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM);
		return LocalDateTime.parse(date, formatter).atZone(ZoneId.of(sourceLocalTimeZone))
				.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
	}

    public static LocalDateTime parseDateTime(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(YYYY_MM_DD_HH_MM);
        return LocalDateTime.parse(date, formatter);
    }


    public static LocalDateTime getUTCTime(LocalDateTime time, String sourceLocalTimeZone) {
        return time.atZone(ZoneId.of(sourceLocalTimeZone)).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
    }
}
