package com.dataquality.backend.service;

import com.dataquality.backend.dto.correction.CorrectionRequest;
import com.dataquality.backend.exception.UnsupportedFileTypeException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites a dataset file with a set of cell corrections applied, one row/column
 * at a time. rowIndex is 1-based and means "the Nth data row, header excluded" -
 * the same convention the Python analysis job uses when it reports anomalies
 * (see quality_check.py's zipWithIndex-based row_index / read_excel_as_spark's
 * first-sheet read), so a correction always targets the exact row the Anomaly
 * Explorer showed it against, in the same sheet that was actually analyzed.
 */
@Service
public class FileCorrectionService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("csv", "json", "xlsx");
    private static final char[] DELIMITER_CANDIDATES = {',', ';', '\t', '|'};

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record AppliedCorrection(Integer rowIndex, String columnName, String oldValue, String newValue) {}

    public boolean isSupported(String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }

    public List<AppliedCorrection> apply(Path source, Path target, String extension, List<CorrectionRequest> corrections) throws IOException {
        String ext = extension.toLowerCase();
        if ("csv".equals(ext)) {
            return applyCsv(source, target, corrections);
        }
        if ("json".equals(ext)) {
            return applyJson(source, target, corrections);
        }
        if ("xlsx".equals(ext)) {
            return applyXlsx(source, target, corrections);
        }
        throw new UnsupportedFileTypeException("La correction directe n'est pas supportée pour les fichiers ." + ext);
    }

    private List<AppliedCorrection> applyCsv(Path source, Path target, List<CorrectionRequest> corrections) throws IOException {
        char delimiter = detectDelimiter(source);
        List<String> headers;
        List<Map<String, String>> rows = new ArrayList<>();

        CSVFormat parseFormat = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();

        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8);
             CSVParser parser = parseFormat.parse(reader)) {
            headers = new ArrayList<>(parser.getHeaderNames());
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : headers) {
                    row.put(header, record.isSet(header) ? record.get(header) : "");
                }
                rows.add(row);
            }
        }

        List<AppliedCorrection> applied = new ArrayList<>();
        for (CorrectionRequest correction : corrections) {
            Map<String, String> row = rowAt(rows, correction.rowIndex());
            requireColumn(headers.contains(correction.columnName()), correction.columnName());
            String oldValue = row.get(correction.columnName());
            row.put(correction.columnName(), correction.newValue());
            applied.add(new AppliedCorrection(correction.rowIndex(), correction.columnName(), oldValue, correction.newValue()));
        }

        CSVFormat writeFormat = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader(headers.toArray(new String[0]))
                .build();

        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, writeFormat)) {
            for (Map<String, String> row : rows) {
                List<String> values = headers.stream().map(row::get).toList();
                printer.printRecord(values);
            }
        }

        return applied;
    }

    @SuppressWarnings("unchecked")
    private List<AppliedCorrection> applyJson(Path source, Path target, List<CorrectionRequest> corrections) throws IOException {
        String content = Files.readString(source, StandardCharsets.UTF_8).strip();
        boolean isJsonArray = content.startsWith("[");
        List<Map<String, Object>> rows = new ArrayList<>();

        if (isJsonArray) {
            rows = objectMapper.readValue(content, new TypeReference<List<Map<String, Object>>>() {});
        } else {
            // JSON-lines: one object per non-blank line, Spark's other supported shape for spark.read.json().
            for (String line : content.split("\\R")) {
                if (line.isBlank()) continue;
                rows.add(objectMapper.readValue(line, new TypeReference<Map<String, Object>>() {}));
            }
        }

        List<AppliedCorrection> applied = new ArrayList<>();
        for (CorrectionRequest correction : corrections) {
            Map<String, Object> row = rowAt(rows, correction.rowIndex());
            requireColumn(row.containsKey(correction.columnName()), correction.columnName());
            Object oldValue = row.get(correction.columnName());
            row.put(correction.columnName(), correction.newValue());
            applied.add(new AppliedCorrection(
                    correction.rowIndex(), correction.columnName(),
                    oldValue == null ? null : oldValue.toString(), correction.newValue()));
        }

        if (isJsonArray) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), rows);
        } else {
            StringBuilder out = new StringBuilder();
            for (Map<String, Object> row : rows) {
                out.append(objectMapper.writeValueAsString(row)).append('\n');
            }
            Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
        }

        return applied;
    }

    /**
     * Edits cells in place on the first sheet of the workbook (matching
     * quality_check.py's read_excel_as_spark, which only ever analyzes sheet
     * 0) and re-saves the whole file. POI only mutates the cells this method
     * touches, so every other cell's value AND formatting/style is preserved
     * untouched - unlike the CSV/JSON paths, this isn't a full rewrite from
     * scratch, it's a real in-place edit of the loaded workbook.
     *
     * The new value is always written as a plain string, exactly like the
     * CSV/JSON paths - re-analysis re-infers the column's real type from the
     * corrected file rather than this method guessing a type back.
     */
    private List<AppliedCorrection> applyXlsx(Path source, Path target, List<CorrectionRequest> corrections) throws IOException {
        List<AppliedCorrection> applied = new ArrayList<>();

        try (InputStream in = Files.newInputStream(source);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Feuille Excel sans en-tête");
            }
            Map<String, Integer> columnIndexByName = new LinkedHashMap<>();
            for (Cell headerCell : headerRow) {
                columnIndexByName.put(cellToString(headerCell), headerCell.getColumnIndex());
            }

            int lastDataRow = sheet.getLastRowNum();
            for (CorrectionRequest correction : corrections) {
                if (correction.rowIndex() < 1 || correction.rowIndex() > lastDataRow) {
                    throw new IllegalArgumentException("Ligne introuvable dans le fichier: " + correction.rowIndex());
                }
                Integer columnIndex = columnIndexByName.get(correction.columnName());
                if (columnIndex == null) {
                    throw new IllegalArgumentException("Colonne introuvable dans le fichier: " + correction.columnName());
                }

                Row row = sheet.getRow(correction.rowIndex());
                if (row == null) {
                    row = sheet.createRow(correction.rowIndex());
                }
                Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String oldValue = cellToString(cell);
                cell.setCellValue(correction.newValue());
                applied.add(new AppliedCorrection(correction.rowIndex(), correction.columnName(), oldValue, correction.newValue()));
            }

            try (OutputStream out = Files.newOutputStream(target)) {
                workbook.write(out);
            }
        }

        return applied;
    }

    private String cellToString(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double value = cell.getNumericCellValue();
                yield (value == Math.floor(value) && !Double.isInfinite(value))
                        ? String.valueOf((long) value)
                        : String.valueOf(value);
            }
            default -> "";
        };
    }

    private <T> T rowAt(List<T> rows, Integer rowIndex) {
        int idx = rowIndex - 1;
        if (idx < 0 || idx >= rows.size()) {
            throw new IllegalArgumentException("Ligne introuvable dans le fichier: " + rowIndex);
        }
        return rows.get(idx);
    }

    private void requireColumn(boolean present, String columnName) {
        if (!present) {
            throw new IllegalArgumentException("Colonne introuvable dans le fichier: " + columnName);
        }
    }

    private char detectDelimiter(Path source) throws IOException {
        String firstLine;
        try (var lines = Files.lines(source, StandardCharsets.UTF_8)) {
            firstLine = lines.findFirst().orElse("");
        }
        char best = ',';
        long bestCount = -1;
        for (char candidate : DELIMITER_CANDIDATES) {
            long count = firstLine.chars().filter(c -> c == candidate).count();
            if (count > bestCount) {
                bestCount = count;
                best = candidate;
            }
        }
        return best;
    }
}
