#!/bin/bash

killgradle.sh

rm -rf ./examples/bookshelf/build/processedResources/ ./examples/bookshelf/build/distributions ./examples/bookshelf/build/libs

export SKORM_WEBAPP_URL=http://localhost:8080
export SKORM_JDBC_URL=jdbc:hsqldb:mem:example
export SKORM_JDBC_USER=sa
export SKORM_JDBC_PASS=

# GRADLE_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006" ./gradlew --stacktrace \
#           :skorm-gradle-plugin:clean \
#           :skorm-gradle-plugin:build \
#           :skorm-gradle-plugin:publishToMavenLocal \
#           :examples:bookshelf:clean \
#           :examples:bookshelf:build \
#           :examples:bookshelf:run

# -i / -d, --stacktrace

GRADLE_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006" ./gradlew --stacktrace \
          :examples:bookshelf:build \
          :examples:bookshelf:run

