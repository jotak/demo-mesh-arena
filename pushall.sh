#!/usr/bin/env bash

if [[ "$#" -lt 2 || "$1" = "--help" ]]; then
	echo "Please provide docker namespaces and tag"
	echo "Ex: ./buildall.sh jotak dev"
	exit
fi

OCI_BIN=`which podman 2>/dev/null || which docker 2>/dev/null`

$OCI_BIN push $1/demo-mesh-arena-ui:$2
$OCI_BIN push $1/demo-mesh-arena-ball:$2
$OCI_BIN push $1/demo-mesh-arena-stadium:$2
$OCI_BIN push $1/demo-mesh-arena-ai:$2

$OCI_BIN push $1/demo-mesh-arena-oj9-ui:$2
$OCI_BIN push $1/demo-mesh-arena-oj9-ball:$2
$OCI_BIN push $1/demo-mesh-arena-oj9-stadium:$2
$OCI_BIN push $1/demo-mesh-arena-oj9-ai:$2
