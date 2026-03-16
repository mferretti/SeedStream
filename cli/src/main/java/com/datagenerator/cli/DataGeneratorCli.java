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

package com.datagenerator.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main entry point for the Data Generator CLI.
 *
 * <p>Provides command-line interface for executing data generation jobs.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * ./datagenerator execute --job config/jobs/file_address.yaml --count 1000
 * </pre>
 */
@Command(
    name = "datagenerator",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "High-performance test data generator",
    subcommands = {ExecuteCommand.class, ValidateCommand.class})
public class DataGeneratorCli implements Runnable {

  public static void main(String[] args) {
    int exitCode = new CommandLine(new DataGeneratorCli()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public void run() {
    // Show help when no subcommand is specified
    CommandLine.usage(this, System.out);
  }
}
