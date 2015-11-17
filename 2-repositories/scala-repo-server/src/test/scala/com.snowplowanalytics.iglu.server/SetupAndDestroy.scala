/*
* Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0, and
* you may not use this file except in compliance with the Apache License
* Version 2.0.  You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the Apache License Version 2.0 is distributed on an "AS
* IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
* implied.  See the Apache License Version 2.0 for the specific language
* governing permissions and limitations there under.
*/
package com.snowplowanalytics.iglu.server

// This project
import util.ServerConfig

// Slick
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.{ StaticQuery => Q }

trait SetupAndDestroy extends BeforeAndAfterAll {
  private val dbName = getClass.getSimpleName.toLowerCase

  private val db = Database.forURL(
    url =  s"jdbc:postgresql://${ServerConfig.pgHost}:${ServerConfig.pgPort}/" +
      s"${ServerConfig.pgDbName}",
    user = ServerConfig.pgUsername,
    password = ServerConfig.pgPassword,
    driver = ServerConfig.pgDriver
  )

  val initializationQuery = """
  delete from apikeys;
  delete from schemas where name != 'schema';
  insert into apikeys (uid, vendor_prefix, permission, createdat) values ('d0ca1d61-f6a8-4b40-a421-dbec5b9cdbad', 'com.benfradet','super',current_timestamp);
  insert into apikeys (uid, vendor_prefix, permission, createdat) values ('83e7c051-cd68-4e44-8b36-09182fa158d5', 'com.benfradet','write',current_timestamp);
  insert into apikeys (uid, vendor_prefix, permission, createdat) values ('a89c5f27-fe76-4754-8a07-d41884af1074', 'com.snowplowanalytics','write',current_timestamp);
  insert into apikeys (uid, vendor_prefix, permission, createdat) values ('6eadba20-9b9f-4648-9c23-770272f8d627', 'com.snowplowanalytics','read',current_timestamp);

  insert into schemas values(10000, 'com.snowplowanalytics.snowplow', 'ad_click', 'jsonschema', '1-0-0', '{
    "$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
    "description": "Schema for an ad click event",
    "self": {
      "vendor": "com.snowplowanalytics.snowplow",
      "name": "ad_click",
      "format": "jsonschema",
      "version": "1-0-0"
    },

    "type": "object",
    "properties": {
      "clickId": {
        "type": "string"
      },
      "impressionId": {
        "type": "string"
      },
      "zoneId": {
        "type": "string"
      },
      "bannerId": {
        "type": "string"
      },
      "campaignId": {
        "type": "string"
      },
      "advertiserId": {
        "type": "string"
      },
      "targetUrl": {
        "type": "string",
        "minLength": 1
      },
      "costModel": {
        "enum": ["cpa", "cpc", "cpm"]
      },
      "cost": {
        "type": "number",
        "minimum": 0
      }
    },
    "required": ["targetUrl"],
    "dependencies": {"cost": ["costModel"]},
    "additionalProperties": false
  }', current_timestamp, current_timestamp, 'f');

  insert into schemas values(20000, 'com.snowplowanalytics.snowplow', 'ad_click', 'jsonschema', '1-0-1', '{
     "$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
     "description": "Schema for an ad click event",
     "self": {
       "vendor": "com.snowplowanalytics.snowplow",
       "name": "ad_click",
       "format": "jsonschema",
       "version": "1-0-1"
     },

     "type": "object",
     "properties": {
       "clickId": {
         "type": "string"
       },
       "impressionId": {
         "type": "string"
       },
       "zoneId": {
         "type": "string"
       },
       "bannerId": {
         "type": "string"
       },
       "campaignId": {
         "type": "string"
       },
       "advertiserId": {
         "type": "string"
       },
       "targetUrl": {
         "type": "string",
         "minLength": 1
       },
       "costModel": {
         "enum": ["cpa", "cpc", "cpm"]
       },
       "cost": {
         "type": "number",
         "minimum": 0
       }
     },
     "required": ["targetUrl"],
     "dependencies": {"cost": ["costModel"]},
     "additionalProperties": false
  }', current_timestamp, current_timestamp, 'f');

  insert into schemas values(30000, 'com.snowplowanalytics.snowplow', 'ad_click2', 'jsonschema', '1-0-1', '{
     "$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
     "description": "Schema for an ad click event",
     "self": {
       "vendor": "com.snowplowanalytics.snowplow",
       "name": "ad_click2",
       "format": "jsonschema",
       "version": "1-0-1"
     },

     "type": "object",
     "properties": {
       "clickId": {
         "type": "string"
       },
       "impressionId": {
         "type": "string"
       },
       "zoneId": {
         "type": "string"
       },
       "bannerId": {
         "type": "string"
       },
       "campaignId": {
         "type": "string"
       },
       "advertiserId": {
         "type": "string"
       },
       "targetUrl": {
         "type": "string",
         "minLength": 1
       },
       "costModel": {
         "enum": ["cpa", "cpc", "cpm"]
       },
       "cost": {
         "type": "number",
         "minimum": 0
       }
     },
     "required": ["targetUrl"],
     "dependencies": {"cost": ["costModel"]},
     "additionalProperties": false
  }', current_timestamp, current_timestamp, 'f');

  insert into schemas values(40000, 'com.benfradet.ben', 'ad_click2', 'jsonschema', '1-0-0', '{
     "$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
     "description": "Schema for an ad click event",
     "self": {
       "vendor": "com.benfradet.ben",
       "name": "ad_click2",
       "format": "jsonschema",
       "version": "1-0-1"
     },

    "type": "object",
    "properties": {
      "clickId": {
        "type": "string"
      },
      "impressionId": {
        "type": "string"
      },
      "zoneId": {
        "type": "string"
      },
      "bannerId": {
        "type": "string"
      },
      "campaignId": {
        "type": "string"
      },
      "advertiserId": {
        "type": "string"
      },
      "targetUrl": {
        "type": "string",
        "minLength": 1
      },
      "costModel": {
        "enum": ["cpa", "cpc", "cpm"]
      },
      "cost": {
        "type": "number",
        "minimum": 0
      }
    },
    "required": ["targetUrl"],
    "dependencies": {"cost": ["costModel"]},
    "additionalProperties": false
  }', current_timestamp, current_timestamp, 't');

  insert into schemas values(50000, 'com.benfradet.ben', 'ad_click', 'jsonschema', '1-0-0', '{
     "$schema": "http://iglucentral.com/schemas/com.snowplowanalytics.self-desc/schema/jsonschema/1-0-0#",
     "description": "Schema for an ad click event",
     "self": {
       "vendor": "com.benfradet.ben",
       "name": "ad_click",
       "format": "jsonschema",
       "version": "1-0-0"
     },

    "type": "object",
    "properties": {
      "clickId": {
        "type": "string"
      },
      "impressionId": {
        "type": "string"
      },
      "zoneId": {
        "type": "string"
      },
      "bannerId": {
        "type": "string"
      },
      "campaignId": {
        "type": "string"
      },
      "advertiserId": {
        "type": "string"
      },
      "targetUrl": {
        "type": "string",
        "minLength": 1
      },
      "costModel": {
        "enum": ["cpa", "cpc", "cpm"]
      },
      "cost": {
        "type": "number",
        "minimum": 0
      }
    },
    "required": ["targetUrl"],
    "dependencies": {"cost": ["costModel"]},
    "additionalProperties": false
  }', current_timestamp, current_timestamp, 'f');"""

  def beforeAll() {
    db withDynSession {
      Q.updateNA(s"drop database if exists ${dbName};").execute
      Q.updateNA(s"create database ${dbName};").execute
      TableInitialization.initializeTables()
      Q.updateNA(initializationQuery).execute
    }
  }

  def afterAll() {
    db withDynSession {
      Q.updateNA(s"""
        delete from apikeys;
        delete from schemas where name != 'schema';
        drop database $dbName
      """).execute

    }
  }

  val database = Database.forURL(
    url = s"jdbc:postgresql://${ServerConfig.pgHost}:${ServerConfig.pgPort}/" +
      s"${dbName}",
    user = ServerConfig.pgUsername,
    password = ServerConfig.pgPassword,
    driver = ServerConfig.pgDriver
  )
}
