/*
 * Copyright (c) 2012-2016 Snowplow Analytics Ltd. All rights reserved.
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
import sbt._
import Keys._

object BuildSettings {

  // Basic settings for our app
  lazy val basicSettings = Seq[Setting[_]](
    organization          :=  "com.snowplowanalytics",
    version               :=  "0.4.0-M1",
    description           :=  "Set of Abstract Syntax Trees for various DDL and Schema formats",
    scalaVersion          :=  "2.10.6",
    crossScalaVersions    :=  Seq("2.10.6", "2.11.8"),
    scalacOptions         :=  Seq("-deprecation", "-encoding", "utf8",
                                  "-unchecked", "-feature",
                                  "-target:jvm-1.7"),
    scalacOptions in Test :=  Seq("-Yrangepos")
  )

  lazy val buildSettings = basicSettings
}
