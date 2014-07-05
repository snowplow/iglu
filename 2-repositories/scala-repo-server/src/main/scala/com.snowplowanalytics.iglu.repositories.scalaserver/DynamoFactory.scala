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
  def getStore: Store[String, String]
}

object DynamoFactory extends DBFactory {
  private val awsAccessKey = "xxx"
  private val awsSecretKey = "yyy"
  private val tableName = "Schemas"
  private val primaryKeyColumn = "SchemaID"
  private val valueColumn = "Schema"

  def getStore: DynamoStringStore = DynamoStringStore(
    awsAccessKey, awsSecretKey, tableName, primaryKeyColumn, valueColumn)
}
