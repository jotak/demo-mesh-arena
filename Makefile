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

LATEST = 1.2.4

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

bold := $(shell tput bold)
smul := $(shell tput smul)
sgr0 := $(shell tput sgr0)

man: help

help:
	@echo "$(bold)Mesh Arena$(sgr0)"
	@echo "⚽ ⚽ ⚽"
	@echo ""
	@echo "Note that you may need a couple of tools depending of which target you run: kubectl, yq, npm, mvn, docker/podman"
	@echo ""
	@echo "$(smul)Main options$(sgr0):"
	@echo "- $(bold)TAG$(sgr0):          Image tag to be used (current: $(TAG))"
	@echo "- $(bold)OCI_USER$(sgr0):     User to use for image tagging / pulling (current: $(OCI_USER))"
	@echo "- $(bold)REMOTE$(sgr0):       When true, options are pre-defined to use quay.io; when false it would default to Minikube (current: $(REMOTE))"
	@echo "- $(bold)NAMESPACE$(sgr0):    Namespace where to deploy everything (current: $(NAMESPACE))"
	@echo "- $(bold)GENTPL_OPTS$(sgr0):  Deployment options:"
	@echo "                '--metrics' will enable runtime metrics"
	@echo "                '--tracing' will add application-defined traces"
	@echo "                '--kafka' will use Kafka, instead of HTTP endpoint, for display events"
	@echo ""
	@echo "$(smul)Main targets$(sgr0):"
	@echo "- $(bold)prepare$(sgr0):          Installs frontend dependencies (to run once, before building)"
	@echo "- $(bold)build$(sgr0):            Builds sources (maven)"
	@echo "- $(bold)images$(sgr0):           Builds OCI images, eventually prepare them for Minikube (pushing to local registry)"
	@echo "- $(bold)push-minikube$(sgr0):    Push built images to Minikube and tag for it"
	@echo "- $(bold)push$(sgr0):             Push to registry (local or remote)"
	@echo "- $(bold)deploy$(sgr0):           Deploy to Kubernetes"
	@echo "- $(bold)dry-deploy$(sgr0):       Simulate a full deployment, but just print out the resulting YAML"
	@echo "- $(bold)kill$(sgr0):             Kill (restart) all pods"
	@echo "- $(bold)expose$(sgr0):           Expose UI to http://localhost:8080/"
	@echo "- $(bold)undeploy$(sgr0):         Remove everything deployed"
	@echo "- $(bold)deploy-tracing$(sgr0):   Shorthand for 'make GENTPL_OPTS=--tracing deploy'"
	@echo "- $(bold)deploy-metrics$(sgr0):   Shorthand for 'make GENTPL_OPTS=--metrics deploy'"
	@echo "- $(bold)deploy-kafka$(sgr0):     Shorthand for 'make GENTPL_OPTS=--kafka deploy'"
	@echo "- $(bold)deploy-km$(sgr0):        Shorthand for 'make GENTPL_OPTS=--kafka --metrics deploy'"
	@echo "- $(bold)deploy-tm$(sgr0):        Shorthand for 'make GENTPL_OPTS=--tracing --metrics deploy'"
	@echo "- $(bold)deploy-kt$(sgr0):        Shorthand for 'make GENTPL_OPTS=--kafka --tracing deploy'"
	@echo "- $(bold)deploy-ktm$(sgr0):       Shorthand for 'make GENTPL_OPTS=--kafka --tracing --metrics deploy'"
	@echo "- $(bold)deploy-latest$(sgr0):    Deploys by pulling last known images from quay.io, with runtime metrics enabled"
	@echo ""
	@echo "$(smul)Scenarios$(sgr0):"
	@echo "- $(bold)scen-burst-current$(sgr0):    'Burst' the current deployed ball(s): will then randomly return some errors, sometimes, on HTTP calls"
	@echo "- $(bold)scen-unburst-current$(sgr0):  Make current ball(s) clean again"
	@echo "- $(bold)scen-add-ball$(sgr0):         Add a second ball"
	@echo "- $(bold)scen-75-25$(sgr0):            Set 75% of calls redirected to a ball, 25% to the other"
	@echo "- $(bold)scen-add-players$(sgr0):      Add two new players to the field"
	@echo "- $(bold)scen-add-burst$(sgr0):        Add a 'burst' second ball"
	@echo "- $(bold)scen-mirroring$(sgr0):        Set up traffic mirroring on balls"
	@echo "- $(bold)scen-outlier$(sgr0):          Set up outlier (error-based circuit breaking) on balls"
	@echo ""
	@echo "$(smul)Special targets$(sgr0):"
	@echo "- $(bold)kafka-se$(sgr0):                 Deploy a kafka (strimzi) cluster external to the mesh; to use with --kafka deploy option"
	@echo "- $(bold)kafka-meshed$(sgr0):             Deploy a kafka (strimzi) cluster as part of the mesh; to use with --kafka deploy option"
	@echo "- $(bold)gen-quickstart$(sgr0):           Generate the quickstart templates with latest tag"
	@echo "- $(bold)expose-jaeger-collector$(sgr0):  In case jaeger-collector isn't exosed by default in Istio, run this target if you use the --tracing deploy option"
	@echo ""
	@echo "$(smul)Examples$(sgr0):"
	@echo ""
	@echo "$(bold)make deploy-latest$(sgr0)"
	@echo "  Quick start, using latest images. Runtime metrics are enabled."
	@echo ""
	@echo "$(bold)NAMESPACE=mesh-arena TAG=$(LATEST) REMOTE=true GENTPL_OPTS="--tracing --metrics" make deploy$(sgr0)"
	@echo "  Deploy images from quay.io, tagged $(LATEST), to namespace 'mesh-arena', with application traces & metrics enabled"
	@echo ""
	@echo "$(bold)make prepare build images deploy$(sgr0)"
	@echo "$(bold)make build images deploy$(sgr0)"
	@echo "$(bold)make build images deploy kill$(sgr0)"
	@echo "  Build everything, deploy to Minikube. Use with 'prepare' for the first time you build. Use with 'kill' if you need to redeploy with code change only."
	@echo ""
	@echo "$(bold)make deploy-tracing$(sgr0)"
	@echo "$(bold)make deploy-metrics$(sgr0)"
	@echo "$(bold)make deploy-tm$(sgr0)"
	@echo "  Deploy with app metrics / app traces / both"
	@echo ""
	@echo "$(bold)OCI_USER=$(OCI_USER) TAG=$(LATEST) REMOTE=true make build images push gen-quickstart$(sgr0)"
	@echo "  Build everything, push to quay.io and generate quickstart templates"
	@echo ""
	@echo "$(bold)make kafka-se$(sgr0)"
	@echo "# wait until all is ready"
	@echo "$(bold)kubectl get pods -n kafka -w$(sgr0)"
	@echo "$(bold)make deploy-kafka$(sgr0)"
	@echo "  Deploy with Kafka (strimzi)"
	@echo ""
	@echo "Enjoy! ⚽"

