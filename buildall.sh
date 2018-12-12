#!/usr/bin/env bash

if [[ "$#" -lt 1 || "$1" = "--help" ]]; then
	echo "Please provide docker tag"
	exit
fi

mvn clean package dependency:copy-dependencies

docker build -t jotak/demo-mesh-arena-ui:$1 ./services/ui
docker build -t jotak/demo-mesh-arena-ball:$1 ./services/ball
docker build -t jotak/demo-mesh-arena-stadium:$1 ./services/stadium
docker build -t jotak/demo-mesh-arena-ai:$1 ./services/ai
