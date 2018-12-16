#!/usr/bin/env bash

mvn clean compile package -DskipTests

mkdir -p app
cp target/jetty-runner.jar app/jetty-runner.jar
cp target/wicket-cluster.war app/wicket-cluster.war