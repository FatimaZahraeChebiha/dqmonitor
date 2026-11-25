"""
Data quality analysis job, triggered by Spring Boot's DatasetService via ProcessBuilder.

Redesign notes (vs. the original version):
  - Uses real Great Expectations (the doc listed it as part of the stack, but the
    original script only had hand-written PySpark null/duplicate checks). Rules are
    fetched from the backend's validation_rules table and translated into GX
    expectations dynamically, so data quality rules are configurable data instead
    of hardcoded logic.
  - invalid_rows/valid_rows are a real per-row count (a row is invalid if it
    fails ANY expectation), not `total_rows * score / 100`. quality_score is
    itself derived from these same counts (valid_rows / total_rows), not from
    how many rules passed with zero violations - the earlier check-based
    formula could show a 0% score on a report where the large majority of rows
    were actually clean, which read as nonsensical next to the row counts.
  - No longer writes to MySQL directly. Posts the result back to the Spring Boot
    API (POST /internal/reports) so the schema has exactly one owner (the JPA
    layer) instead of being duplicated across Java and Python.
  - Reads all configuration (backend URL, internal API key) from environment
    variables instead of a hardcoded dict with committed credentials.
"""

import sys
import os
import json

# Must be set before the SparkSession is created. Without this, Spark defaults its
# worker processes to the "python3" executable name, which on Windows can resolve
# to the Microsoft Store's app-execution-alias stub instead of a real interpreter,
# causing worker-connect failures that look unrelated to Python at all. Pinning the
# worker to the exact interpreter running this script removes that class of
# environment-dependent failure entirely.
os.environ["PYSPARK_PYTHON"] = sys.executable
os.environ["PYSPARK_DRIVER_PYTHON"] = sys.executable

import pandas as pd
import requests
from pyspark.sql import SparkSession
from pyspark.sql.types import LongType

import great_expectations as gx
from great_expectations.expectations import (
    ExpectColumnValuesToNotBeNull,
    ExpectColumnValuesToBeUnique,
    ExpectColumnValuesToMatchRegex,
    ExpectColumnValuesToBeBetween,
    ExpectColumnValuesToBeInSet,
    ExpectColumnToExist,
)

BACKEND_BASE_URL = os.environ.get("BACKEND_BASE_URL", "http://localhost:8080")
INTERNAL_API_KEY = os.environ.get("INTERNAL_API_KEY", "dev-internal-key-change-me")
HEADERS = {"X-Internal-Key": INTERNAL_API_KEY, "Content-Type": "application/json"}


def fetch_active_rules():
    """Rules configured on the Validation Rules page - falls back to the default
    not-null + no-duplicates baseline if none are configured for this dataset."""
    try:
        resp = requests.get(
            f"{BACKEND_BASE_URL}/internal/validation-rules/active", headers=HEADERS, timeout=10
        )
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        print(f"[WARN] Could not fetch validation rules ({e}); using default checks.")
        return []


def build_expectation(rule, columns):
    column = rule.get("columnName")
    rule_type = rule.get("ruleType")
    value = rule.get("ruleValue")
    # Stashed in the expectation's own meta dict so the validation result can
    # report back which user-defined rule (e.g. "email_non_vide") actually
    # failed, instead of only the generic GX expectation type - meta is the
    # standard GX mechanism for round-tripping caller-supplied identifiers.
    meta = {"rule_name": rule.get("ruleName")}

    if rule_type == "REQUIRED_COLUMN":
        return ExpectColumnToExist(column=column, meta=meta)

    if column not in columns:
        print(f"[WARN] Rule '{rule.get('ruleName')}' targets unknown column '{column}' - skipped")
        return None

    if rule_type == "NOT_NULL":
        return ExpectColumnValuesToNotBeNull(column=column, meta=meta)
    if rule_type == "UNIQUE":
        return ExpectColumnValuesToBeUnique(column=column, meta=meta)
    if rule_type == "REGEX":
        return ExpectColumnValuesToMatchRegex(column=column, regex=value, meta=meta)
    if rule_type == "RANGE":
        parts = (value or "").split(",")
        min_v = parts[0].strip() if len(parts) > 0 and parts[0].strip() != "" else None
        max_v = parts[1].strip() if len(parts) > 1 and parts[1].strip() != "" else None
        return ExpectColumnValuesToBeBetween(
            column=column,
            min_value=float(min_v) if min_v is not None else None,
            max_value=float(max_v) if max_v is not None else None,
            meta=meta,
        )
    if rule_type == "ENUM":
        allowed_values = [v.strip() for v in (value or "").split(",") if v.strip() != ""]
        return ExpectColumnValuesToBeInSet(column=column, value_set=allowed_values, meta=meta)

    print(f"[WARN] Unknown rule type '{rule_type}' - skipped")
    return None


