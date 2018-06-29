# demo-mesh-arena

## Build & Run for OpenShift

```bash
oc new-project mesh-arena
oc adm policy add-scc-to-user privileged -z default

# Build all
mvn package dependency:copy-dependencies

# UI
cd services/ui
docker build -t jotak/demo-mesh-arena-ui .
oc apply -f <(istioctl kube-inject -f ./Deployment.yml)
oc create -f ./Service.yml
oc expose service ui

# Get the route URL
oc get route ui
# Open it in a browser. When it's ready, you will just see an empty page with title "Mesh Arena"

# Ball
cd ../ball
docker build -t jotak/demo-mesh-arena-ball .
oc apply -f <(istioctl kube-inject -f ./Deployment.yml)
oc create -f ./Service.yml

# Stadium
cd ../stadium
docker build -t jotak/demo-mesh-arena-stadium .
oc apply -f <(istioctl kube-inject -f ./Deployment.yml)
oc create -f ./Service.yml

# Locals
cd ../ai-locals
docker build -t jotak/demo-mesh-arena-ai-locals .
oc apply -f <(istioctl kube-inject -f ./Deployment.yml)

# Visitors
cd ../ai-visitors
docker build -t jotak/demo-mesh-arena-ai-visitors .
oc apply -f <(istioctl kube-inject -f ./Deployment.yml)

# Start game
oc expose service stadium --path=/start
# And navigate to that route
# or alternatively, from within stadium pod:
curl -H "Content-Type: application/json" http://localhost:8080/start
```

## Run from IDE

- Run UI's main
- Open browser to localhost:8080
- Run Ball's main
- Run Stadium's main
- Start game: ```curl http://localhost:8082/start```
- Run Visitors and Locals AIs
