/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
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

// swagger
import io.swagger.models.auth.{ApiKeyAuthDefinition, In}

// swagger-akka-http
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.{Contact, Info, License}

// This project
import service.{ApiKeyGenService, SchemaService, ValidationService}
import util.ServerConfig


class SwaggerDocService(serverConfig: ServerConfig) extends SwaggerHttpService {
  override val apiClasses: Set[Class[_]] = Set(
    classOf[ApiKeyGenService],
    classOf[SchemaService],
    classOf[ValidationService]
  )
  override val host = s"${serverConfig.interface}:${serverConfig.port}"
  override val unwantedDefinitions = Seq("Function1", "Function1RequestContextFutureRouteResult")
  override def securitySchemeDefinitions = Map("APIKeyHeader" -> new ApiKeyAuthDefinition("apikey", In.HEADER) )
  override def info = Info(
  title = "Iglu Server",
  description =
    """This is the API documentation for the Iglu Server,
      |a machine-readable schema repository built by Snowplow Analytics.""".stripMargin,
  termsOfService = "https://snowplowanalytics.com/terms-of-service/",
  version = com.snowplowanalytics.iglu.server.generated.Settings.version,
  contact = Some(Contact(name = "Snowplow Analytics", url = "https://snowplowanalytics.com/company/contact-us/",
                        email = "contact@snowplowanalytics.com")),
  license = Some(License(name = "Apache License 2.0",
                         url = "https://github.com/snowplow/iglu/blob/master/LICENSE-2.0.txt"))
  )
}