FROM python:3.13-slim

# PySpark needs a JVM even when driven entirely from Python. This image's Debian
# base only ships openjdk-21 (no 17) - PySpark 4.x supports both, so 21 is used.
RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
        procps \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .
COPY spark_jobs ./spark_jobs

EXPOSE 8001

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8001/health', timeout=3)" || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001"]
