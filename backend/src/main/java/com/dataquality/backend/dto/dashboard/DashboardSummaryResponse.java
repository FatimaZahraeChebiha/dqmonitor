package com.dataquality.backend.dto.dashboard;

import com.dataquality.backend.dto.report.QualityReportResponse;

import java.util.List;

public record DashboardSummaryResponse(
        double avgQualityScore,
        long totalFiles,
        long totalAnomalies,
        long totalValidations,
        List<ScoreTrendPoint> scoreTrend,
        List<AnomalyDistributionEntry> anomalyDistribution,
        List<ColumnAnomalyEntry> topProblematicColumns,
        List<QualityReportResponse> recentActivity,
        List<QualityDimensionEntry> qualityDimensions,
        DatasetStatusDistribution datasetStatusDistribution,
        List<AnalysisVolumePoint> analysisVolumeTrend,
        List<DatasetAttentionEntry> datasetsNeedingAttention
) {}
