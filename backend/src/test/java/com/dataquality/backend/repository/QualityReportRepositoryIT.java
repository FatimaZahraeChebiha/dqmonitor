package com.dataquality.backend.repository;

import com.dataquality.backend.model.Dataset;
import com.dataquality.backend.model.DatasetStatus;
import com.dataquality.backend.model.QualityReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises findLatestPerDataset against a real MySQL instance rather than
 * mocking the repository - this is exactly the query a user reported as buggy
 * (repeated re-analyses of one dataset were crowding the Dashboard's recent
 * activity feed), so the JPQL correlated subquery itself needs to be proven
 * correct, not just that the service maps whatever the repository returns.
 *
 * The dev database this runs against already has real data from manual UI
 * testing, so assertions filter by the exact dataset id created in each test
 * rather than asserting on the full result set - the database is shared, not
 * empty, and a large page size is used so a test's own rows can't be pushed
 * out by unrelated pre-existing reports.
 */
@SpringBootTest
@Transactional
class QualityReportRepositoryIT {

    private static final int LARGE_PAGE = 1000;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private QualityReportRepository qualityReportRepository;

    private Dataset saveDataset(String fileName) {
        Dataset dataset = new Dataset();
        dataset.setFileName(fileName);
        dataset.setStoredFileName(java.util.UUID.randomUUID() + ".csv");
        dataset.setFilePath("/tmp/" + fileName);
        dataset.setFileSize(1024L);
        dataset.setStatus(DatasetStatus.COMPLETED);
        return datasetRepository.save(dataset);
    }

    private void saveReport(Dataset dataset, double score, LocalDateTime generatedAt) {
        QualityReport report = new QualityReport();
        report.setDataset(dataset);
        report.setQualityScore(score);
        report.setTotalRows(10);
        report.setValidRows(8);
        report.setInvalidRows(2);
        report.setGeneratedAt(generatedAt);
        qualityReportRepository.save(report);
    }

    private List<QualityReport> entriesFor(List<QualityReport> results, Long datasetId) {
        return results.stream().filter(r -> r.getDataset().getId().equals(datasetId)).toList();
    }

    @Test
    void returnsOnlyTheMostRecentReportPerDataset() {
        LocalDateTime now = LocalDateTime.now();
        Dataset repeatedlyAnalyzed = saveDataset("dataset_anomalies_it_test.csv");
        saveReport(repeatedlyAnalyzed, 0.0, now.minusHours(3));
        saveReport(repeatedlyAnalyzed, 0.0, now.minusHours(2));
        saveReport(repeatedlyAnalyzed, 53.3, now.minusMinutes(5));

        List<QualityReport> latestPerDataset =
                qualityReportRepository.findLatestPerDataset(PageRequest.of(0, LARGE_PAGE));

        List<QualityReport> forThisDataset = entriesFor(latestPerDataset, repeatedlyAnalyzed.getId());

        // Exactly one row for this dataset, not three - the two earlier
        // re-analyses must not crowd out (or duplicate alongside) the latest one.
        assertThat(forThisDataset).hasSize(1);
        assertThat(forThisDataset.get(0).getQualityScore()).isEqualTo(53.3);
    }

    @Test
    void doesNotLetARepeatedlyAnalyzedDatasetHideAnotherDatasetsOnlyReport() {
        LocalDateTime now = LocalDateTime.now();
        Dataset repeatedlyAnalyzed = saveDataset("dataset_anomalies_it_test2.csv");
        for (int i = 0; i < 5; i++) {
            saveReport(repeatedlyAnalyzed, 0.0, now.minusMinutes(i));
        }

        Dataset analyzedOnce = saveDataset("cardio_it_test.csv");
        saveReport(analyzedOnce, 100.0, now.minusDays(1));

        List<QualityReport> latestPerDataset =
                qualityReportRepository.findLatestPerDataset(PageRequest.of(0, LARGE_PAGE));

        assertThat(entriesFor(latestPerDataset, repeatedlyAnalyzed.getId())).hasSize(1);
        assertThat(entriesFor(latestPerDataset, analyzedOnce.getId())).hasSize(1);
    }

    @Test
    void ordersResultsByMostRecentFirst() {
        LocalDateTime now = LocalDateTime.now();
        Dataset older = saveDataset("older_it_test.csv");
        saveReport(older, 90.0, now.minusDays(10));

        Dataset newer = saveDataset("newer_it_test.csv");
        saveReport(newer, 95.0, now.plusMinutes(1));

        List<QualityReport> latestPerDataset =
                qualityReportRepository.findLatestPerDataset(PageRequest.of(0, LARGE_PAGE));

        int newerIndex = latestPerDataset.indexOf(entriesFor(latestPerDataset, newer.getId()).get(0));
        int olderIndex = latestPerDataset.indexOf(entriesFor(latestPerDataset, older.getId()).get(0));

        assertThat(newerIndex).isLessThan(olderIndex);
    }
}
