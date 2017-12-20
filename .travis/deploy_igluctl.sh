#!/bin/bash

tag=$1

project="igluctl/"
project_len=${#project}

cicd=${tag:0:${project_len}}
release=${tag:${project_len}}

if [ "${cicd}" == "${project}" ]; then
    if [ "${release}" == "" ]; then
        echo "WARNING! No release specified! Ignoring."
        exit 2
    fi
else
    echo "This can't be deployed - there's no ${project} tag! (Is the travis condition set?)"
    exit 1
fi

cd "${TRAVIS_BUILD_DIR}"

export TRAVIS_BUILD_RELEASE_TAG="${release}"
release-manager \
    --config "./.travis/release_igluctl.yml" \
    --check-version \
    --make-version \
    --make-artifact \
    --upload-artifact
