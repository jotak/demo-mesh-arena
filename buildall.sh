#!/usr/bin/env bash

mvn package dependency:copy-dependencies

docker build -t jotak/demo-mesh-arena-ui ./services/ui
docker build -t jotak/demo-mesh-arena-ball ./services/ball
docker build -t jotak/demo-mesh-arena-stadium ./services/stadium
docker build -t jotak/demo-mesh-arena-ai ./services/ai
