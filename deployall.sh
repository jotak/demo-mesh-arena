#!/usr/bin/env bash

if [[ "$1" != "metrics" && "$1" != "tracing" && "$1" != "both" && "$1" != "naked" ]]; then
    echo "Please provide target modifier: 'tracing', 'metrics', 'both' or 'naked'"
    exit
fi

oc new-project mesh-arena
oc project mesh-arena
oc adm policy add-scc-to-user privileged -z default

if [[ "$1" = "metrics" ]]; then
    cat full.yml | ./with_metrics.sh | istioctl kube-inject -f - | oc apply -f -
fi
if [[ "$1" = "tracing" ]]; then
    cat full.yml | ./with_tracing.sh | istioctl kube-inject -f - | oc apply -f -
fi
if [[ "$1" = "both" ]]; then
    cat full.yml | ./with_metrics.sh | ./with_tracing.sh | istioctl kube-inject -f - | oc apply -f -
fi
if [[ "$1" = "naked" ]]; then
    cat full.yml | istioctl kube-inject -f - | oc apply -f -
fi

oc expose service ui
