# demo-mesh-arena

## Pre-requisite

- Kubernetes or OpenShift cluster running (ex: minikube 0.27+ / minishift)
- Istio 0.8+ installed 
- For visualization support, Kiali installed

## Prepare

Clone the project and set the env **DEMO_MESH_ARENA_HOME**
```bash
cd ~/software
git clone https://github.com/jotak/demo-mesh-arena.git
export DEMO_MESH_ARENA_HOME=/Users/nicolas/software/demo-mesh-arena
```

## Build & Run for Kubernetes

Provision a local K8S cluster with **minikube**

```bash
minikube start \
    --memory=4096 --cpus=4  --extra-config=controller-manager.cluster-signing-cert-file="/var/lib/localkube/certs/ca.crt" \
    --extra-config=controller-manager.cluster-signing-key-file="/var/lib/localkube/certs/ca.key" \
    --extra-config=apiserver.admission- control="NamespaceLifecycle,LimitRanger,ServiceAccount,PersistentVolumeLabel,DefaultStorageClass,DefaultTolerationSeconds,MutatingAdmissionWebhook,ValidatingAdmissionWebhook,ResourceQuota" \
    --kubernetes-version=v1.10.0
```

Use the Docker env inside minikube through
```bash
eval $(minikube docker-env)

## Install istio without enabling mutual TLS authentication between sidecars
kubectl apply -f $ISTIO_HOME/install/kubernetes/istio-demo.yaml

## Install Node Modules
cd $DEMO_MESH_ARENA_HOME/services/ui/src/main/resources/webroot && npm install

## Build 
cd $DEMO_MESH_ARENA_HOME
mvn package dependency:copy-dependencies

docker build -t jotak/demo-mesh-arena-ui ./services/ui
docker build -t jotak/demo-mesh-arena-ball ./services/ball
docker build -t jotak/demo-mesh-arena-stadium ./services/stadium
docker build -t jotak/demo-mesh-arena-ai ./services/ai
```

Deploy UI, Ball and Stadium

```bash
kubectl apply -f <(istioctl kube-inject -f ./services/ui/Deployment.yml)
kubectl apply -f ./services/ui/Service.yml

kubectl apply -f <(istioctl kube-inject -f ./services/ball/Deployment.yml)
kubectl apply -f ./services/ball/Service.yml

kubectl apply -f <(istioctl kube-inject -f ./services/stadium/Deployment-Smaller.yml)
kubectl apply -f ./services/stadium/Service.yml
```

Verify all is good with in default namespace
```bash
watch kubectl get pods
```

If ok, try to open a browser
```bash
# DEPLOY THE GATEWAY
istioctl create -f mesh-arena-gateway.yaml
```

And try now to access with browser:
```bash
export INGRESS_PORT=$(kubectl -n istio-system get service istio-ingressgateway -o jsonpath='{.spec.ports[?(@.name=="http")].nodePort}')
export INGRESS_HOST=$(minikube ip)
export GATEWAY_URL=$INGRESS_HOST:$INGRESS_PORT
open http://$GATEWAY_URL
```

So you can now deploy the goats
```bash
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-2-locals.yml)
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-2-visitors.yml)
```

To update goats with real players. Sorry no Chris Waddle in this release.
```bash
kubectl delete -f <(istioctl kube-inject -f ./services/ai/Deployment-2-locals.yml)
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Mbappe.yml)
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Messi.yml)
```

And for more fun waiting for battle royale mode
```bash
kubectl apply -f <(istioctl kube-inject -f ./services/stadium/Deployment-Bigger.yml
istioctl create -f ./services/stadium/stadium-routes.yml
istioctl create -f ./services/stadium/route-all-smaller.yml
istioctl replace -f ./services/stadium/route-all-bigger.yml
```

## Build & Run for OpenShift

```bash

## Install Node Modules
cd $DEMO_MESH_ARENA_HOME/services/ui/src/main/resources/webroot && npm install

# Build all
cd $DEMO_MESH_ARENA_HOME
mvn package dependency:copy-dependencies

docker build -t jotak/demo-mesh-arena-ui ./services/ui
docker build -t jotak/demo-mesh-arena-ball ./services/ball
docker build -t jotak/demo-mesh-arena-stadium ./services/stadium
docker build -t jotak/demo-mesh-arena-ai ./services/ai

oc login -u system:admin

oc new-project mesh-arena
oc adm policy add-scc-to-user privileged -z default

oc apply -f <(istioctl kube-inject -f ./services/ui/Deployment.yml)
oc create -f ./services/ui/Service.yml
oc expose service ui
oc apply -f <(istioctl kube-inject -f ./services/ball/Deployment.yml)
oc create -f ./services/ball/Service.yml
oc apply -f <(istioctl kube-inject -f ./services/stadium/Deployment-Smaller.yml)
oc create -f ./services/stadium/Service.yml
oc apply -f <(istioctl kube-inject -f ./services/ai/Deployment-2-locals.yml)
oc create -f ./services/ai/Service-locals.yml
oc apply -f <(istioctl kube-inject -f ./services/ai/Deployment-2-visitors.yml)
oc create -f ./services/ai/Service-visitors.yml

```


### Better players

Those goats are really bad players. Let's deploy another version to rise the level: Messi.

```bash
oc apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Messi.yml)
oc apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Mbappe.yml)
```

### Another stadium

```bash
oc apply -f <(istioctl kube-inject -f ./services/stadium/Deployment-Bigger.yml)

istioctl create -f ./services/stadium/stadium-routes.yml
istioctl create -f ./services/stadium/route-all-smaller.yml

istioctl replace -f ./services/stadium/route-all-bigger.yml
```

### Another ball

```bash
oc apply -f <(istioctl kube-inject -f ./services/ball/Deployment-v2.yml)

istioctl create -f ./services/ball/destrule.yml
istioctl create -f ./services/ball/virtualservice.yml
```

## Run from IDE

- Run UI's main
- Open browser to localhost:8080
- Run Ball's main
- Run Stadium's main
- Start game: ```curl http://localhost:8082/start```
- Run Visitors and Locals AIs
