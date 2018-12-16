#!/usr/bin/env bash

#mvn clean compile package

java -server -Dnode.name=node2 -jar app/jetty-runner.jar --port 8081 app/wicket-cluster.war