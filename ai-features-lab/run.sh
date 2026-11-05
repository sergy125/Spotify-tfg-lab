#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
javac --release 21 -cp 'lib/*' -d out Main.java models/*.java audiofeatures/*.java
exec java -cp 'out:lib/*' Main "$@"
