package com.dataquality.backend.service;

import com.dataquality.backend.model.QualityReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders a single quality report as a downloadable PDF - a fixed offline
 * record of an analysis result, useful for sharing with people who don't have
 * (or shouldn't need) an account on the platform, or for audit/compliance
 * archiving outside the app itself.
 */
@Service
public class PdfReportService {

    private static final Color BRAND_BLUE = new Color(0x3B, 0x6E, 0xA8);
    private static final Color SUCCESS_GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Color WARNING_AMBER = new Color(0xB2, 0x6A, 0x00);
    private static final Color ERROR_RED = new Color(0xC6, 0x28, 0x28);
    private static final Color HEADER_GREY = new Color(0xF5, 0xF5, 0xF5);
    // All PDF text is fixed English (a canonical, archivable document format,
    // independent of the viewer's in-app UI language) - Locale.ENGLISH keeps the
    // date formatting consistent with that instead of following the server JVM's
    // default locale.
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] generate(QualityReport report) {
        Document document = new Document(PageSize.A4, 50, 50, 60, 50);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            addHeader(document, report);
            addSummary(document, report);
            addChecksTable(document, report);
            addFooter(document);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate PDF report", e);
        }
    }

    private void addHeader(Document document, QualityReport report) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND_BLUE);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);

        Paragraph title = new Paragraph("Data Quality Report", titleFont);
        title.setSpacingAfter(4);
        document.add(title);

        Paragraph fileName = new Paragraph(report.getDataset().getFileName(), subtitleFont);
        fileName.setSpacingAfter(2);
        document.add(fileName);

        Paragraph generated = new Paragraph(
                "Generated " + report.getGeneratedAt().format(DATE_FORMAT),
                subtitleFont
        );
        generated.setSpacingAfter(20);
        document.add(generated);
    }

    private void addSummary(Document document, QualityReport report) throws DocumentException {
        double score = report.getQualityScore() != null ? report.getQualityScore() : 0;
        Color scoreColor = score >= 80 ? SUCCESS_GREEN : score >= 50 ? WARNING_AMBER : ERROR_RED;

        PdfPTable summary = new PdfPTable(4);
        summary.setWidthPercentage(100);
        summary.setSpacingAfter(20);

        addSummaryCell(summary, "Quality Score", String.format(Locale.ENGLISH, "%.1f%%", score), scoreColor);
        addSummaryCell(summary, "Total Rows", String.valueOf(nullToZero(report.getTotalRows())), Color.BLACK);
        addSummaryCell(summary, "Valid Rows", String.valueOf(nullToZero(report.getValidRows())), SUCCESS_GREEN);
        addSummaryCell(summary, "Invalid Rows", String.valueOf(nullToZero(report.getInvalidRows())), ERROR_RED);

        document.add(summary);
    }

    private void addSummaryCell(PdfPTable table, String label, String value, Color valueColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(HEADER_GREY);
        cell.setBorderColor(Color.LIGHT_GRAY);
        cell.setPadding(10);

        Paragraph labelParagraph = new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
        Paragraph valueParagraph = new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, valueColor));
        valueParagraph.setSpacingBefore(4);

        cell.addElement(labelParagraph);
        cell.addElement(valueParagraph);
        table.addCell(cell);
    }

    private void addChecksTable(Document document, QualityReport report) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.BLACK);
        Paragraph section = new Paragraph("Checks performed", sectionFont);
        section.setSpacingAfter(10);
        document.add(section);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4, 1.5f, 1.5f, 1.5f});

        addHeaderCell(table, "Check");
        addHeaderCell(table, "Result");
        addHeaderCell(table, "Anomalies");
        addHeaderCell(table, "% Anomalies");

        for (JsonNode check : parseChecks(report.getReportDetails())) {
            boolean success = check.path("success").asBoolean(false);
            table.addCell(bodyCell(check.path("check_name").asText("-")));
            table.addCell(bodyCell(success ? "Passed" : "Failed", success ? SUCCESS_GREEN : ERROR_RED));
            table.addCell(bodyCell(String.valueOf(check.path("unexpected_count").asInt(0))));
            table.addCell(bodyCell(String.format(Locale.ENGLISH, "%.1f%%", check.path("unexpected_pct").asDouble(0))));
        }

        document.add(table);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
        cell.setBackgroundColor(BRAND_BLUE);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private PdfPCell bodyCell(String text) {
        return bodyCell(text, Color.BLACK);
    }

    private PdfPCell bodyCell(String text, Color color) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 10, color)));
        cell.setPadding(8);
        cell.setBorderColor(Color.LIGHT_GRAY);
        return cell;
    }

    private void addFooter(Document document) throws DocumentException {
        Paragraph footer = new Paragraph(
                "Generated by DQMonitor - Enhanced Technologies",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY)
        );
        footer.setSpacingBefore(30);
        document.add(footer);
    }

    private Iterable<JsonNode> parseChecks(String reportDetailsJson) {
        if (reportDetailsJson == null || reportDetailsJson.isBlank()) {
            return java.util.List.of();
        }
        try {
            return objectMapper.readTree(reportDetailsJson);
        } catch (IOException e) {
            return java.util.List.of();
        }
    }

    private int nullToZero(Integer value) {
        return value != null ? value : 0;
    }
}
