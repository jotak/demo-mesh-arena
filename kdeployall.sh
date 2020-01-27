#!/usr/bin/env bash

if [[ "$1" = "metrics" ]]; then
    cat full.yml | ./with_metrics.sh | istioctl kube-inject -f - | kubectl apply -f -
    exit
fi

if [[ "$1" = "tracing" ]]; then
    cat full.yml | ./with_tracing.sh | istioctl kube-inject -f - | kubectl apply -f -
    exit
fi

if [[ "$1" = "both" ]]; then
    cat full.yml | ./with_metrics.sh | ./with_tracing.sh | istioctl kube-inject -f - | kubectl apply -f -
    exit
fi

if [[ "$1" = "naked" ]]; then
    cat full.yml | istioctl kube-inject -f - | kubectl apply -f -
    exit
fi

echo "Please provide target modifier: 'tracing', 'metrics', 'both' or 'naked'"
