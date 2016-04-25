#!/bin/sh

# Copyright (c) 2015-2016 Snowplow Analytics Ltd. All rights reserved.
#
# This program is licensed to you under the Apache License Version 2.0, and
# you may not use this file except in compliance with the Apache License
# Version 2.0.  You may obtain a copy of the Apache License Version 2.0 at
# http://www.apache.org/licenses/LICENSE-2.0.
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the Apache License Version 2.0 is distributed on an "AS
# IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied.  See the Apache License Version 2.0 for the specific language
# governing permissions and limitations there under.

# Script to upload all schemas in a folder to the Iglu repo
# Takes three arguments: the target repo host, the API key, and the input schema directory
# Uses PUT rather than POST, so existing schemas are overwritten

# Note that this script assumes the target Scala Repo Server is empty:
# it has no intelligent synchronization routine yet

set -e;

host=$1;
apikey=$2;
schemafolder=$3;

# Function sending POST request with first argument
upload () {
  destination="$host/api/schemas/$(
    # Keep the last 4 slash-separated components of the filename
    echo $1 | awk -F '/' '{print $(NF-3)"/"$(NF-2)"/"$(NF-1)"/"$(NF)}';
  )";
  echo "\nUploading schema in file '$1' to endpoint '$destination'";
  curl "${destination}?isPublic=true" -XPOST -d @$1 -H "apikey: $apikey";
}

# Predicate checking if first argument contains `jsonschema` as its format
isJsonSchema () {
  #         1. VENDOR       2. NAME        3. FORMAT      4. SCHEMAVER
  [[ $1 =~ ([a-zA-Z._-]+)\/([a-zA-Z_-]+)\/([a-zA-Z_-]+)\/([1-9][0-9]*-[0-9]*-[0-9]*) ]]
  if [[ ${BASH_REMATCH[3]} = "jsonschema" ]]; then
    return 0;
  else
    return 1;
  fi
}

for schemapath in $(find $schemafolder -type f); do
  if isJsonSchema $schemapath; then
    upload $schemapath;
  fi
done;

