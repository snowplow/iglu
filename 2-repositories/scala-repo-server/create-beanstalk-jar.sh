#!/bin/sh
rm -rf target
sbt assembly
tail -c +44 `ls target/scala-2.10/iglu-server-*` > iglu-server.jar
