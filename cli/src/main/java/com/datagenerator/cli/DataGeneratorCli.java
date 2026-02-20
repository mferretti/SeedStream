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
    subcommands = {ExecuteCommand.class})
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