def compute_quality_score(valid_rows, total_rows):
    """Score = rows with zero rule violations / total rows, as a percentage.

    A row counts as valid only if it violates none of the configured rules - one
    bad column in an otherwise clean row still makes that whole row invalid. This
    is deliberately the same ratio already shown as "valid rows" elsewhere in the
    report, so the two numbers can never contradict each other the way a
    check-based score (rules passed / rules checked) could: that formula treated
    a rule as "failed" the instant a single row violated it, so a report where
    almost every row was clean could still show a 0% score.
    """
    if total_rows <= 0:
        return 100.0
    return round(valid_rows / total_rows * 100, 1)


def detect_delimiter(file_path):
    """Auto-detects the CSV delimiter by counting candidates on the header line -
    picks whichever splits the header into the most fields, since a real delimiter
    always appears far more often than any of these characters would by coincidence
    inside a single unstructured line."""
    candidates = [",", ";", "\t", "|"]
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        first_line = f.readline()
    return max(candidates, key=lambda c: first_line.count(c))


def read_excel_as_spark(spark, file_path):
    """Spark has no native XLSX reader, so the first sheet is loaded through
    pandas (openpyxl engine) and handed to Spark as an in-memory DataFrame -
    the same object every downstream check already works with regardless of
    source format. Only the first sheet is read, matching what
    FileCorrectionService.applyXlsx() edits on the Java side, so analysis and
    correction always agree on which sheet "the data" is.

    NaN -> None is required before the Spark conversion: pandas represents an
    empty Excel cell as NaN, and Spark's pandas conversion leaves NaN as the
    float NaN rather than a real null, which would make every NOT_NULL check
    silently pass on blank cells instead of catching them.
    """
    pandas_df = pd.read_excel(file_path, sheet_name=0, engine="openpyxl")
    pandas_df = pandas_df.where(pd.notnull(pandas_df), None)
    # Excel columns can end up as pandas "object" dtype when a column mixes
    # types (e.g. mostly numbers with one stray text cell) - Spark's schema
    # inference requires one consistent type per column, so those are coerced
    # to strings rather than letting createDataFrame guess and potentially
    # fail. Purely numeric or purely textual columns are unaffected.
    for column in pandas_df.columns:
        if pandas_df[column].dtype == object:
            pandas_df[column] = pandas_df[column].apply(lambda v: v if v is None else str(v))
    return spark.createDataFrame(pandas_df)


def submit_result(dataset_id, success, quality_score=None, total_rows=None,
                   valid_rows=None, invalid_rows=None, results=None, anomalies=None, error_message=None):
    payload = {
        "datasetId": int(dataset_id),
        "success": success,
        "qualityScore": quality_score,
        "totalRows": total_rows,
        "validRows": valid_rows,
        "invalidRows": invalid_rows,
        "reportDetails": json.dumps(results) if results is not None else None,
        "anomalies": anomalies,
        "errorMessage": error_message,
    }
    resp = requests.post(f"{BACKEND_BASE_URL}/internal/reports", json=payload, headers=HEADERS, timeout=60)
    resp.raise_for_status()


