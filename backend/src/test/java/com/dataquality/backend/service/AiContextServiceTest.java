package com.dataquality.backend.service;

import com.dataquality.backend.dto.dashboard.DashboardSummaryResponse;
import com.dataquality.backend.dto.dashboard.DatasetStatusDistribution;
import com.dataquality.backend.model.Dataset;
import com.dataquality.backend.model.DatasetStatus;
import com.dataquality.backend.model.QualityReport;
import com.dataquality.backend.model.RuleType;
import com.dataquality.backend.model.ValidationRule;
import com.dataquality.backend.repository.DatasetRepository;
import com.dataquality.backend.repository.QualityReportRepository;
import com.dataquality.backend.repository.ValidationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiContextServiceTest {

    @Mock private DatasetRepository datasetRepository;
    @Mock private QualityReportRepository qualityReportRepository;
    @Mock private ValidationRuleRepository validationRuleRepository;
    @Mock private DashboardService dashboardService;

    @InjectMocks
    private AiContextService aiContextService;

    @BeforeEach
    void setUp() {
        when(dashboardService.getSummary()).thenReturn(
                new DashboardSummaryResponse(87.5, 12L, 34L, 560L, List.of(), List.of(), List.of(), List.of(), List.of(),
                        new DatasetStatusDistribution(0, 0, 0), List.of(), List.of())
        );
        when(validationRuleRepository.findByIsActive(true)).thenReturn(List.of());
    }

    @Test
    void alwaysIncludesTheDashboardSummary() {
        when(datasetRepository.findAllByOrderByUploadedAtDesc(any(Pageable.class)))
                .thenReturn(emptyPage());
        when(qualityReportRepository.findAllByOrderByGeneratedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        String context = aiContextService.buildContext("How is everything looking?");

        assertThat(context).contains("average quality score 87.5%");
        assertThat(context).contains("12 files analyzed");
        assertThat(context).contains("34 total anomalies");
    }

    @Test
    void includesActiveValidationRulesByName() {
        ValidationRule rule = new ValidationRule();
        rule.setRuleName("email-format");
        rule.setColumnName("email");
        rule.setRuleType(RuleType.REGEX);
        when(validationRuleRepository.findByIsActive(true)).thenReturn(List.of(rule));
        when(datasetRepository.findAllByOrderByUploadedAtDesc(any(Pageable.class)))
                .thenReturn(emptyPage());
        when(qualityReportRepository.findAllByOrderByGeneratedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        String context = aiContextService.buildContext("What rules are active?");

        assertThat(context).contains("email-format");
        assertThat(context).contains("column: email");
    }

    @Test
    void includesTheLatestReportWhenTheMessageNamesADataset() {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setFileName("customers.csv");
        dataset.setStatus(DatasetStatus.COMPLETED);
        when(datasetRepository.findAllByOrderByUploadedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dataset)));

        QualityReport report = new QualityReport();
        report.setQualityScore(72.0);
        report.setValidRows(72);
        report.setInvalidRows(28);
        report.setTotalRows(100);
        report.setGeneratedAt(LocalDateTime.now());
        report.setReportDetails("[]");
        when(qualityReportRepository.findByDatasetId(1L)).thenReturn(List.of(report));

        String context = aiContextService.buildContext("How does customers.csv look?");

        assertThat(context).contains("customers.csv");
        assertThat(context).contains("score 72.0");
    }

    @Test
    void fallsBackToRecentReportsWhenNoDatasetIsNamed() {
        when(datasetRepository.findAllByOrderByUploadedAtDesc(any(Pageable.class)))
                .thenReturn(emptyPage());

        Dataset dataset = new Dataset();
        dataset.setFileName("orders.csv");
        QualityReport report = new QualityReport();
        report.setDataset(dataset);
        report.setQualityScore(90.0);
        report.setInvalidRows(5);
        when(qualityReportRepository.findAllByOrderByGeneratedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report)));

        String context = aiContextService.buildContext("How is data quality overall?");

        assertThat(context).contains("orders.csv");
        assertThat(context).contains("score 90.0");
    }

    private Page<Dataset> emptyPage() {
        return new PageImpl<>(List.of());
    }
}
