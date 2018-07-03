/*
 * Copyright (c) 2012-2016 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.iglu.schemaddl.sql

case class Begin(isolationLevel: Option[IsolationLevel.type], permission: Option[Permission]) extends Statement {
  def toDdl = {
    val attrs = List(isolationLevel, permission).flatten.map(_.toDdl)
    s"BEGIN TRANSACTION${envelope(attrs)}"
  }

  private def envelope(attrs: List[String]): String = attrs match {
    case t :: _ => attrs.mkString(" ")
    case Nil    => ""
  }
}

sealed trait Permission extends Ddl
case object ReadWriteIsolation extends Permission { def toDdl = "READ WRITE" }
case object ReadOnly extends Permission { def toDdl = "READ ONLY" }

case object IsolationLevel extends Ddl {
  def toDdl = "ISOLATION LEVEL SERIALIZABLE"
}
