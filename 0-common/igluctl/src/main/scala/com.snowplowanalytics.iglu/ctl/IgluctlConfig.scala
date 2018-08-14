package com.snowplowanalytics.iglu.ctl

// java
import java.io.File

// this project
import Command.IgluctlAction


case class IgluctlConfig(
  description: Option[String],
  input:       File,
  lintCommand: LintCommand,
  generateCommand: GenerateCommand,
  actions: List[IgluctlAction]
)
