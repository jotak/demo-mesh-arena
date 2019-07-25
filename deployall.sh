#!/usr/bin/env bash

oc new-project mesh-arena
oc project mesh-arena
oc adm policy add-scc-to-user privileged -z default

oc apply -f <(istioctl kube-inject -f full-metrics.yml)

oc expose service ui
