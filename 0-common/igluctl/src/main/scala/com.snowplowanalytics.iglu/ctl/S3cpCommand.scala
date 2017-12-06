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

// java
import java.io.File

// scalaz
import scalaz._
import Scalaz._

// awscala
import awscala._
import awscala.s3._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.{AmazonClientException, AmazonServiceException}

// This project
import FileUtils._
import Utils._

/**
 * Class holding arguments passed from shell into `static s3cp` igluctl command
 * and command's main logic
 *
 * @param inputDir directory with JSON Schemas or any other files
 * @param bucketName S3 bucket name, without protocol or additional path
 * @param path optional path on S3 bucket
 * @param accessKeyId AWS Access Key Id
 * @param secretAccessKey AWS Secret Access Key
 * @param profile AWS profile name
 * @param region optional AWS region
 */
case class S3cpCommand(
    inputDir: File,
    bucketName: String,
    path: Option[String],
    accessKeyId: Option[String],
    secretAccessKey: Option[String],
    profile: Option[String],
    region: Option[String])
  extends Command.CtlCommand {
  import S3cpCommand._

  /**
   * Try to extract credentials from following sources:
   * 1. From key options passed to igluctl
   * 2. From profile name passed to igluctl (searched by AWS SDK)
   * 3. From default sources provided by AWS SDK
   */
  lazy val credentials: String \/ Credentials = {
    val xor = (accessKeyId, secretAccessKey, profile) match {
      case (Some(keyId), Some(secret), None) =>
        Credentials(keyId, secret).right
      case (None, None, Some(p)) =>
        val provider = new ProfileCredentialsProvider(p)
        \/.fromTryCatch(provider.getCredentials).map { c =>
          Credentials(c.getAWSAccessKeyId, c.getAWSSecretKey)
        }
      case _ =>
        val provider = DefaultCredentialsProvider()
        \/.fromTryCatch(provider.getCredentials)
    }
    xor.leftMap(t => Option(t.getMessage).getOrElse(t.toString))
  }

  lazy val awsRegion = region.map(Region.apply).getOrElse(Region.default())

  lazy val s3 = for { creds <- credentials } yield S3(creds)(awsRegion)

  lazy val bucket = Bucket(bucketName)

  def process(): Unit = {
    val schemasT = for {
      service <- fromXor(s3)
      file    <- fromXors(getFiles)
      key      = getS3Path(file)
      result  <- fromXor(upload(file, key, service))
    } yield result

    val results = schemasT.run

    val total = results.foldLeft(Total.empty)((total, report) => total.add(report))
    total.exit()
  }

  /**
   * Stream all files of get single file is `inputDir` isn't directory
   */
  def getFiles: Stream[String \/ File] = {
    if (inputDir.isDirectory) streamAllFiles(inputDir).map(_.right)
    else Stream(inputDir.right)
  }

  /**
   * Legacy method intended to upload only JSON Schemas, skipping all other files
   * @todo remove
   */
  def uploadSchemas(): Unit = {
    val jsons = getJsonFilesStream(inputDir, Some(filterJsonSchemas))

    val schemasT = for {
      service <- fromXor(s3)
      json    <- fromXors(jsons.map(_.disjunction))
      schema  <- fromXor(extractSchema(json))
      result  <- fromXor(upload(json.origin, schema.self.toPath, service))
    } yield result

    val results = schemasT.run

    val total = results.foldLeft(Total.empty)((total, report) => total.add(report))
    total.exit()
  }

  /**
   * Exception-free upload file to Amazon S3
   *
   * @param file file object ready to be upload on S3. AWS SDK will use its
   *             metadata on S3
   * @param path full path on AWS S3
   * @param service S3 client object
   * @return either error or successful message
   */
  def upload(file: File, path: String, service: S3): String \/ String = {
    try {
      val result = service.put(bucket, path, file)
      s"File [${file.getPath}] uploaded as [s3://${bucketName + "/" + result.key}]".right
    } catch {
      case e: AmazonClientException => e.toString.left
      case e: AmazonServiceException => e.toString.left
    }
  }

  /**
   * Get full S3 path for particular file based on its path of filesystem and
   * specified S3 root
   *
   * @param file file object ready to be upload to S3
   * @return full S3 path
   */
  def getS3Path(file: File): String = {
    val pathOnS3: String = {
      val path = file.getAbsolutePath.drop(inputDir.getAbsolutePath.length + 1)
      if (FileUtils.separator == """\""")
        path.replace(FileUtils.separator, """/""")
      else
        path
    }

    val bucketPath = path match {
      case Some("/") => ""
      case Some(p) if p.endsWith("/") => p
      case Some(p) => p + "/"
      case None => ""
    }

    if (inputDir.isDirectory) {
      bucketPath + inputDir.getName + "/" + pathOnS3
    } else {
      bucketPath + inputDir.getName
    }
  }
}

object S3cpCommand {

  /**
   * End-of-the-world class, containing info about success/failure of execution
   *
   * @param successes number of successfully validated schemas
   * @param failures number of schemas with errors
   */
  case class Total(successes: Int, failures: Int) {
    /**
     * Exit from app with error status if invalid schemas were found
     */
    def exit(): Unit = {
      println(s"TOTAL: $successes files were successfully uploaded")
      println(s"TOTAL: $failures errors were encountered")

      if (failures > 0) sys.exit(1)
      else sys.exit(0)
    }

    /**
     * Append and print report for another file
     *
     * @param report file processing result
     * @return modified total object
     */
    def add(report: String \/ String): Total = {
      report match {
        case \/-(s) =>
          println(s"SUCCESS: $s")
          copy(successes = successes + 1)
        case -\/(f) =>
          println(s"FAILURE: $f")
          copy(failures = failures + 1)
      }
    }
  }

  object Total {
    val empty = Total(0, 0)
  }
}
