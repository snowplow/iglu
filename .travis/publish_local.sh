#!/bin/bash

set -e

cd "${TRAVIS_BUILD_DIR}/0-common/scala-core"
sbt +publishLocal
sbt "project igluCoreCirce" +publishLocal --warn
sbt "project igluCoreJson4s" +publishLocal --warn

cd "${TRAVIS_BUILD_DIR}/0-common/schema-ddl"
sbt +publishLocal --warn
