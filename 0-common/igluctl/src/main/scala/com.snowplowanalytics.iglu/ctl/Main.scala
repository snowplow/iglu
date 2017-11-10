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
package com.snowplowanalytics.iglu.ctl

object Main extends App {
  Command.cliParser.parse(args, Command()).map(c => (c, c.toCommand)) match {
    case Some((_, Some(ddl: GenerateCommand))) =>
      ddl.processDdl()
    case Some((_, Some(sync: PushCommand))) =>
      sync.process()
    case Some((_, Some(lint: LintCommand))) =>
      lint.process()
    case Some((_, Some(s3cp: S3cpCommand))) =>
      s3cp.process()
    case Some((_, Some(java : GenerateJavaCommand)))  =>
      java.processJava()
    case Some((c, _)) if c.command.isEmpty || c.command.contains("static")=>
      Command.cliParser.showUsageAsError()
    case _ =>
      sys.exit(0)

  }
}
