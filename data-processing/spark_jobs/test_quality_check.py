from quality_check import compute_quality_score


def test_empty_dataset_returns_full_score():
    assert compute_quality_score(valid_rows=0, total_rows=0) == 100.0


def test_all_rows_valid_returns_full_score():
    assert compute_quality_score(valid_rows=15, total_rows=15) == 100.0


def test_all_rows_invalid_returns_zero():
    assert compute_quality_score(valid_rows=0, total_rows=15) == 0.0


def test_matches_the_test_dataset_scenario():
    # test_dataset_anomalies.csv: 15 rows, 7 of them each violate at least one
    # rule (null email, bad email format, null age, out-of-range age, null
    # salaire, negative salaire) - so 8 rows are entirely clean.
    valid_rows = 8
    total_rows = 15
    assert compute_quality_score(valid_rows, total_rows) == 53.3


def test_score_matches_the_valid_rows_ratio_exactly():
    # The whole point of this formula: "Score" and "Lignes valides" can never
    # disagree, because they're now literally the same ratio.
    valid_rows, total_rows = 37, 200
    score = compute_quality_score(valid_rows, total_rows)
    assert score == round(valid_rows / total_rows * 100, 1)


def test_rounds_to_one_decimal_place():
    # 1/3 of rows valid -> 33.333...% should round to 33.3%, not truncate or
    # carry extra precision into the report.
    assert compute_quality_score(valid_rows=1, total_rows=3) == 33.3
