# OCI CLI (docker or podman)
OCI_BIN ?= $(shell which podman 2>/dev/null || which docker 2>/dev/null)
OCI_BIN_SHORT = $(shell if [[ ${OCI_BIN} == *"podman" ]]; then echo "podman"; else echo "docker"; fi)
# Tag for docker images
TAG ?= dev
OCI_USER ?= jotak
# Set REMOTE=true if you want to use remote (quay.io) images
REMOTE ?= false
NAMESPACE ?= default
# List of images
TO_BUILD ?= ai-hotspot ai-openj9 ball-hotspot ball-openj9 stadium-hotspot ui-hotspot
TO_DEPLOY ?= ai-hotspot ai-openj9 ball-hotspot stadium-hotspot ui-hotspot
GENTPL_VERSION ?= base

ifeq ($(REMOTE),true)
OCI_DOMAIN ?= quay.io
OCI_DOMAIN_IN_CLUSTER ?= quay.io
PULL_POLICY ?= "IfNotPresent"
else ifeq ($(OCI_BIN_SHORT),podman)
OCI_DOMAIN ?= "$(shell minikube ip):5000"
OCI_DOMAIN_IN_CLUSTER ?= localhost:5000
PULL_POLICY ?= "Always"
TAG_MINIKUBE = true
else
OCI_DOMAIN ?= ""
OCI_DOMAIN_IN_CLUSTER ?= ""
PULL_POLICY ?= "Never"
endif

ifeq ($(OCI_BIN_SHORT),podman)
PUSH_OPTS ?= --tls-verify=false
endif

.ensure-yq:
	@command -v yq >/dev/null 2>&1 || { echo >&2 "yq is required. Grab it on https://github.com/mikefarah/yq"; exit 1; }

prepare:
	@echo "Installing frontend dependencies..."
	cd ui/src/main/resources/webroot && npm install

build:
	@echo "Building services..."
	mvn clean package dependency:copy-dependencies

ifeq ($(TAG_MINIKUBE),true)
images: images-push tag-minikube
else
images: images-push
endif

images-push:
	@echo "Building images..."
	for svc in ${TO_BUILD} ; do \
		echo "Building $$svc:" ; \
		${OCI_BIN_SHORT} build -t ${OCI_DOMAIN}/${OCI_USER}/mesharena-$$svc:${TAG} -f ./k8s/$$svc.dockerfile . ; \
		${OCI_BIN_SHORT} push ${PUSH_OPTS} ${OCI_DOMAIN}/${OCI_USER}/mesharena-$$svc:${TAG} ; \
	done

tag-minikube:
	@echo "Tagging for Minikube..."
	for svc in ${TO_BUILD} ; do \
		${OCI_BIN_SHORT} tag ${OCI_DOMAIN}/${OCI_USER}/mesharena-$$svc:${TAG} ${OCI_DOMAIN_IN_CLUSTER}/${OCI_USER}/mesharena-$$svc:${TAG} ; \
	done

deploy: .ensure-yq
	kubectl label namespace ${NAMESPACE} istio-injection=enabled ; \
	for svc in ${TO_DEPLOY} ; do \
		./gentpl.sh $$svc -v ${GENTPL_VERSION} -pp ${PULL_POLICY} -d "${OCI_DOMAIN_IN_CLUSTER}" -u ${OCI_USER} -t ${TAG} -n ${NAMESPACE} ${GENTPL_OPTS} | kubectl -n ${NAMESPACE} apply -f - ; \
	done

dry-deploy: .ensure-yq
	for svc in ${TO_DEPLOY} ; do \
		./gentpl.sh $$svc -v ${GENTPL_VERSION} -pp ${PULL_POLICY} -d "${OCI_DOMAIN_IN_CLUSTER}" -u ${OCI_USER} -t ${TAG} -n ${NAMESPACE} ${GENTPL_OPTS} ; \
	done

deploy-tracing: GENTPL_OPTS=--tracing
deploy-tracing: deploy

deploy-metrics: GENTPL_OPTS=--metrics
deploy-metrics: deploy

deploy-both: GENTPL_OPTS=--tracing --metrics
deploy-both: deploy

scen-burst-current:
	kubectl -n ${NAMESPACE} set env deployment -l app=ball PCT_ERRORS=20

scen-unburst-current:
	kubectl -n ${NAMESPACE} set env deployment -l app=ball PCT_ERRORS=0

scen-add-ball: TO_DEPLOY=ball-openj9
scen-add-ball: deploy

scen-75-25:
	kubectl apply -f ./istio/destrule.yml -n ${NAMESPACE} ; \
	kubectl apply -f ./istio/virtualservice-75-25.yml -n ${NAMESPACE}

scen-add-players: GENTPL_VERSION="mbappe messi"
scen-add-players: TO_DEPLOY=ai-hotspot ai-openj9
scen-add-players: deploy
	kubectl apply -f ./istio/destrule.yml -n ${NAMESPACE} ; \
	kubectl apply -f ./istio/virtualservice-by-label.yml -n ${NAMESPACE}

scen-add-burst: GENTPL_VERSION=burst
scen-add-burst: TO_DEPLOY=ball-openj9
scen-add-burst: deploy

scen-mirroring:
	kubectl apply -f ./istio/destrule.yml -n ${NAMESPACE} ; \
	kubectl apply -f ./istio/virtualservice-mirrored.yml -n ${NAMESPACE}

scen-outlier:
	kubectl delete -f ./istio/virtualservice-mirrored.yml -n ${NAMESPACE} ; \
    kubectl apply -f ./istio/destrule-outlier.yml -n ${NAMESPACE}

expose:
	@echo "URL: http://localhost:8080/"
	kubectl wait pod -l app=ui --for=condition=Ready --timeout=300s -n ${NAMESPACE} ; \
	kubectl -n ${NAMESPACE} port-forward svc/ui 8080:8080

undeploy:
	kubectl -n ${NAMESPACE} delete all -l "project=mesh-arena" ; \
	kubectl -n ${NAMESPACE} delete destinationrule -l "project=mesh-arena" ; \
	kubectl -n ${NAMESPACE} delete virtualservice -l "project=mesh-arena" ; \
	kubectl -n ${NAMESPACE} delete gateway -l "project=mesh-arena" ; \
	kubectl -n ${NAMESPACE} delete envoyfilter -l "project=mesh-arena"

kill:
	kubectl -n ${NAMESPACE} delete pods -l "project=mesh-arena"

deploy-latest: TAG=1.1.8
deploy-latest: OCI_DOMAIN_IN_CLUSTER=quay.io
deploy-latest: PULL_POLICY=IfNotPresent
deploy-latest: TAG_MINIKUBE=false
deploy-latest: GENTPL_OPTS=--metrics
deploy-latest: deploy

expose-jaeger-collector:
	kubectl apply -f ./istio/jaeger-collector.yml

kafka:
	kubectl create namespace kafka ; \
	kubectl apply -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka ; \
	kubectl apply -f ./k8s/strimzi.yaml -n kafka
