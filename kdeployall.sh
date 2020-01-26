#!/usr/bin/env bash

if [[ "$1" = "metrics" ]]; then
    kubectl apply -f <(istioctl kube-inject -f full-metrics.yml)
    exit
fi

if [[ "$1" = "tracing" ]]; then
    kubectl apply -f <(istioctl kube-inject -f full-tracing.yml)
    exit
fi

if [[ "$1" = "both" ]]; then
    kubectl apply -f <(istioctl kube-inject -f full-metrics-tracing.yml)
    exit
fi

if [[ "$1" = "naked" ]]; then
    kubectl apply -f <(istioctl kube-inject -f full.yml)
    exit
fi

echo "Please provide target modifier: 'tracing', 'metrics', 'both' or 'naked'"
