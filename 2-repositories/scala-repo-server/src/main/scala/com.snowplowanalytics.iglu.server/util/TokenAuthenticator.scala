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
import spray.routing.{ RequestContext, AuthenticationFailedRejection }
import spray.routing.authentication.{ Authentication, ContextAuthenticator }

/**
 * Object letting us authenticate users.
 */
object TokenAuthenticator {

  type TokenExtractor = RequestContext => Option[String]

  /**
   * Extract the token from the specified header.
   */
  object TokenExtraction {
    def fromHeader(headerName: String): TokenExtractor = {
      context: RequestContext =>
        context.request.headers.
          find(_.name.toLowerCase == headerName).
          map(_.value) match {
            case None => Some("-")
            case s => s
          }
    }
  }

  /**
   * Under the hood class performing the authentication.
   * @param extractor extracting the token from the request
   * @param authenticator takes an API key and validates it through the dao
   */
  class TokenAuthenticator[T](extractor: TokenExtractor,
    authenticator: (String => Future[Option[T]]))
    (implicit executionContext: ExecutionContext)
    extends ContextAuthenticator[T] {

    import AuthenticationFailedRejection._

    /**
     * Performs the authentication.
     * @param context the http request context necessary to extract the token
     * @return a future containing the authentication which is of type
     * Either[Rejection, T]
     */
    def apply(context: RequestContext): Future[Authentication[T]] =
      extractor(context) match {
        //if there is no api_key header provided
        case None => Future(
          Left(AuthenticationFailedRejection(CredentialsMissing, List())))
        case Some(token) =>
          authenticator(token) map {
            //if the authentication succeeds
            case Some(t) => Right(t)
            //if the authentication fails
            case None =>
              Left(AuthenticationFailedRejection(CredentialsRejected, List()))
          }
      }
  }

  /**
   * Entry point to perform the authentication.
   * @param headerName containing the API key token to be extracted
   * @param authenticator function taking a string and returning something if
   * the authentication succeeds or nothing if the authentication fails
   */
  def apply[T](headerName: String)(authenticator: (String => Future[Option[T]]))
    (implicit executionContext: ExecutionContext) = {

    //Extracts the token from the http request
    def extractor(context: RequestContext) =
      TokenExtraction.fromHeader(headerName)(context)
    
    //Performs the authentication
    new TokenAuthenticator(extractor, authenticator)
  }
}
