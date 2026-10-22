#!/usr/bin/env bash
set -euo pipefail
command -v java >/dev/null || { echo 'Install JDK 21 first.'; exit 1; }
command -v mvn >/dev/null || { echo 'Install Maven 3.9+ first (macOS: brew install maven).'; exit 1; }
mvn clean javafx:run
