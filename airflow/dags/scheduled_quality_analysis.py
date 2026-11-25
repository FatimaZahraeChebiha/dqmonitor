"""
Sweeps up datasets that were uploaded but never analyzed - either because a user
forgot to click "Analyze" on the Datasets page, or because the upload was made
by an automated process with no one watching the UI. Runs every 15 minutes and
calls the same internal API the manual "Analyze" button uses
(backend/InternalController), so there is exactly one place that owns the
analyze-trigger logic - this DAG is just a scheduler on top of it, not a
second implementation of it.

BACKEND_INTERNAL_URL and BACKEND_INTERNAL_KEY are read from the environment
(set in docker-compose.yml from the same INTERNAL_API_KEY used by the Python
quality_check job), matching the project's env-var-driven config convention.
"""

import json
import os
import urllib.error
import urllib.request
from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.python import PythonOperator

BACKEND_INTERNAL_URL = os.environ.get("BACKEND_INTERNAL_URL", "http://backend:8080/internal")
BACKEND_INTERNAL_KEY = os.environ.get("BACKEND_INTERNAL_KEY", "")


def _request(method: str, path: str, body: dict | None = None) -> dict | list | None:
    url = f"{BACKEND_INTERNAL_URL}{path}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("X-Internal-Key", BACKEND_INTERNAL_KEY)
    request.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(request, timeout=15) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


def fetch_pending_datasets(**context) -> list[dict]:
    pending = _request("GET", "/datasets/pending") or []
    print(f"Found {len(pending)} dataset(s) awaiting analysis")
    context["ti"].xcom_push(key="pending_datasets", value=pending)
    return pending


def trigger_analysis(**context) -> None:
    pending = context["ti"].xcom_pull(task_ids="fetch_pending_datasets", key="pending_datasets") or []
    for dataset in pending:
        dataset_id = dataset["id"]
        try:
            _request("POST", f"/datasets/{dataset_id}/analyze")
            print(f"Triggered analysis for dataset {dataset_id} ({dataset.get('fileName')})")
        except urllib.error.URLError as e:
            print(f"Failed to trigger analysis for dataset {dataset_id}: {e}")


with DAG(
    dag_id="scheduled_quality_analysis",
    description="Finds datasets awaiting analysis and triggers the same analysis pipeline the Datasets page uses",
    start_date=datetime(2026, 1, 1),
    schedule=timedelta(minutes=15),
    catchup=False,
    default_args={"retries": 1, "retry_delay": timedelta(minutes=2)},
    tags=["dqmonitor"],
) as dag:
    fetch_task = PythonOperator(
        task_id="fetch_pending_datasets",
        python_callable=fetch_pending_datasets,
    )

    trigger_task = PythonOperator(
        task_id="trigger_analysis",
        python_callable=trigger_analysis,
    )

    fetch_task >> trigger_task
