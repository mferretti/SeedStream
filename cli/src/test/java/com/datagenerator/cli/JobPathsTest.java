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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.datagenerator.cli.JobPaths.StructuresResolution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class JobPathsTest {
  private static final String X_YAML = "x.yaml";
  private static final String STRUCTURES = "structures";

  @TempDir Path base;

  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final Logger appLogger = (Logger) LoggerFactory.getLogger("com.datagenerator");

  @BeforeEach
  void captureLogs() {
    appender.start();
    appLogger.addAppender(appender);
  }

  @AfterEach
  void releaseLogs() {
    appLogger.detachAppender(appender);
    appender.stop();
  }

  private Path touch(Path file) throws IOException {
    Files.createDirectories(Objects.requireNonNull(file.getParent()));
    return Files.writeString(file, "name: x\ndata: {}\n");
  }

  private StructuresResolution resolve(String job, String explicit, Path currentDir) {
    return JobPaths.resolveStructuresPath(Path.of(job), explicit, X_YAML, currentDir);
  }

  private long warnCount() {
    return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
  }

  @Test
  void shouldUseSiblingStructuresWhenJobPathIsRelativeAndCurrentDirHoldsJobsDir()
      throws IOException {
    touch(base.resolve(STRUCTURES).resolve(X_YAML));

    StructuresResolution r = resolve("jobs/x.yaml", null, base);

    assertThat(r.structuresPath()).isEqualTo(base.resolve(STRUCTURES));
    assertThat(r.fellBack()).isFalse();
    assertThat(warnCount()).isZero();
  }

  @Test
  void shouldPreferSiblingStructuresOverConfigStructuresWhenBothHoldTheFile() throws IOException {
    touch(base.resolve(STRUCTURES).resolve(X_YAML));
    touch(base.resolve("config/structures").resolve(X_YAML));

    StructuresResolution r = resolve("jobs/x.yaml", null, base);

    assertThat(r.structuresPath()).isEqualTo(base.resolve(STRUCTURES));
    assertThat(r.fellBack()).isFalse();
    assertSingleWarnNamesBoth(
        base.resolve(STRUCTURES).resolve(X_YAML),
        base.resolve("config/structures").resolve(X_YAML));
  }

  @Test
  void shouldWarnAndUseSiblingWhenCurrentDirIsNamedJobsAndBothLayoutsHoldTheFile()
      throws IOException {
    Path cwd = base.resolve("jobs");
    touch(base.resolve(STRUCTURES).resolve(X_YAML));
    touch(cwd.resolve("config/structures").resolve(X_YAML));

    StructuresResolution r = resolve(X_YAML, null, cwd);

    assertThat(r.structuresPath()).isEqualTo(base.resolve(STRUCTURES));
    assertThat(r.fellBack()).isFalse();
    assertSingleWarnNamesBoth(
        base.resolve(STRUCTURES).resolve(X_YAML), cwd.resolve("config/structures").resolve(X_YAML));
  }

  private void assertSingleWarnNamesBoth(Path siblingFile, Path configFile) {
    assertThat(appender.list)
        .filteredOn(e -> e.getLevel() == Level.WARN)
        .singleElement()
        .satisfies(
            e ->
                assertThat(e.getFormattedMessage())
                    .contains(siblingFile.toString())
                    .contains(configFile.toString()));
  }

  @Test
  void shouldGiveSameResultAsBeforeWhenJobPathIsAbsolute() throws IOException {
    Path uc = base.resolve("uc");
    touch(uc.resolve(STRUCTURES).resolve(X_YAML));
    Path otherCwd = base.resolve("elsewhere");
    Files.createDirectories(otherCwd);

    StructuresResolution r =
        JobPaths.resolveStructuresPath(uc.resolve("jobs/x.yaml"), null, X_YAML, otherCwd);

    assertThat(r.structuresPath()).isEqualTo(uc.resolve(STRUCTURES));
    assertThat(r.fellBack()).isFalse();
  }

  @Test
  void shouldNormaliseDotSlashJobPath() throws IOException {
    touch(base.resolve(STRUCTURES).resolve(X_YAML));

    StructuresResolution r = resolve("./jobs/x.yaml", null, base);

    assertThat(r.structuresPath()).isEqualTo(base.resolve(STRUCTURES));
  }

  @Test
  void shouldNormaliseParentTraversalJobPath() throws IOException {
    Path cwd = base.resolve("work");
    Files.createDirectories(cwd);
    touch(base.resolve("uc").resolve(STRUCTURES).resolve(X_YAML));

    StructuresResolution r = resolve("../uc/jobs/x.yaml", null, cwd);

    assertThat(r.structuresPath()).isEqualTo(base.resolve("uc").resolve(STRUCTURES));
    assertThat(r.fellBack()).isFalse();
  }

  @Test
  void shouldUseConfigStructuresWhenJobDirIsNotNamedJobs() throws IOException {
    touch(base.resolve("config/structures").resolve(X_YAML));
    // a sibling structures/ next to a non-"jobs" dir must be ignored
    touch(base.resolve(STRUCTURES).resolve(X_YAML));

    StructuresResolution r = resolve("mydir/x.yaml", null, base);

    assertThat(r.structuresPath()).isEqualTo(base.resolve("config/structures"));
    assertThat(r.fellBack()).isFalse();
    assertThat(warnCount()).isZero();
  }

  @Test
  void shouldReturnConfigStructuresWithoutThrowingWhenJobDirIsNotNamedJobsAndFileIsMissing() {
    StructuresResolution r = resolve("mydir/x.yaml", null, base);

    assertThat(r.structuresPath()).isEqualTo(base.resolve("config/structures"));
  }

  @Test
  void shouldFallBackToConfigStructuresAndWarnWhenSiblingLacksTheFile() throws IOException {
    Files.createDirectories(base.resolve(STRUCTURES));
    touch(base.resolve("config/structures").resolve(X_YAML));

    StructuresResolution r = resolve("jobs/x.yaml", null, base);

    assertThat(r.structuresPath()).isEqualTo(base.resolve("config/structures"));
    assertThat(r.fellBack()).isTrue();
    assertThat(r.tried())
        .containsExactly(base.resolve(STRUCTURES), base.resolve("config/structures"));
    assertThat(appender.list)
        .filteredOn(e -> e.getLevel() == Level.WARN)
        .singleElement()
        .satisfies(
            e ->
                assertThat(e.getFormattedMessage())
                    .contains(X_YAML)
                    .contains(base.resolve("config/structures").toString())
                    .contains(base.resolve(STRUCTURES).toString()));
  }

  @Test
  void shouldFallBackToConfigStructuresWhenSiblingDirDoesNotExistAtAll() throws IOException {
    touch(base.resolve("config/structures").resolve(X_YAML));

    StructuresResolution r = resolve("jobs/x.yaml", null, base);

    assertThat(r.structuresPath()).isEqualTo(base.resolve("config/structures"));
    assertThat(r.fellBack()).isTrue();
    assertThat(warnCount()).isEqualTo(1);
  }

  @Test
  void shouldFailListingEveryLocationTriedWhenNeitherSiblingNorConfigHoldsTheFile() {
    assertThatThrownBy(() -> resolve("jobs/x.yaml", null, base))
        .isNotInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining(X_YAML)
        .hasMessageContaining(base.resolve(STRUCTURES).toString())
        .hasMessageContaining(base.resolve("config/structures").toString());
  }

  @Test
  void shouldResolveExplicitStructuresPathAgainstCurrentDirWithoutExistenceChecks()
      throws IOException {
    // inference candidates exist but must be ignored when structures_path is explicit
    touch(base.resolve(STRUCTURES).resolve(X_YAML));
    touch(base.resolve("config/structures").resolve(X_YAML));

    StructuresResolution r = resolve("jobs/x.yaml", "custom/structs", base);

    assertThat(r.structuresPath()).isEqualTo(base.resolve("custom/structs"));
    assertThat(r.fellBack()).isFalse();
    assertThat(warnCount()).isZero();
  }

  @Test
  void shouldKeepAbsoluteExplicitStructuresPathAsIs() {
    Path abs = base.resolve("abs-structs");

    StructuresResolution r = resolve("jobs/x.yaml", abs.toString(), base.resolve("other"));

    assertThat(r.structuresPath()).isEqualTo(abs);
  }

  @Test
  void shouldResolveToLinkSiblingNotTargetSiblingWhenJobsDirIsSymlinked() throws IOException {
    Path shared = base.resolve("shared");
    touch(shared.resolve("jobs").resolve(X_YAML));
    touch(shared.resolve(STRUCTURES).resolve(X_YAML));
    Path uc = base.resolve("uc");
    touch(uc.resolve(STRUCTURES).resolve(X_YAML));
    try {
      Files.createSymbolicLink(uc.resolve("jobs"), shared.resolve("jobs"));
    } catch (UnsupportedOperationException | IOException e) {
      Assumptions.abort("filesystem refuses symbolic links: " + e);
    }

    StructuresResolution r = resolve("uc/jobs/x.yaml", null, base);

    assertThat(r.structuresPath()).isEqualTo(uc.resolve(STRUCTURES));
    assertThat(r.fellBack()).isFalse();
  }
}
