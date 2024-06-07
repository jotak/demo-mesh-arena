#!/usr/bin/env bash

if [[ "$#" -lt 1 || "$1" = "--help" ]]; then
	echo "Syntax: gentpl.sh <service name> <other options>"
	echo ""
	exit
fi

FULL_NAME="$1"

# Split on first '-': https://www.linuxjournal.com/article/8919
# E.g. for FULL_NAME=ball-openj9 => BASE_NAME=ball, VM=openj9
NAME="${1%-*}"
# VM="${1##*-}"
VERSION="${1##*-}"
VM="temurin-17"

PULL_POLICY="Never"
DOMAIN=""
USER="jotak"
TAG="dev"
NAMESPACE="default"
VERSION_OVERRIDE=""
METRICS_ENABLED="0"
TRACING_ENABLED="0"
USER_9000="0"
KAFKA_ADDRESS=""
INTERACTIVE_MODE="0"
LAST_ARG=""

for arg in "$@"
do
    if [[ "$arg" = "--metrics" ]]; then
        METRICS_ENABLED="1"
    elif [[ "$arg" = "--tracing" ]]; then
        TRACING_ENABLED="1"
    elif [[ "$arg" = "--user9000" ]]; then
        USER_9000="1"
    elif [[ "$arg" = "--kafka" ]]; then
        KAFKA_ADDRESS="messaging-kafka-bootstrap.kafka:9092"
    elif [[ "$arg" = "--interactive" ]]; then
        INTERACTIVE_MODE="1"
    elif [[ "$LAST_ARG" = "-v" ]]; then
        VERSION="$arg"
        LAST_ARG=""
    elif [[ "$LAST_ARG" = "-vo" ]]; then
        VERSION_OVERRIDE="$arg"
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

if [[ "$VERSION_OVERRIDE" = "" ]]; then
  VERSION_OVERRIDE="$VERSION"
fi

IMAGE="${DOMAIN}${USER}/mesharena-$NAME:$TAG"

if [[ -f "./k8s/$NAME-$VERSION.yml" ]] ; then
  cat "./k8s/$NAME-$VERSION.yml" \
      | ./yq w - metadata.labels.version $VERSION_OVERRIDE \
      | ./yq w - metadata.labels.vm $VM \
      | ./yq w - metadata.name "$NAME-$VERSION_OVERRIDE" \
      | ./yq w - spec.selector.matchLabels.version $VERSION_OVERRIDE \
      | ./yq w - spec.selector.matchLabels.vm $VM \
      | ./yq w - spec.template.metadata.labels.version $VERSION_OVERRIDE \
      | ./yq w - spec.template.metadata.labels.vm $VM \
      | ./yq w - spec.template.spec.containers[0].imagePullPolicy $PULL_POLICY \
      | ./yq w - spec.template.spec.containers[0].image $IMAGE \
      | ./yq w - spec.template.spec.containers[0].name $FULL_NAME \
      | ( [ "$METRICS_ENABLED" = "1" ] && ./yq w - --tag '!!str' "spec.template.metadata.annotations.(prometheus.io/scrape)" "true" || cat ) \
      | ( [ "$USER_9000" = "1" ] && ./yq w - spec.template.spec.containers[0].securityContext.runAsUser 9000 || cat ) \
      | ./yq w - --tag '!!str' "spec.template.spec.containers[0].env.(name==METRICS_ENABLED).value" $METRICS_ENABLED \
      | ./yq w - --tag '!!str' "spec.template.spec.containers[0].env.(name==TRACING_ENABLED).value" $TRACING_ENABLED \
      | ./yq w - --tag '!!str' "spec.template.spec.containers[0].env.(name==INTERACTIVE_MODE).value" $INTERACTIVE_MODE \
      | ( [ "$KAFKA_ADDRESS" != "" ] && ./yq w - --tag '!!str' "spec.template.spec.containers[0].env.(name==KAFKA_ADDRESS).value" $KAFKA_ADDRESS || cat ) \
      | ./yq w - "spec.template.spec.containers[0].env.(name==JAEGER_SERVICE_NAME).value" $NAME.$NAMESPACE
  echo "---"
fi
