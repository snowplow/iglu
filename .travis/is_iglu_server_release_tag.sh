#!/bin/bash

set -e

tag=$1

cicd=${tag:0:12}
release=${tag:12}

if [ "${cicd}" == "iglu-server/" ]; then
  if [ "${release}" == "" ]; then
    echo "Warning! No release specified! Ignoring."
    exit 2
  fi
  exit 0
else
  exit 1
fi
