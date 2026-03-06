/*
 * Copyright 2026 Marco Ferretti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Command-line interface for the data generator.
 *
 * <p>This package provides the CLI entry point and commands for running data generation jobs. Built
 * with Picocli for argument parsing and validation.
 *
 * <p><b>Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.cli.DataGeneratorCli} - Main CLI entry point
 *   <li>{@link com.datagenerator.cli.ExecuteCommand} - Execute command for running generation jobs
 * </ul>
 *
 * <p><b>Commands:</b>
 *
 * <ul>
 *   <li><b>execute:</b> Run a data generation job from configuration file
 * </ul>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>
 * # Basic execution with defaults (json format, 100 records)
 * datagenerator execute --job config/jobs/file_user.yaml
 *
 * # Custom format and count
 * datagenerator execute --job config/jobs/file_user.yaml --format csv --count 10000
 *
 * # Reproducible generation with seed
 * datagenerator execute --job config/jobs/kafka_events.yaml --seed 12345
 *
 * # Debug mode with detailed logging
 * datagenerator execute --job config/jobs/file_user.yaml --debug
 *
 * # Full example with all options
 * datagenerator execute \
 *   --job config/jobs/kafka_user.yaml \
 *   --format json \
 *   --count 1000000 \
 *   --seed 42 \
 *   --threads 8 \
 *   --debug
 * </pre>
 *
 * <p><b>Options:</b>
 *
 * <ul>
 *   <li><b>--job:</b> Path to job configuration YAML file (required)
 *   <li><b>--format:</b> Output format (json, csv) - defaults to json
 *   <li><b>--count:</b> Number of records to generate - defaults to 100
 *   <li><b>--seed:</b> Master seed for reproducible generation - overrides config
 *   <li><b>--threads:</b> Number of worker threads - defaults to CPU count
 *   <li><b>--debug:</b> Enable debug logging for detailed execution info
 * </ul>
 *
 * <p><b>Exit Codes:</b>
 *
 * <ul>
 *   <li><b>0:</b> Success
 *   <li><b>1:</b> Configuration error (invalid YAML, missing files)
 *   <li><b>2:</b> Generation error (type errors, circular references)
 *   <li><b>3:</b> Destination error (connection failed, write error)
 * </ul>
 *
 * <p><b>Logging:</b> Uses SLF4J with Logback. Log levels:
 *
 * <ul>
 *   <li><b>INFO:</b> Progress updates, completion summary (default)
 *   <li><b>DEBUG:</b> Detailed execution info (use --debug flag)
 *   <li><b>ERROR:</b> Fatal errors with stack traces
 * </ul>
 *
 * @see picocli.CommandLine
 * @see com.datagenerator.core.engine.GenerationEngine
 */
package com.datagenerator.cli;
