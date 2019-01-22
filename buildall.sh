#!/usr/bin/env bash

if [[ "$#" -lt 2 || "$1" = "--help" ]]; then
	echo "Please provide docker namespaces and tag"
	echo "Ex: ./buildall.sh jotak dev"
	exit
fi

mvn clean package dependency:copy-dependencies

docker build -t $1/demo-mesh-arena-ui:$2 ./services/ui
docker build -t $1/demo-mesh-arena-ball:$2 ./services/ball
docker build -t $1/demo-mesh-arena-stadium:$2 ./services/stadium
docker build -t $1/demo-mesh-arena-ai:$2 ./services/ai
