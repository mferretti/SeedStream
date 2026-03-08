#!/bin/bash
# Run the local equivalent of the GitHub build pipeline before pushing.
#
# Default behavior mirrors .github/workflows/build.yml:
# - spotlessCheck
# - spotbugsMain
# - build
# - test jacocoTestReport
# - dependencyCheckAll (non-blocking, like continue-on-error in CI)
#
# Usage:
#   ./utils/run-local-ci.sh
#   ./utils/run-local-ci.sh --skip-dependency-check
#   ./utils/run-local-ci.sh --strict-dependency-check

set -euo pipefail

SKIP_DEPENDENCY_CHECK=false
STRICT_DEPENDENCY_CHECK=false

for arg in "$@"; do
  case "$arg" in
    --skip-dependency-check)
      SKIP_DEPENDENCY_CHECK=true
      ;;
    --strict-dependency-check)
      STRICT_DEPENDENCY_CHECK=true
      ;;
    -h|--help)
      sed -n '1,22p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg"
      echo "Use --help for usage."
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

if [ ! -x "./gradlew" ]; then
  chmod +x ./gradlew
fi

run_step() {
  local name="$1"
  shift
  echo
  echo "==> $name"
  "$@"
}

run_step "Check code formatting" ./gradlew spotlessCheck
run_step "Run SpotBugs" ./gradlew spotbugsMain
run_step "Build with Gradle" ./gradlew build
run_step "Run tests with coverage" ./gradlew test jacocoTestReport

if [ "$SKIP_DEPENDENCY_CHECK" = true ]; then
  echo
  echo "==> Skipping OWASP Dependency-Check (--skip-dependency-check)"
else
  echo
  echo "==> Run OWASP Dependency-Check"
  if [ "$STRICT_DEPENDENCY_CHECK" = true ]; then
    ./gradlew dependencyCheckAll
  else
    if ./gradlew dependencyCheckAll; then
      echo "Dependency-Check completed successfully."
    else
      echo "Dependency-Check failed, but continuing (matches CI continue-on-error)."
    fi
  fi
fi

echo
echo "Local CI pipeline checks completed."
