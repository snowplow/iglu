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
package util

// Scala
import scala.concurrent.{ ExecutionContext, Future }

// Spray
import spray.routing.{ RequestContext, AuthenticationFailedRejection }
import spray.routing.authentication.{ Authentication, ContextAuthenticator }

object TokenAuthenticator{

  /**
   * Extract the token from the specified header
   */
  type TokenExtractor = RequestContext => Option[String]
  object TokenExtraction {
    def fromHeader(headerName: String): TokenExtractor = {
      context: RequestContext =>
        context.request.headers.find(_.name == headerName).map(_.value)
    }
  }

  class TokenAuthenticator[T](extractor: TokenExtractor,
    authenticator: (String => Future[Option[T]]))
    (implicit executionContext: ExecutionContext)
    extends ContextAuthenticator[T] {

    import AuthenticationFailedRejection._

    def apply(context: RequestContext): Future[Authentication[T]] =
      extractor(context) match {
        case None => Future(
          Left(AuthenticationFailedRejection(CredentialsMissing, List())))
        case Some(token) =>
          authenticator(token) map {
            case Some(t) => Right(t)
            case None =>
              Left(AuthenticationFailedRejection(CredentialsRejected, List()))
          }
      }
  }

  def apply[T](headerName: String)(authenticator: (String => Future[Option[T]]))
    (implicit executionContext: ExecutionContext) = {

    def extractor(context: RequestContext) =
      TokenExtraction.fromHeader(headerName)(context)
    
    new TokenAuthenticator(extractor, authenticator)
  }
}
