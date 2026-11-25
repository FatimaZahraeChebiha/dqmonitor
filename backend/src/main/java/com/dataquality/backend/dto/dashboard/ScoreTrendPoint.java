package com.dataquality.backend.dto.dashboard;

import java.time.LocalDateTime;

public record ScoreTrendPoint(LocalDateTime date, double score) {}
