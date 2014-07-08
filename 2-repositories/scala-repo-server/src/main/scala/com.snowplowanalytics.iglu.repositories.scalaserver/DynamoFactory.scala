/*
* Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0,
* and you may not use this file except in compliance with the
* Apache License Version 2.0.
* You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the Apache License Version 2.0 is distributed on
* an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
* express or implied.  See the Apache License Version 2.0 for the specific
* language governing permissions and limitations there under.
*/
package com.snowplowanalytics.iglu.repositories.scalaserver

// Storehaus
import com.twitter.storehaus.dynamodb.DynamoStringStore
import com.twitter.storehaus.Store

trait DBFactory {
  val schemaStore: Store[String, String]
  protected val schemaTableName = "Schemas"
  protected val schemaPrimaryKey = "SchemaID"
  protected val schemaValueColumn = "Schema"

  val apiKeyStore: Store[String, String]
  protected val apiKeyTableName = "ApiKeys"
  protected val apiKeyPrimaryKey = "ApiKey"
  protected val apiKeyValueColumn = "Permission"
}

object DynamoFactory extends DBFactory {
  private val awsAccessKey = "xxx"
  private val awsSecretKey = "yyy"

  val schemaStore: DynamoStringStore = DynamoStringStore(
    awsAccessKey, awsSecretKey,
    schemaTableName, schemaPrimaryKey, schemaValueColumn)

  val apiKeyStore: DynamoStringStore = DynamoStringStore(
    awsAccessKey, awsSecretKey,
    apiKeyTableName, apiKeyPrimaryKey, apiKeyValueColumn)
}
