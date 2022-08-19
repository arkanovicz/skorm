#!/bin/bash

## TODO - comment the 'skorm' section in :examples:bookshelf

./gradlew :skorm-common:jvmJar :skorm-common:publishJvmPublicationToMavenLocal
./gradlew :skorm-core:jvmJar :skorm-core:publishJvmPublicationToMavenLocal
./gradlew :skorm-gradle-plugin:publishToMavenLocal

## TODO - uncomment the 'skorm' section in :examples:bookshelf

export SKORM_WEBAPP_URL=http://localhost:8080
export SKORM_JDBC_URL=jdbc:hsqldb:mem:example
export SKORM_JDBC_USER=sa
export SKORM_JDBC_PASS=

./gradlew :examples:bookshelf:build :examples:bookshelf:run
