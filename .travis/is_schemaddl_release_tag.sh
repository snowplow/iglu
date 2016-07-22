#!/bin/bash

tag=$1
cicd=${tag:0:10}
release=${tag:10}

if [ "${cicd}" == "schemaddl/" ]; then
  if [ "${release}" == "" ]; then
    echo "Warning! No release specified! Ignoring."
    exit 2
  fi
  exit 0
else
  exit 1
fi
