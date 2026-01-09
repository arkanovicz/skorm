#!/bin/bash

set -e

git pull
./gradlew --refresh-dependencies clean
./gradlew publishKotlinMultiplatformPublicationToMavenLocal
./gradlew publishJvmPublicationToMavenLocal
./gradlew skorm-gradle-plugin:publishToMavenLocal
./gradlew skorm-jdbc:publishToMavenLocal

