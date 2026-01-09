#!/bin/bash

# ./gradlew -d --scan --stacktrace :skorm-gradle-plugin:publishToMavenLocal > log
./gradlew  --stacktrace :skorm-gradle-plugin:publishToMavenLocal

