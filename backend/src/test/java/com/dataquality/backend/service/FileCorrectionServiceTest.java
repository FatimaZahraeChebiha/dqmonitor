package com.dataquality.backend.service;

import com.dataquality.backend.dto.correction.CorrectionRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileCorrectionServiceTest {

    private final FileCorrectionService service = new FileCorrectionService();

    @Test
    void isSupportedAcceptsCsvJsonAndXlsx() {
        assertThat(service.isSupported("csv")).isTrue();
        assertThat(service.isSupported("CSV")).isTrue();
        assertThat(service.isSupported("json")).isTrue();
        assertThat(service.isSupported("xlsx")).isTrue();
        assertThat(service.isSupported("xls")).isFalse();
    }

    @Test
    void correctsACellInCsvWithoutTouchingOtherRowsOrColumns(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("source.csv");
        Files.writeString(source, "id,nom,email\n1,Amine,amine@x.com\n2,Sara,\n3,Youssef,youssef@x.com\n", StandardCharsets.UTF_8);
        Path target = dir.resolve("target.csv");

        List<FileCorrectionService.AppliedCorrection> applied = service.apply(
                source, target, "csv",
                List.of(new CorrectionRequest(2, "email", "sara@x.com")));

        assertThat(applied).hasSize(1);
        assertThat(applied.get(0).oldValue()).isEmpty();
        assertThat(applied.get(0).newValue()).isEqualTo("sara@x.com");

        String result = Files.readString(target, StandardCharsets.UTF_8);
        assertThat(result).contains("2,Sara,sara@x.com");
        assertThat(result).contains("1,Amine,amine@x.com");
        assertThat(result).contains("3,Youssef,youssef@x.com");
    }

    @Test
    void preservesCommasInsideQuotedCsvFieldsUntouchedByTheCorrection(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("source.csv");
        // The address column deliberately contains a comma inside quotes - a naive
        // split-on-comma parser would corrupt this row even though only "ville" changes.
        Files.writeString(source, "nom,adresse,ville\n\"Agence A\",\"12, Rue de la Paix\",Casablanca\n", StandardCharsets.UTF_8);
        Path target = dir.resolve("target.csv");

        List<FileCorrectionService.AppliedCorrection> applied = service.apply(
                source, target, "csv",
                List.of(new CorrectionRequest(1, "ville", "Rabat")));

        assertThat(applied.get(0).oldValue()).isEqualTo("Casablanca");
        String result = Files.readString(target, StandardCharsets.UTF_8);
        assertThat(result).contains("12, Rue de la Paix");
        assertThat(result).contains("Rabat");
        assertThat(result).doesNotContain("Casablanca");
    }

    @Test
    void correctsAJsonArrayFile(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("source.json");
        Files.writeString(source, "[{\"id\":1,\"age\":999},{\"id\":2,\"age\":30}]", StandardCharsets.UTF_8);
        Path target = dir.resolve("target.json");

        List<FileCorrectionService.AppliedCorrection> applied = service.apply(
                source, target, "json",
                List.of(new CorrectionRequest(1, "age", "40")));

        assertThat(applied.get(0).oldValue()).isEqualTo("999");
        String result = Files.readString(target, StandardCharsets.UTF_8);
        assertThat(result).contains("\"age\" : \"40\"");
        assertThat(result).contains("\"id\" : 2");
    }

    @Test
    void correctsAJsonLinesFile(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("source.json");
        Files.writeString(source, "{\"id\":1,\"age\":999}\n{\"id\":2,\"age\":30}\n", StandardCharsets.UTF_8);
        Path target = dir.resolve("target.json");

        service.apply(source, target, "json", List.of(new CorrectionRequest(2, "age", "31")));

        String result = Files.readString(target, StandardCharsets.UTF_8);
        assertThat(result.lines()).hasSize(2);
        // The corrected value is written as the string the client sent, so it
        // round-trips as a JSON string ("31"), not a re-inferred number.
        assertThat(result).contains("\"age\":\"31\"");
    }

    @Test
    void rejectsAnOutOfRangeRowIndex(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("source.csv");
        Files.writeString(source, "id\n1\n2\n", StandardCharsets.UTF_8);
        Path target = dir.resolve("target.csv");

        assertThatThrownBy(() -> service.apply(source, target, "csv", List.of(new CorrectionRequest(99, "id", "9"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownColumn(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("source.csv");
        Files.writeString(source, "id\n1\n", StandardCharsets.UTF_8);
        Path target = dir.resolve("target.csv");

        assertThatThrownBy(() -> service.apply(source, target, "csv", List.of(new CorrectionRequest(1, "nope", "x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void correctsACellInXlsxWithoutTouchingOtherCells(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("source.xlsx");
        writeXlsx(source,
                new String[] {"id", "nom", "age"},
                new Object[][] {
                        {1, "Amine", 28},
                        {2, "Sara", 999},
                        {3, "Youssef", 41},
                });
        Path target = dir.resolve("target.xlsx");

        List<FileCorrectionService.AppliedCorrection> applied = service.apply(
                source, target, "xlsx",
                List.of(new CorrectionRequest(2, "age", "34")));

        assertThat(applied).hasSize(1);
        assertThat(applied.get(0).oldValue()).isEqualTo("999");
        assertThat(applied.get(0).newValue()).isEqualTo("34");

        try (InputStream in = Files.newInputStream(target); Workbook workbook = WorkbookFactory.create(in)) {
            var sheet = workbook.getSheetAt(0);
            // Sheet row 0 is the header, so rowIndex=2 (Sara, the 2nd data row) is sheet row 2.
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("34");
            // Untouched rows keep their original numeric cells, unaffected by the correction.
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(28.0);
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("Youssef");
        }
    }

    @Test
    void rejectsAnOutOfRangeRowIndexInXlsx(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("source.xlsx");
        writeXlsx(source, new String[] {"id"}, new Object[][] {{1}, {2}});
        Path target = dir.resolve("target.xlsx");

        assertThatThrownBy(() -> service.apply(source, target, "xlsx", List.of(new CorrectionRequest(99, "id", "9"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownColumnInXlsx(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("source.xlsx");
        writeXlsx(source, new String[] {"id"}, new Object[][] {{1}});
        Path target = dir.resolve("target.xlsx");

        assertThatThrownBy(() -> service.apply(source, target, "xlsx", List.of(new CorrectionRequest(1, "nope", "x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void writeXlsx(Path path, String[] headers, Object[][] rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("data");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = rows[r][c];
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else {
                        row.createCell(c).setCellValue(String.valueOf(value));
                    }
                }
            }
            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        }
    }
}
