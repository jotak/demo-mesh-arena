#!/usr/bin/env bash

if [[ "$#" -lt 1 || "$1" = "--help" ]]; then
	echo "Syntax: gentpl.sh <service name> <other options>"
	echo ""
	exit
fi

FULL_NAME="$1"

# Split on first '-': https://www.linuxjournal.com/article/8919
# E.g. for FULL_NAME=ball-j11oj9 => BASE_NAME=ball, VARIANT=j11oj9
BASE_NAME="${1%-*}"
VARIANT="${1##*-}"

PULL_POLICY="Never"
DOMAIN=""
USER="jotak"
TAG="dev"
NAMESPACE="default"
TPL="base"
METRICS_ENABLED="0"
TRACING_ENABLED="0"
LAST_ARG=""

for arg in "$@"
do
    if [[ "$arg" = "--metrics" ]]; then
        METRICS_ENABLED="1"
    elif [[ "$arg" = "--tracing" ]]; then
        TRACING_ENABLED="1"
    elif [[ "$LAST_ARG" = "-tpl" ]]; then
        TPL="$arg"
        LAST_ARG=""
    elif [[ "$LAST_ARG" = "-pp" ]]; then
        PULL_POLICY="$arg"
        LAST_ARG=""
    elif [[ "$LAST_ARG" = "-d" ]]; then
        DOMAIN="$arg/"
        LAST_ARG=""
    elif [[ "$LAST_ARG" = "-t" ]]; then
        TAG="$arg"
        LAST_ARG=""
    elif [[ "$LAST_ARG" = "-u" ]]; then
        USER="$arg"
        LAST_ARG=""
    elif [[ "$LAST_ARG" = "-n" ]]; then
        NAMESPACE="$arg"
        LAST_ARG=""
    else
        LAST_ARG="$arg"
    fi
done

IMAGE="${DOMAIN}${USER}/mesharena-$FULL_NAME:$TAG"

cat ./k8s/$BASE_NAME-$TPL.yml \
    | yq w - metadata.labels.version $VARIANT \
    | yq w - metadata.name $FULL_NAME \
    | yq w - spec.selector.matchLabels.version $VARIANT \
    | yq w - spec.template.metadata.labels.version $VARIANT \
    | yq w - spec.template.spec.containers[0].imagePullPolicy $PULL_POLICY \
    | yq w - spec.template.spec.containers[0].image $IMAGE \
    | yq w - spec.template.spec.containers[0].name $FULL_NAME \
    | yq w - --tag '!!str' "spec.template.spec.containers[0].env.(name==METRICS_ENABLED).value" $METRICS_ENABLED \
    | yq w - --tag '!!str' "spec.template.spec.containers[0].env.(name==TRACING_ENABLED).value" $TRACING_ENABLED \
    | yq w - "spec.template.spec.containers[0].env.(name==JAEGER_SERVICE_NAME).value" $BASE_NAME.$NAMESPACE
