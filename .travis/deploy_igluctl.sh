#!/bin/bash

set -e

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

cd "${TRAVIS_BUILD_DIR}/0-common/igluctl"
project_version=$(sbt version -Dsbt.log.noformat=true | tail -n 1 | perl -ne 'print $1 if /(\d+\.\d+[^\r\n]*)/')
if [ "${project_version}" == "${release}" ]; then
    # local publish only dependency, scala-core
    cd "${TRAVIS_BUILD_DIR}/0-common/scala-core"
    sbt +publishLocal
    sbt "project igluCoreCirce" +publishLocal --warn
    sbt "project igluCoreJson4s" +publishLocal --warn
    # universal publish schema-ddl
    cd "${TRAVIS_BUILD_DIR}/0-common/schema-ddl"
    sbt +publishLocal --warn
else
    echo "Tag version '${release}' doesn't match version in scala project ('${project_version}'). Aborting!"
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
