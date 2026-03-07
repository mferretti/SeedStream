#!/bin/bash
# Copyright 2026 Marco Ferretti
# 
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
KAFKA_TOPIC="${KAFKA_TOPIC:-benchmark-topic}"
CONTAINER_NAME="kafka-benchmark"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Kafka Producer Benchmark Runner${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to check if Kafka is running
check_kafka() {
    echo -e "${YELLOW}Checking if Kafka is accessible at ${KAFKA_BOOTSTRAP}...${NC}"
    
    # Try to connect using nc (netcat)
    if command -v nc &> /dev/null; then
        KAFKA_HOST=$(echo $KAFKA_BOOTSTRAP | cut -d: -f1)
        KAFKA_PORT=$(echo $KAFKA_BOOTSTRAP | cut -d: -f2)
        
        if nc -z -w5 "$KAFKA_HOST" "$KAFKA_PORT" 2>/dev/null; then
            echo -e "${GREEN}✓ Kafka is accessible at ${KAFKA_BOOTSTRAP}${NC}"
            return 0
        else
            echo -e "${RED}✗ Kafka is NOT accessible at ${KAFKA_BOOTSTRAP}${NC}"
            return 1
        fi
    else
        echo -e "${YELLOW}⚠ netcat (nc) not found, skipping connectivity check${NC}"
        echo -e "${YELLOW}  Assuming Kafka is running...${NC}"
        return 0
    fi
}

# Function to start Kafka in Docker
start_kafka_docker() {
    echo ""
    echo -e "${YELLOW}Starting Kafka in Docker...${NC}"
    
    # Check if Docker is available
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}✗ Docker is not installed or not in PATH${NC}"
        echo -e "${RED}  Please install Docker or start Kafka manually${NC}"
        exit 1
    fi
    
    # Check if container already exists
    if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        echo -e "${YELLOW}Container '${CONTAINER_NAME}' already exists${NC}"
        
        # Check if it's running
        if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
            echo -e "${GREEN}✓ Kafka container is already running${NC}"
        else
            echo -e "${YELLOW}Starting existing container...${NC}"
            docker start "$CONTAINER_NAME"
            echo -e "${YELLOW}Waiting for Kafka to be ready (15 seconds)...${NC}"
            sleep 15
        fi
    else
        echo -e "${YELLOW}Creating and starting new Kafka container...${NC}"
        docker run -d --name "$CONTAINER_NAME" \
          -p 9092:9092 \
          -e KAFKA_ENABLE_KRAFT=yes \
          -e KAFKA_CFG_PROCESS_ROLES=broker,controller \
          -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
          -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
          -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
          -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
          -e KAFKA_BROKER_ID=1 \
          -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
          -e ALLOW_PLAINTEXT_LISTENER=yes \
          bitnami/kafka:latest
        
        echo -e "${YELLOW}Waiting for Kafka to be ready (20 seconds)...${NC}"
        sleep 20
    fi
    
    echo -e "${GREEN}✓ Kafka is running in Docker (container: ${CONTAINER_NAME})${NC}"
}

# Function to create benchmark topic
create_topic() {
    echo ""
    echo -e "${YELLOW}Creating benchmark topic: ${KAFKA_TOPIC}${NC}"
    
    # Check if topic already exists
    if docker exec "$CONTAINER_NAME" kafka-topics.sh --list \
        --bootstrap-server localhost:9092 2>/dev/null | grep -q "^${KAFKA_TOPIC}$"; then
        echo -e "${GREEN}✓ Topic '${KAFKA_TOPIC}' already exists${NC}"
    else
        docker exec "$CONTAINER_NAME" kafka-topics.sh --create \
          --topic "$KAFKA_TOPIC" \
          --bootstrap-server localhost:9092 \
          --partitions 3 \
          --replication-factor 1
        
        echo -e "${GREEN}✓ Topic '${KAFKA_TOPIC}' created (3 partitions)${NC}"
    fi
}

# Function to run benchmarks
run_benchmarks() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  Running Kafka Benchmarks${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo -e "${YELLOW}Bootstrap: ${KAFKA_BOOTSTRAP}${NC}"
    echo -e "${YELLOW}Topic: ${KAFKA_TOPIC}${NC}"
    echo -e "${YELLOW}Test configurations: 24 (sync x compression x batchSize)${NC}"
    echo -e "${YELLOW}Estimated time: 15-20 minutes${NC}"
    echo ""
    
    cd "$PROJECT_ROOT"
    
    ./gradlew :benchmarks:jmh -Pjmh.includes=".*KafkaBenchmark.*" \
      -Dkafka.bootstrap="$KAFKA_BOOTSTRAP" \
      -Dkafka.topic="$KAFKA_TOPIC" \
      "$@"
    
    echo ""
    echo -e "${GREEN}✓ Benchmarks complete!${NC}"
    echo -e "${YELLOW}Results: benchmarks/build/reports/jmh/results.json${NC}"
}

# Function to show cleanup instructions
show_cleanup() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  Cleanup Instructions${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
    echo "To stop and remove the Kafka container:"
    echo -e "${YELLOW}  docker stop ${CONTAINER_NAME} && docker rm ${CONTAINER_NAME}${NC}"
    echo ""
    echo "To view results:"
    echo -e "${YELLOW}  cat benchmarks/build/reports/jmh/results.json${NC}"
    echo ""
    echo "To format results:"
    echo -e "${YELLOW}  python3 benchmarks/format_results.py > KAFKA-BENCHMARK-RESULTS.md${NC}"
    echo ""
}

# Main execution
main() {
    # Check if Kafka is already running
    if check_kafka; then
        echo -e "${GREEN}✓ Using existing Kafka instance${NC}"
        SKIP_DOCKER=true
    else
        echo -e "${YELLOW}Kafka not detected at ${KAFKA_BOOTSTRAP}${NC}"
        echo -e "${YELLOW}Would you like to start Kafka in Docker? (y/n)${NC}"
        read -r -p "> " response
        
        if [[ "$response" =~ ^[Yy]$ ]]; then
            start_kafka_docker
            create_topic
            SKIP_DOCKER=false
        else
            echo -e "${RED}✗ Cannot run benchmarks without Kafka${NC}"
            echo ""
            echo "Please start Kafka manually or run this script again to use Docker."
            exit 1
        fi
    fi
    
    # Run benchmarks
    run_benchmarks "$@"
    
    # Show cleanup instructions if we started Docker
    if [ "$SKIP_DOCKER" != "true" ]; then
        show_cleanup
    fi
}

# Help message
if [[ "$1" == "-h" || "$1" == "--help" ]]; then
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Run Kafka producer benchmarks with automatic setup."
    echo ""
    echo "Options:"
    echo "  -h, --help              Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  KAFKA_BOOTSTRAP         Kafka bootstrap servers (default: localhost:9092)"
    echo "  KAFKA_TOPIC            Benchmark topic name (default: benchmark-topic)"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Use defaults (localhost:9092)"
    echo "  KAFKA_BOOTSTRAP=broker1:9092 $0      # Custom broker"
    echo "  KAFKA_TOPIC=my-bench $0               # Custom topic"
    echo ""
    echo "This script will:"
    echo "  1. Check if Kafka is running"
    echo "  2. Optionally start Kafka in Docker if not found"
    echo "  3. Create benchmark topic (3 partitions)"
    echo "  4. Run all Kafka benchmarks (~15-20 minutes)"
    echo "  5. Display results location"
    echo ""
    exit 0
fi

# Run main function
main "$@"