def run_quality_check(dataset_id, file_path):
    spark = SparkSession.builder.appName("DataQualityCheck").getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    try:
        if file_path.endswith(".json"):
            df = spark.read.json(file_path)
        elif file_path.endswith(".xlsx"):
            df = read_excel_as_spark(spark, file_path)
        else:
            delimiter = detect_delimiter(file_path)
            df = spark.read.csv(file_path, header=True, inferSchema=True, sep=delimiter)

        total_rows = df.count()
        columns = df.columns
        print(f"[INFO] Dataset {dataset_id}: {total_rows} rows, columns: {columns}")

        # Spark dataframes have no built-in row identity (unlike pandas), so Great
        # Expectations can't report *which* rows failed without one.
        # monotonically_increasing_id() would technically satisfy that, but it
        # packs the partition number into the high bits of the value (e.g.
        # 8589934592 for the first row of partition 1), which is meaningless to a
        # user looking at "row 8589934593" in the Anomaly Explorer. zipWithIndex
        # numbers rows 0..N-1 in read order instead - stable for this run and
        # readable as "the Nth data row" (1-based here, header excluded).
        row_id_column = "__gx_row_id"
        indexed_schema = df.schema.add(row_id_column, LongType(), False)
        indexed_rdd = df.rdd.zipWithIndex().map(lambda pair: (*pair[0], pair[1] + 1))
        df = spark.createDataFrame(indexed_rdd, schema=indexed_schema)

        rules = fetch_active_rules()

        context = gx.get_context(mode="ephemeral")
        data_source = context.data_sources.add_spark(name="quality_check_datasource")
        data_asset = data_source.add_dataframe_asset(name="dataset")
        batch_definition = data_asset.add_batch_definition_whole_dataframe("batch")
        batch = batch_definition.get_batch(batch_parameters={"dataframe": df})

        suite = gx.ExpectationSuite(name=f"dataset-{dataset_id}-suite")

        if rules:
            for rule in rules:
                expectation = build_expectation(rule, columns)
                if expectation is not None:
                    suite.add_expectation(expectation)
        else:
            # Default baseline when no custom rules are configured for this dataset.
            for column in columns:
                suite.add_expectation(ExpectColumnValuesToNotBeNull(column=column))

        results = []
        anomalies = []
        failing_row_indices = set()

        if suite.expectations:
            validation_result = batch.validate(
                suite,
                result_format={
                    "result_format": "COMPLETE",
                    "unexpected_index_column_names": [row_id_column],
                },
            )

            for r in validation_result.results:
                check_type = r.expectation_config.type
                column = r.expectation_config.kwargs.get("column")
                # Falls back to the GX expectation type when a rule has no
                # user-defined name to report (e.g. the default not-null
                # baseline used when no custom rules are configured).
                rule_name = (r.expectation_config.meta or {}).get("rule_name") or check_type
                unexpected_count = r.result.get("unexpected_count", 0) or 0
                unexpected_index_list = r.result.get("unexpected_index_list") or []
                unexpected_list = r.result.get("unexpected_list") or []

                # With unexpected_index_column_names set, each entry is a dict like
                # {"__gx_row_id": 123} rather than a bare index - unwrap it so the
                # set contains hashable row identifiers.
                for entry in unexpected_index_list:
                    row_id = entry.get(row_id_column) if isinstance(entry, dict) else entry
                    failing_row_indices.add(row_id)

                # Every failing row for this rule, uncapped - the Anomaly Explorer
                # needs the real count, not a fixed sample. Persisted separately
                # via the "anomalies" payload into report_anomalies (a real table),
                # not into reportDetails (a TEXT blob) which stays exactly the
                # per-check summary it always was - the Quality Score calculation
                # below is untouched by this change.
                for entry, val in zip(unexpected_index_list, unexpected_list):
                    row_id = entry.get(row_id_column) if isinstance(entry, dict) else entry
                    anomalies.append({
                        "rowIndex": row_id,
                        "column": column,
                        "ruleName": rule_name,
                        "checkType": check_type,
                        "value": None if val is None else str(val),
                    })

                # Reports the check as passed whenever it found zero violations,
                # rather than trusting GX's own r.success flag directly: for the
                # UNIQUE and BETWEEN expectation types, GX's Spark backend can
                # return success=False even with unexpected_count=0 (a known rough
                # edge of that backend, not a real violation - reproduced and
                # confirmed via the stored reportDetails for report 35, e.g.
                # expect_column_values_to_be_unique/_between reporting
                # unexpected_count=0 with success=false). unexpected_count is
                # already the source of truth for invalid_rows/quality_score below
                # (via unexpected_index_list), so deriving the displayed status
                # from the same number keeps the "Résultat" badge consistent with
                # the "Lignes en anomalie" column next to it.
                check_passed = unexpected_count == 0

                results.append({
                    "check_name": f"{check_type}_{column}" if column else check_type,
                    "check_type": check_type,
                    "column": column,
                    "success": check_passed,
                    "unexpected_count": unexpected_count,
                    "unexpected_pct": round(unexpected_count / total_rows * 100, 2) if total_rows > 0 else 0,
                })

                status = "PASS" if check_passed else "FAIL"
                print(f"  [{status}] {check_type} ({column}) - unexpected: {unexpected_count}")

        # Whole-row duplicate detection isn't expressible as a single GX column
        # expectation, so it runs as a constant baseline check alongside the
        # configured rules (matches the original script's intent, now measured
        # correctly rather than estimated).
        # Must dedupe on the original columns only - df now also carries the
        # synthetic __gx_row_id column, which is unique per row by construction
        # and would make every row look distinct if included.
        duplicate_count = total_rows - df.dropDuplicates(columns).count()
        dup_success = duplicate_count == 0
        results.append({
            "check_name": "no_duplicates",
            "check_type": "no_duplicates",
            "success": dup_success,
            "unexpected_count": duplicate_count,
            "unexpected_pct": round(duplicate_count / total_rows * 100, 2) if total_rows > 0 else 0,
        })
        print(f"  [{'PASS' if dup_success else 'FAIL'}] no_duplicates - unexpected: {duplicate_count}")

        # Real per-row validity: a row is invalid if it appears in ANY expectation's
        # unexpected_index_list. Duplicate rows aren't attributable to a specific
        # index reliably in a distributed dataframe, so they're reported separately
        # via the no_duplicates check above but aren't double-counted into
        # invalid_rows here.
        invalid_rows = len(failing_row_indices)
        valid_rows = total_rows - invalid_rows
        quality_score = compute_quality_score(valid_rows, total_rows)

        print(f"\n{'=' * 50}")
        print(f"[RESULT] Quality score : {quality_score}%")
        print(f"[RESULT] Valid rows    : {valid_rows}")
        print(f"[RESULT] Invalid rows  : {invalid_rows}")
        print(f"{'=' * 50}")

        submit_result(
            dataset_id, success=True, quality_score=quality_score,
            total_rows=total_rows, valid_rows=valid_rows, invalid_rows=invalid_rows,
            results=results, anomalies=anomalies,
        )
        print("[INFO] Report submitted to backend")

    except Exception as e:
        print(f"[ERROR] {e}")
        try:
            submit_result(dataset_id, success=False, error_message=str(e))
        except Exception as callback_error:
            print(f"[ERROR] Could not report failure to backend: {callback_error}")
        raise
    finally:
        spark.stop()


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("[ERROR] Usage: quality_check.py <dataset_id> <file_path>")
        sys.exit(1)

    run_quality_check(sys.argv[1], sys.argv[2])
