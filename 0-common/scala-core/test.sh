#!/bin/sh

set -e

# sbt +test --warn
# sbt "project igluCoreCirce" +test --warn
# sbt "project igluCoreJson4s" +test --warn


# sbt +publishLocal --warn
# sbt "project igluCoreCirce" +publishLocal --warn
sbt "project igluCoreJson4s" +publishLocal --warn

