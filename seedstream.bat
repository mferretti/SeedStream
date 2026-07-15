@rem
@rem SeedStream launcher for Windows — mirror of the POSIX `seedstream` wrapper.
@rem
@rem   seedstream execute --job config\jobs\quickstart.yaml --count 1000
@rem   seedstream --help
@rem
@rem Uses an existing fat JAR if present, otherwise builds it once via Gradle,
@rem then `java -jar`. Force a rebuild with SEEDSTREAM_REBUILD=1.
@rem
@rem   JAVA_HOME          if set, %JAVA_HOME%\bin\java is used; else `java` on PATH
@rem   JAVA_OPTS          extra JVM flags
@rem   SEEDSTREAM_JAR     explicit path to a fat jar (skips discovery/build)
@rem   SEEDSTREAM_REBUILD =1 forces `:cli:fatJar` before running
@rem
@if "%DEBUG%"=="" @echo off
setlocal

set "SEEDSTREAM_HOME=%~dp0"

@rem --- resolve java ----------------------------------------------------------
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)

@rem --- locate the fat jar ----------------------------------------------------
set "JAR="
if defined SEEDSTREAM_JAR (
  set "JAR=%SEEDSTREAM_JAR%"
) else if not "%SEEDSTREAM_REBUILD%"=="1" (
  if exist "%SEEDSTREAM_HOME%seedstream-*.jar" (
    for /f "delims=" %%f in ('dir /b /o-d "%SEEDSTREAM_HOME%seedstream-*.jar"') do (
      set "JAR=%SEEDSTREAM_HOME%%%f" & goto :haveJar
    )
  )
  if exist "%SEEDSTREAM_HOME%cli\build\libs\seedstream-*.jar" (
    for /f "delims=" %%f in ('dir /b /o-d "%SEEDSTREAM_HOME%cli\build\libs\seedstream-*.jar"') do (
      set "JAR=%SEEDSTREAM_HOME%cli\build\libs\%%f" & goto :haveJar
    )
  )
)
:haveJar

@rem --- build on demand -------------------------------------------------------
if not defined JAR goto :build
if not exist "%JAR%" goto :build
goto :run

:build
echo seedstream: building the runtime jar ^(first run or SEEDSTREAM_REBUILD=1^)...  1>&2
call "%SEEDSTREAM_HOME%gradlew.bat" -p "%SEEDSTREAM_HOME%" :cli:fatJar --console=plain -q
if errorlevel 1 exit /b 1
for /f "delims=" %%f in ('dir /b /o-d "%SEEDSTREAM_HOME%cli\build\libs\seedstream-*.jar"') do (
  set "JAR=%SEEDSTREAM_HOME%cli\build\libs\%%f" & goto :run
)
echo seedstream: build did not produce cli\build\libs\seedstream-*.jar. 1>&2
exit /b 1

:run
"%JAVA_EXE%" %JAVA_OPTS% -jar "%JAR%" %*
exit /b %ERRORLEVEL%
