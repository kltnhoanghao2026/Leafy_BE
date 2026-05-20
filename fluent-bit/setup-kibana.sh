#!/bin/bash
# Kibana Setup Script for Centralized Logging
# Usage: ./setup-kibana.sh [elasticsearch_url]
#
# Run this script after docker-compose is up and Elasticsearch + Kibana are healthy.
# Access Kibana at http://localhost:5601

ES_HOST="${1:-http://localhost:9200}"
INDEX_PATTERN="logs-*"

echo "======================================"
echo "Kibana Centralized Logging Setup"
echo "======================================"
echo "Elasticsearch Host: $ES_HOST"
echo "Index Pattern: $INDEX_PATTERN"
echo ""

# Wait for Elasticsearch to be ready
echo "[1/4] Waiting for Elasticsearch to be ready..."
until curl -sf "$ES_HOST" > /dev/null 2>&1; do
    echo "  Waiting for Elasticsearch..."
    sleep 5
done
echo "  Elasticsearch is ready!"

# Create index template for logs
echo ""
echo "[2/4] Creating index template for logs..."
curl -X PUT "$ES_HOST/_index_template/logs-template" \
  -H 'Content-Type: application/json' \
  -d '{
  "index_patterns": ["logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0,
      "index.lifecycle.name": "logs-policy",
      "index.lifecycle.rollover_alias": "logs"
    },
    "mappings": {
      "properties": {
        "@timestamp": { "type": "date" },
        "level": { "type": "keyword" },
        "pid": { "type": "integer" },
        "thread": { "type": "keyword" },
        "class": { "type": "keyword" },
        "message": { "type": "text" },
        "log": { "type": "text" },
        "log_processed": { "type": "text" },
        "container_name": { "type": "keyword" },
        "container_id": { "type": "keyword" },
        "kubernetes": {
          "type": "object",
          "properties": {
            "pod_name": { "type": "keyword" },
            "namespace_name": { "type": "keyword" },
            "container_name": { "type": "keyword" }
          }
        },
        "_hash": { "type": "keyword" }
      }
    }
  },
  "priority": 100
}'
echo ""
echo "  Index template created!"

# Create ILM policy for log retention
echo ""
echo "[3/4] Creating index lifecycle management policy..."
curl -X PUT "$ES_HOST/_ilm/policy/logs-policy" \
  -H 'Content-Type: application/json' \
  -d '{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0ms",
        "actions": {
          "rollover": {
            "max_primary_shard_size": "50gb"
          },
          "set_priority": {
            "priority": 100
          }
        }
      },
      "warm": {
        "min_age": "7d",
        "actions": {
          "set_priority": {
            "priority": 50
          }
        }
      },
      "delete": {
        "min_age": "30d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}'
echo ""
echo "  ILM policy created!"

# Create Kibana index pattern via API
echo ""
echo "[4/4] Creating Kibana index pattern..."
curl -X POST "http://localhost:5601/api/saved_objects/index-pattern/logs-*" \
  -H 'Content-Type: application/json' \
  -H 'kbn-xsrf: true' \
  -d "{
    \"attributes\": {
      \"title\": \"$INDEX_PATTERN\",
      \"timeFieldName\": \"@timestamp\"
    }
  }" 2>/dev/null || echo "  Note: Kibana index pattern creation via API requires Kibana to be fully initialized."
echo ""

echo "======================================"
echo "Setup Complete!"
echo "======================================"
echo ""
echo "Next steps:"
echo "1. Open Kibana: http://localhost:5601"
echo "2. Go to Stack Management > Index Patterns"
echo "3. Create index pattern: $INDEX_PATTERN"
echo "4. Set time field to: @timestamp"
echo "5. Explore your logs in Discover"
echo ""
echo "Useful queries:"
echo "  - All logs:                    *"
echo "  - Error logs only:             level:ERROR"
echo "  - Logs by service:            kubernetes.container_name:plant-management-service"
echo "  - Recent errors (last 1h):     level:ERROR AND @timestamp:[now-1h TO now]"
echo ""
echo "To view Fluent Bit logs:"
echo "  docker logs leafy-fluent-bit"
echo ""
echo "To test Elasticsearch manually:"
echo "  curl '$ES_HOST/_cat/indices/logs-*?v'"
