#!/bin/bash

set -e

tag=$1

cicd=${tag:0:11}
release=${tag:11}

if [ "${cicd}" == "scala-core/" ]; then
  if [ "${release}" == "" ]; then
    echo "Warning! No release specified! Ignoring."
    exit 2
  fi
  exit 0
else
  exit 1
fi
