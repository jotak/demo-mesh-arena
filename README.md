# demo-mesh-arena

## Pre-requisite

- Kubernetes or OpenShift cluster running (ex: minikube / minishift)
- Istio installed
- For visualization support, Kiali installed

## Build & Run for Kubernetes

(to be completed)

## Build & Run for OpenShift

```bash
# Build all
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
oc apply -f <(istioctl kube-inject -f ./services/ai/Deployment-2-visitors.yml)

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

## Run from IDE

- Run UI's main
- Open browser to localhost:8080
- Run Ball's main
- Run Stadium's main
- Start game: ```curl http://localhost:8082/start```
- Run Visitors and Locals AIs
