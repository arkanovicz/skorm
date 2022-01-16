#!/bin/bash

export SKORM_WEBAPP_URL=http://lcoalhost:8080
export SKORM_JDBC_URL=jdbc://...
export SKORM_JDBC_USER=
export SKORM_JDBC_PASS=
./gradlew -i --stacktrace :examples:bookshelf:run
