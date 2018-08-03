package com.snowplowanalytics.iglu.ctl

// this project
import Command.IgluctlAction


case class IgluctlConfig(
  description: Option[String],
  lintCommand: LintCommand,
  generateCommand: GenerateCommand,
  actions: List[IgluctlAction]
)
