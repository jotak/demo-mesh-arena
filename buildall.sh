#!/usr/bin/env bash

if [[ "$#" -lt 2 || "$1" = "--help" ]]; then
	echo "Please provide docker namespaces and tag"
	echo "Ex: ./buildall.sh jotak dev"
	exit
fi

mvn clean package dependency:copy-dependencies

if [ $? -ne 0 ]; then
    exit
fi

docker build -t $1/demo-mesh-arena-ui:$2 ./services/ui
docker build -t $1/demo-mesh-arena-ball:$2 ./services/ball
docker build -t $1/demo-mesh-arena-stadium:$2 ./services/stadium
docker build -t $1/demo-mesh-arena-ai:$2 ./services/ai

docker build -t $1/demo-mesh-arena-oj9-ui:$2 -f ./services/ui/Dockerfile-oj9 ./services/ui
docker build -t $1/demo-mesh-arena-oj9-ball:$2 -f ./services/ball/Dockerfile-oj9 ./services/ball
docker build -t $1/demo-mesh-arena-oj9-stadium:$2 -f ./services/stadium/Dockerfile-oj9 ./services/stadium
docker build -t $1/demo-mesh-arena-oj9-ai:$2 -f ./services/ai/Dockerfile-oj9 ./services/ai
