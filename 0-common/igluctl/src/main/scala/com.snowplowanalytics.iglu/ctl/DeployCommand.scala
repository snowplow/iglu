package com.snowplowanalytics.iglu.ctl

// java
import java.io.File

// scalaz
import scalaz._

// json4s
import org.json4s._
import org.json4s.jackson.JsonMethods.{asJsonNode, fromJsonNode}

// iglu scala client
import com.snowplowanalytics.iglu.client.{Resolver, SchemaKey}
import com.snowplowanalytics.iglu.client.validation.ValidatableJsonMethods.validateAgainstSchema
import com.snowplowanalytics.iglu.client.repositories.{HttpRepositoryRef, RepositoryRefConfig}

// this project
import FileUtils.getJsonFromFile


case class DeployCommand(config: File) extends Command.CtlCommand {

  implicit val formats: DefaultFormats = DefaultFormats

  /**
    * Primary method of static deploy command
    * Performs usual schema workflow at once, per configuration file
    * Short-circuits on first failed step
    */
  def process(): Unit = {
    getJsonFromFile(config) match {
      case Success(configDoc) =>
        SchemaKey.parseNel("iglu:com.snowplowanalytics.iglu/igluctl_config/jsonschema/1-0-0") match {
          case Success(schemaKey) =>
            implicit val resolver: Resolver = getResolver()
            resolver.lookupSchema(schemaKey) match {
              case Success(configSchema) =>
                validateAgainstSchema(asJsonNode(configDoc).get("data"), configSchema) match {
                  case Success(doc) =>
                    val allConfig: JValue = fromJsonNode(doc)

                    val jInput: JValue = allConfig \ "input"
                    val jLint: JValue = allConfig \ "lint"
                    val jGenerate: JValue = allConfig \ "generate"
                    val jActions: JValue = allConfig \ "actions"

                    (jInput merge jLint).extractOpt[LintCommand] match {
                      case Some(lintCommand) =>
                        lintCommand.process()
                        (jInput merge jGenerate).extractOpt[GenerateCommand] match {
                          case Some(generateCommand) =>
                            generateCommand.processDdl()
                            jActions match {
                              case JNull => println("Operations are complete!")
                              case JArray(actions: List[JValue]) => ???
                              case _ =>
                                sys.error("Unexpected type for actions. It can be array or null!")
                                sys.exit(1)
                            }
                          case None =>
                            sys.error("Couldn't deserialize as GenerateCommand")
                            sys.exit(1)
                        }
                      case None =>
                        sys.error("Couldn't deserialize as LintCommand")
                        sys.exit(1)
                    }
                  case Failure(errors) =>
                    errors.foreach(msg => sys.error(msg.getMessage))
                    sys.exit(1)
                }
              case Failure(errors) =>
                errors.foreach(msg => sys.error(msg.getMessage))
                sys.exit(1)
            }
          case Failure(errors) =>
            errors.foreach(msg => sys.error(msg.getMessage))
            sys.exit(1)
        }
      case Failure(error) =>
        sys.error(error)
        sys.exit(1)
    }
  }

  /**
    * Prepare a Resolver
    * @return A Resolver instance
    */
  def getResolver(): Resolver = {
    val igluCentral = RepositoryRefConfig("Iglu central", 0, List("com.snowplowanalytics"))
    val httpRepository = HttpRepositoryRef(igluCentral, "http://iglucentral.com")
    val mirrorIgluCentral = RepositoryRefConfig("Iglu Central - GCP Mirror", 1, List("com.snowplowanalytics"))
    val mirrorHttpRepository = HttpRepositoryRef(mirrorIgluCentral, "http://mirror01.iglucentral.com")

    Resolver(500, List(httpRepository, mirrorHttpRepository))
  }
}
