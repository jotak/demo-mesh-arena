#!/usr/bin/env bash

kubectl label namespace default istio-injection=enabled

if [[ "$1" = "metrics" ]]; then
    cat full.yml | ./with_metrics.sh | kubectl apply -n default -f -
    exit
fi

if [[ "$1" = "tracing" ]]; then
    cat full.yml | ./with_tracing.sh | kubectl apply -n default -f -
    exit
fi

if [[ "$1" = "both" ]]; then
    cat full.yml | ./with_metrics.sh | ./with_tracing.sh | kubectl -n default apply -f -
    exit
fi

if [[ "$1" = "naked" ]]; then
    cat full.yml | kubectl -n default apply -f -
    exit
fi

echo "Please provide target modifier: 'tracing', 'metrics', 'both' or 'naked'"
