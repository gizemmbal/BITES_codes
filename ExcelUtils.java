package com.xperexpo.organizationservice.utils;

import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;

public final class ExcelUtils {

	private ExcelUtils() {
		// initlenmemesi constructor private yapıldı.
	}

	public static Sheet initExcelWorkBook(Workbook workbook, String sheetName, List<String> headers,
			Set<Integer> headerIndexesForCustomSize, int columnSize) {
		Sheet sheet = workbook.createSheet(sheetName);
		Row headerRow = sheet.createRow(0);
		XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
		XSSFFont font = (XSSFFont) workbook.createFont();
		font.setBold(true);
		style.setFont(font);
		style.setAlignment(HorizontalAlignment.CENTER);

		for (int col = 0; col < headers.size(); col++) {
			Cell cell = headerRow.createCell(col);
			cell.setCellStyle(style);
			cell.setCellValue(headers.get(col));
			if (!headerIndexesForCustomSize.contains(col)) {
				sheet.setColumnWidth(col, columnSize);
			} else {
				sheet.autoSizeColumn(col);
			}
		}
		return sheet;
	}

	public static void setColumnWidthAndAlignment(Workbook workbook, Sheet sheet, Row row, int columnSize, int cellNumber,
			boolean isAutoSize) {
		XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
		style.setAlignment(HorizontalAlignment.CENTER);
		for (int col = 0; col < cellNumber; col++) {
			Cell cell = row.getCell(col);
			cell.setCellStyle(style);

			if (isAutoSize) {
				sheet.autoSizeColumn(col);
			} else {
				sheet.setColumnWidth(col, columnSize);
			}
		}
	}
}
