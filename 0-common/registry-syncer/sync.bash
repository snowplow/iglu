#!/bin/bash

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

# Script to upload all schemas in a folder to the Iglu registry
# Takes three arguments: the target registry host, the API key, and the input 
# schema directory

# Uses PUT rather than POST, so existing schemas are overwritten

# Note that this script assumes the target Scala Registry Server is empty:
# it has no intelligent synchronization routine yet

echo "================================="
echo "  Starting Iglu Registry Syncer"
echo "---------------------------------"

set -e;

if [ "$#" -ne 3 ]
then
  echo "ERROR: 3 arguments required, $# provided"
  echo "Usage: $0 HOST APIKEY PATH"
  exit 1
fi

host=$1;
apikey=$2;
schemafolder=$3;

good_counter=0

# Function sending POST request with first argument
upload () {
  destination="$host/api/schemas/$(
    # Keep the last 4 slash-separated components of the filename
    echo $1 | awk -F '/' '{print $(NF-3)"/"$(NF-2)"/"$(NF-1)"/"$(NF)}';
  )";
  echo "Uploading schema from file:";
  echo "'$1'";
  echo "To endpoint:";
  echo "'$destination'";
  echo "";
  result="$(curl --silent "${destination}?isPublic=false" -XPUT -d @$1 -H "apikey: $write_api_key" --fail)";

  # Process result
  status="$(echo ${result} | python -c 'import json,sys;obj=json.load(sys.stdin);print obj["status"]')"
  if [[ "${status}" -eq "200" ]] || [[ "${status}" -eq "201" ]]; then
    let good_counter=good_counter+1
  fi
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

# Generate read/write apikeys

echo ""
echo "Making all_vendor API Keys:"
echo "curl --silent ${host}/api/auth/keygen -X POST -H "apikey: ${apikey}" -d "vendor_prefix=*""
api_keys="$(curl --silent ${host}/api/auth/keygen -X POST -H "apikey: ${apikey}" -d "vendor_prefix=*")"
write_api_key="$(echo ${api_keys} | python -c 'import json,sys;print(json.load(sys.stdin)["write"])')"
read_api_key="$(echo ${api_keys} | python -c 'import json,sys;print(json.load(sys.stdin)["read"])')"
echo $api_keys
echo "Keys: $(echo ${api_keys} | xargs)"

# Upload found keys

echo -e "\nUploading all Schemas found in ${schemafolder}\n"

for schemapath in $(find $schemafolder -type f); do
  if isJsonSchema $schemapath; then
    upload $schemapath;
  fi
done;

# Output results

echo ""
echo "Result Counts:"
echo " - Schemas uploaded: ${good_counter}"

echo ""
echo "Remove created API Keys:"
echo " - Remove ${write_api_key}: $(curl --silent ${host}/api/auth/keygen -X DELETE -H "apikey: ${apikey}" -d "key=${write_api_key}" | xargs)"
echo " - Remove ${read_api_key}: $(curl --silent ${host}/api/auth/keygen -X DELETE -H "apikey: ${apikey}" -d "key=${read_api_key}" | xargs)"

echo ""
echo "--------"
echo "  Done  "
echo "========"

