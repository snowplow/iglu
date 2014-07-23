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
package com.snowplowanalytics.iglu.server
package util

// Scala
import scala.concurrent.{ ExecutionContext, Future }

// Spray
import spray.routing.{ RequestContext,
                       Rejection,
                       MalformedQueryParamRejection,
                       MissingQueryParamRejection }

object JsonSchemaValidator {
  
  type JsonExtractor = RequestContext => Option[String]

  object JsonExtraction {
    def fromQueryString(parameterName: String): JsonExtractor = {
      context: RequestContext =>
        context.request.uri.query.get(parameterName)
    }
  }

  class JsonSchemaValidator[T](extractor: JsonExtractor,
    validator: (String => Future[Option[T]]), parameterName: String)
    (implicit executionContext: ExecutionContext) {

    def apply(context: RequestContext): Future[Either[Rejection, T]] =
      extractor(context) match {
        case None => Future(Left(MissingQueryParamRejection(parameterName)))
        case Some(json) =>
          validator(json) map {
            case Some(t) => Right(t)
            case None => Left(MalformedQueryParamRejection(parameterName,
              "The json provided is not a valid self-describing json"))
          }
      }
  }

  def apply[T](parameterName: String)(validator: (String => Future[Option[T]]))
    (implicit executionContext: ExecutionContext) = {

    def extractor(context: RequestContext) =
      JsonExtraction.fromQueryString(parameterName)(context)

    new JsonSchemaValidator(extractor, validator, parameterName)
  }
}
