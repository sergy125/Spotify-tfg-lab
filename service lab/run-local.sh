#!/bin/bash
set -euo pipefail
LAB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$LAB_DIR"

if [[ ! -f .env ]]; then
  echo "Falta .env. Copia .env.example y configura tu token de Spotify."
  exit 1
fi
set -a
source .env
set +a

if [[ -x "$LAB_DIR/.local/java21/bin/java" ]]; then
  export JAVA_HOME="$LAB_DIR/.local/java21"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "Compilando Service Lab con Java 21 y SQLite..."
if [[ -x .local/maven/bin/mvn ]]; then
  .local/maven/bin/mvn -Dmaven.repo.local="$LAB_DIR/.local/m2" -q -DskipTests package
else
  ./mvnw -q -DskipTests package
fi
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar target/service-lab-0.0.1-SNAPSHOT.jar
