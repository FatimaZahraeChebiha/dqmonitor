package com.dataquality.backend.service;

import com.dataquality.backend.repository.DatasetRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DatasetServiceTest {

    @Test
    void buildProcessBuilderUsesConfiguredPythonAndScriptPath() {
        DatasetService service = new DatasetService(
                mock(DatasetRepository.class),
                "C:/tmp/uploads/",
                "python",
                "C:/repo/data-processing/spark_jobs/quality_check.py"
        );

        ProcessBuilder builder = service.buildProcessBuilder(7L, "C:/tmp/uploads/file.csv");

        assertThat(builder.command()).containsExactly(
                "python",
                "C:/repo/data-processing/spark_jobs/quality_check.py",
                "7",
                "C:/tmp/uploads/file.csv"
        );
        assertThat(builder.redirectErrorStream()).isTrue();
    }
}
