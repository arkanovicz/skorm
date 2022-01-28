#!/bin/bash

export SKORM_WEBAPP_URL=http://localhost:8080
export SKORM_JDBC_URL=jdbc:hsqldb:mem:example
export SKORM_JDBC_USER=
export SKORM_JDBC_PASS=
GRADLE_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006" ./gradlew -i --stacktrace :examples:bookshelf:run
#GRADLE_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006" ./gradlew --debug --stacktrace :examples:bookshelf:run
#GRADLE_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006" ./gradlew --debug --scan --stacktrace :examples:bookshelf:run

