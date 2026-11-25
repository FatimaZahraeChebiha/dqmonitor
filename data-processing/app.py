"""
FastAPI wrapper around quality_check.py's run_quality_check(), turning the
analysis job into a small, container-native microservice instead of a CLI
script the backend must have local filesystem/interpreter access to invoke via
ProcessBuilder. Spring Boot's DatasetService calls POST /analyze over the
docker-compose network (or localhost in dev); this returns immediately (202)
and the job runs in a background thread, exactly matching the original
fire-and-forget semantics - the job still reports its own completion via
POST /internal/reports on the backend.
"""

import logging
import threading

from fastapi import FastAPI
from pydantic import BaseModel

from spark_jobs.quality_check import run_quality_check

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("dqmonitor-analysis-service")

app = FastAPI(title="DQMonitor Analysis Service", version="1.0.0")


class AnalyzeRequest(BaseModel):
    datasetId: int
    filePath: str


@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/analyze", status_code=202)
async def analyze(request: AnalyzeRequest):
    # async def keeps this handler on the event loop thread instead of FastAPI's
    # automatic threadpool offload for sync handlers - spawning the Spark job
    # thread from a thread that is itself already a nested worker thread breaks
    # py4j's per-thread JVM connection tracking (Great Expectations' internal
    # Spark calls then fail with "SparkPlan.session() is null"). Spawning from
    # the true main/event-loop thread avoids that extra nesting level.
    def run():
        try:
            run_quality_check(str(request.datasetId), request.filePath)
        except Exception:
            logger.exception("Unhandled error running quality_check for dataset %s", request.datasetId)

    thread = threading.Thread(target=run, name=f"quality-check-{request.datasetId}", daemon=True)
    thread.start()
    return {"accepted": True, "datasetId": request.datasetId}
