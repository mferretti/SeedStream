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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * Runs the real CLI entry point ({@link DataGeneratorCli} plus the production friendly exception
 * handler, exactly as {@code main} wires it) and captures exit code, picocli stderr/stdout and the
 * application log events.
 *
 * <p>Exit codes: 0 success, 1 runtime failure (message printed to stderr by the friendly handler),
 * 2 picocli usage error (message + usage printed to stderr).
 */
final class CliTestSupport {

  private CliTestSupport() {}

  /** Outcome of one CLI invocation. */
  record Result(int exit, String err, String out, List<ILoggingEvent> logs) {

    boolean logged(Level level, String fragment) {
      return logs.stream()
          .anyMatch(e -> e.getLevel() == level && e.getFormattedMessage().contains(fragment));
    }
  }

  static Result run(String... args) {
    CommandLine cmd = new CommandLine(new DataGeneratorCli());
    cmd.setExecutionExceptionHandler(DataGeneratorCli.friendlyExceptionHandler());
    StringWriter err = new StringWriter();
    StringWriter out = new StringWriter();
    cmd.setErr(new PrintWriter(err, true));
    cmd.setOut(new PrintWriter(out, true));

    Logger appLogger = (Logger) LoggerFactory.getLogger("com.datagenerator");
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    appLogger.addAppender(appender);
    try {
      int exit = cmd.execute(args);
      return new Result(exit, err.toString(), out.toString(), List.copyOf(appender.list));
    } finally {
      appLogger.detachAppender(appender);
      appender.stop();
      resetLogLevels();
    }
  }

  static void resetLogLevels() {
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
    ((Logger) LoggerFactory.getLogger("com.datagenerator")).setLevel(Level.INFO);
  }
}
