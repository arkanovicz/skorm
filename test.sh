#!/bin/bash

killgradle.sh

export SKORM_WEBAPP_URL=http://localhost:8080
export SKORM_JDBC_URL=jdbc:hsqldb:mem:example
export SKORM_JDBC_USER=sa
export SKORM_JDBC_PASS=

cd examples

./gradlew run

