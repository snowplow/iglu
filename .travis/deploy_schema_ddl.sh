#!/bin/bash

tag=$1

project="schema-ddl/"
project_len=${#project}

cicd=${tag:0:${project_len}}
release=${tag:${project_len}}

if [ "${cicd}" == "${project}" ]; then
    if [ "${release}" == "" ]; then
        echo "WARNING! No release specified! Ignoring."
        exit 2
    fi
else
    echo "This can't be deployed - there's no ${project} tag! (Is the travis condition set?    )"
    exit 1
fi

mkdir ~/.bintray/
FILE=$HOME/.bintray/.credentials
cat <<EOF >$FILE
realm = Bintray API Realm
host = api.bintray.com
user = $BINTRAY_SNOWPLOW_MAVEN_USER
password = $BINTRAY_SNOWPLOW_MAVEN_API_KEY
EOF

cd "${TRAVIS_BUILD_DIR}/0-common/schema-ddl"

project_version=$(sbt version -Dsbt.log.noformat=true | tail -n 1 | perl -ne 'print $1 if /(\d+\.\d+[^\r\n]*)/')

if [ "${project_version}" == "${release}" ]; then
    # local publish only dependency, scala-core
    cd "${TRAVIS_BUILD_DIR}/0-common/scala-core"
    sbt +test --warn
    sbt +publishLocal
    sbt project igluCoreCirce +test --warn
    sbt project igluCoreCirce +publishLocal --warn
    sbt project igluCoreJson4s +test --warn
    sbt project igluCoreJson4s +publishLocal --warn
    # universal publish schema-ddl
    cd "${TRAVIS_BUILD_DIR}/0-common/schema-ddl"
    sbt +test --warn
    sbt +publish
    sbt +bintraySyncMavenCentral

else
    echo "Tag version '${release}' doesn't match version in scala project ('${project_version}'). Aborting!"
    exit 1
fi
