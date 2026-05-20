# syntax=docker/dockerfile:1
# Build stage
FROM python:3.11-slim AS builder

WORKDIR /app

# Install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY rag-service/requirements.txt .
RUN pip install --no-cache-dir --user -r requirements.txt

# Production stage
FROM python:3.11-slim

WORKDIR /app

# Install runtime dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r app && useradd -r -g app app

# Copy Python packages from builder
COPY --from=builder /root/.local /app/.local
ENV PATH=/app/.local/bin:$PATH

# Copy application code
COPY --chown=app:app rag-service/ ./app/

WORKDIR /app

# Set Python path
ENV PYTHONPATH=/app

# Expose port
EXPOSE 8199

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8199/actuator/health || exit 1

# Switch to non-root user
USER app

# Run the application
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8199"]
