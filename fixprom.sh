#!/usr/bin/env bash

kubectl apply -f ./prom-cm.yaml -n istio-system
kubectl delete pod -l app=prometheus -n istio-system