.ensure-yq:
	@command -v yq >/dev/null 2>&1 || { echo >&2 "yq is required. Grab it on https://github.com/mikefarah/yq"; exit 1; }

prepare:
	@echo "⚽ Installing frontend dependencies..."
	cd ui/src/main/resources/webroot && npm install

build:
	@echo "⚽ Building services..."
	mvn clean package dependency:copy-dependencies

ifeq ($(TAG_MINIKUBE),true)
images: images-build push-minikube
else
images: images-build
endif

images-build:
	@echo "⚽ Building images..."
	for svc in ${TO_BUILD} ; do \
		echo "Building $$svc:" ; \
		${OCI_BIN_SHORT} build -t ${OCI_DOMAIN}/${OCI_USER}/mesharena-$$svc:${TAG} -f ./k8s/$$svc.dockerfile . ; \
	done

push-minikube:
	@echo "⚽ Tagging for Minikube..."
	for svc in ${TO_BUILD} ; do \
		${OCI_BIN_SHORT} push ${PUSH_OPTS} ${OCI_DOMAIN}/${OCI_USER}/mesharena-$$svc:${TAG} ; \
		${OCI_BIN_SHORT} tag ${OCI_DOMAIN}/${OCI_USER}/mesharena-$$svc:${TAG} ${OCI_DOMAIN_IN_CLUSTER}/${OCI_USER}/mesharena-$$svc:${TAG} ; \
	done

push:
	@echo "⚽ Pushing images..."
	for svc in ${TO_BUILD} ; do \
		${OCI_BIN_SHORT} push ${PUSH_OPTS} ${OCI_DOMAIN}/${OCI_USER}/mesharena-$$svc:${TAG} ; \
	done

deploy: .ensure-yq
	kubectl label namespace ${NAMESPACE} istio-injection=enabled 2> /dev/null ; \
	for svc in ${TO_DEPLOY} ; do \
		./gentpl.sh $$svc -v ${GENTPL_VERSION} -pp ${PULL_POLICY} -d "${OCI_DOMAIN_IN_CLUSTER}" -u ${OCI_USER} -t ${TAG} -n ${NAMESPACE} ${GENTPL_OPTS} | kubectl -n ${NAMESPACE} apply -f - ; \
	done

