package com.snowplowanalytics.iglu.ctl

// java
import java.io.File
import java.util.UUID

import com.snowplowanalytics.iglu.ctl.PushCommand.HttpUrl

// scalaz
import scalaz._
import Scalaz._

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.{asJsonNode, fromJsonNode, compact, render}

// iglu scala client
import com.snowplowanalytics.iglu.client.ValidatedNel
import com.snowplowanalytics.iglu.client.{Resolver, SchemaKey}
import com.snowplowanalytics.iglu.client.validation.ValidatableJsonMethods.validateAgainstSchema
import com.snowplowanalytics.iglu.client.validation.ProcessingMessageMethods._
import com.snowplowanalytics.iglu.client.repositories.{HttpRepositoryRef, RepositoryRefConfig}

// this project
import FileUtils.getJsonFromFile
import Command.IgluctlAction


case class DeployCommand(config: File) extends Command.CtlCommand {

  implicit val formats: DefaultFormats = DefaultFormats

  implicit val resolver: Resolver = {
    val igluCentral = RepositoryRefConfig("Iglu central", 0, List("com.snowplowanalytics"))
    val httpRepository = HttpRepositoryRef(igluCentral, "http://iglucentral.com")
    val mirrorIgluCentral = RepositoryRefConfig("Iglu Central - GCP Mirror", 1, List("com.snowplowanalytics"))
    val mirrorHttpRepository = HttpRepositoryRef(mirrorIgluCentral, "http://mirror01.iglucentral.com")

    Resolver(500, List(httpRepository, mirrorHttpRepository))
  }

  /**
    * Primary method of static deploy command
    * Performs usual schema workflow at once, per configuration file
    * Short-circuits on first failed step
    */
  def process(): Unit = {

    val igluctlConfig = for {
      configDoc <- getJsonFromFile(config)
      schemaKey <- SchemaKey.parseNel("iglu:com.snowplowanalytics.iglu/igluctl_config/jsonschema/1-0-0")
      configSchema <- resolver.lookupSchema(schemaKey)
      config <- validateAgainstSchema(asJsonNode(configDoc).get("data"), configSchema)
      igluctlConfig <- extractIgluctlConfig(fromJsonNode(config))
    } yield igluctlConfig

    igluctlConfig match {
      case Success(ctlConfig) =>
        ctlConfig.lintCommand.process()
        ctlConfig.generateCommand.processDdl()
        ctlConfig.actions.foreach{
          case pc: PushCommand => pc.process()
          case sp: S3cpCommand => sp.process()
        }
      case Failure(err) =>
        sys.error(err.toString)
        sys.exit(1)
    }
  }

  def extractIgluctlConfig(config: JValue): ValidatedNel[IgluctlConfig] = {
    val description = (config \ "description").extractOpt[String]
    val jInput: JValue = config \ "input"
    val jLint: JValue = config \ "lint"
    val jGenerate: JValue = config \ "generate"
    val jActions: JValue = config \ "actions"

    val lint: LintCommand = (jInput merge jLint).extract[LintCommand]
    val generate: GenerateCommand = (jInput merge jGenerate).extract[GenerateCommand]
    val actions = extractActions(jInput.extract[String], jActions)

    actions.map{ IgluctlConfig(description, new File(jInput.extract[String]), lint, generate, _) }
  }

  def extractKey[A](json: JValue, key: String)(implicit ev: Manifest[A]): Either[String, A] =
    try {
      Right((json \ key).extract[A])
    } catch {
      case _: MappingException => Left(s"Cannot extract key $key from ${compact(render(json))}")
    }

  def extractAction(input: String, actionDoc: JValue): ValidatedNel[IgluctlAction] = {
    actionDoc \ "action" match {
      case JString("s3cp") =>
        val command: Either[String, S3cpCommand] = for {
          bucket <- extractKey[String](actionDoc, "bucketPath")
          accessKeyId <- extractKey[Option[String]](actionDoc, "accessKeyId")
          secretAccessKey <- extractKey[Option[String]](actionDoc, "secretAccessKey")
          profile <- extractKey[Option[String]](actionDoc, "profile")
          region <- extractKey[Option[String]](actionDoc, "region")
        } yield S3cpCommand(new File(input), bucket, None, accessKeyId, secretAccessKey, profile, region)

        command.fold(err => err.toProcessingMessageNel.failure, s3cp => s3cp.success)
      case JString("push") =>
        val command: Either[String, PushCommand] = for {
          registryRoot <- extractKey[HttpUrl](actionDoc, "registry")
          masterApiKey <- extractKey[UUID](actionDoc, "apikey")
          isPublic <- extractKey[Boolean](actionDoc, "isPublic")
        } yield PushCommand(registryRoot, masterApiKey, new File(input), isPublic)

        command.fold(err => err.toProcessingMessageNel.failure, push => push.success)
      case JString(action) => s"Unrecognized action $action".toProcessingMessageNel.failure
      case _ => "Action can only be a string".toProcessingMessageNel.failure
    }
  }

  def extractActions(input: String, actions: JValue): ValidatedNel[List[IgluctlAction]] = {
    actions match {
      case JNull => List.empty[IgluctlAction].success
      case JArray(actions: List[JValue]) => actions.traverse(extractAction(input, _))
      case _ => "Actions can be either array or null.".toProcessingMessageNel.failure
    }
  }

}