dry-deploy: .ensure-yq
	for svc in ${TO_DEPLOY} ; do \
		./gentpl.sh $$svc -v ${GENTPL_VERSION} -pp ${PULL_POLICY} -d "${OCI_DOMAIN_IN_CLUSTER}" -u ${OCI_USER} -t ${TAG} -n ${NAMESPACE} ${GENTPL_OPTS} ; \
	done

kill:
	kubectl -n ${NAMESPACE} delete pods -l "project=mesh-arena"

expose:
	@echo "⚽ URL: http://localhost:8080/"
	kubectl wait pod -l app=ui --for=condition=Ready --timeout=300s -n ${NAMESPACE} ; \
	kubectl -n ${NAMESPACE} port-forward svc/ui 8080:8080

undeploy:
	kubectl -n ${NAMESPACE} delete all -l "project=mesh-arena" ; \
	kubectl -n ${NAMESPACE} delete destinationrule -l "project=mesh-arena" ; \
	kubectl -n ${NAMESPACE} delete virtualservice -l "project=mesh-arena" ; \
	kubectl -n ${NAMESPACE} delete gateway -l "project=mesh-arena" ; \
	kubectl -n ${NAMESPACE} delete envoyfilter -l "project=mesh-arena"

deploy-tracing: GENTPL_OPTS=--tracing
deploy-tracing: deploy

deploy-metrics: GENTPL_OPTS=--metrics
deploy-metrics: deploy

deploy-kafka: GENTPL_OPTS=--kafka
deploy-kafka: deploy

deploy-km: GENTPL_OPTS=--metrics --kafka
deploy-km: deploy

deploy-tm: GENTPL_OPTS=--tracing --metrics
deploy-tm: deploy

deploy-kt: GENTPL_OPTS=--tracing --kafka
deploy-kt: deploy

deploy-ktm: GENTPL_OPTS=--tracing --metrics --kafka
deploy-ktm: deploy

deploy-latest: TAG=${LATEST}
deploy-latest: OCI_DOMAIN_IN_CLUSTER=quay.io
deploy-latest: PULL_POLICY=IfNotPresent
deploy-latest: TAG_MINIKUBE=false
deploy-latest: GENTPL_OPTS=--metrics
deploy-latest: deploy

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

expose-jaeger-collector:
	kubectl apply -f ./istio/jaeger-collector.yml

kafka-se:
	@kubectl create namespace kafka 2> /dev/null ; \
	kubectl get namespace -l istio-injection=enabled | grep kafka ; \
	if [ $$? -eq 0 ]; then \
		echo "Istio injection is enabled, removing it and restarting pods" ; \
		kubectl label namespace kafka istio-injection- ; \
		kubectl delete pods --all -n kafka ; \
	fi ; \
	kubectl apply -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka ; \
	kubectl apply -f ./k8s/strimzi.yml -n kafka ; \
	kubectl apply -f ./istio/kafka-se.yml

kafka-meshed:
	@kubectl create namespace kafka 2> /dev/null ; \
	kubectl get namespace -l istio-injection=enabled | grep kafka ; \
	if [ $$? -eq 1 ]; then \
		echo "Istio injection is disabled, adding it and restarting pods" ; \
		kubectl label namespace kafka istio-injection=enabled ; \
		kubectl delete -f ./istio/kafka-se.yml ; \
		kubectl delete pods --all -n kafka ; \
	fi ; \
	kubectl apply -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka ; \
	kubectl apply -f ./k8s/strimzi.yml -n kafka

gen-quickstart:
	@echo "⚽ Generating quickstart templates..."
	rm quickstart-naked.yml quickstart-metrics.yml quickstart-tracing.yml quickstart-both.yml quickstart-kafka.yml ; \
	for svc in ${TO_DEPLOY} ; do \
		./gentpl.sh $$svc -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n default >> quickstart-naked.yml ; \
		./gentpl.sh $$svc -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n default --tracing >> quickstart-tracing.yml ; \
		./gentpl.sh $$svc -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n default --metrics >> quickstart-metrics.yml ; \
		./gentpl.sh $$svc -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n default --tracing --metrics >> quickstart-both.yml ; \
		./gentpl.sh $$svc -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n default --tracing --kafka >> quickstart-kafka.yml ; \
	done ; \
	cat ./istio/kafka-se.yml >> quickstart-kafka.yml

reload-minikube: push-minikube kill
