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

# Open it in a browser.

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
oc apply -f <(istioctl kube-inject -f ./Deployment-5-goats.yml)

# Start game
```

### Better players

Those goats are really bad players. Let's deploy another version to rise the level: Messi.

```bash
cd ../ai-visitors
oc apply -f <(istioctl kube-inject -f ./Deployment-Messi.yml)
```

### Another stadium

```bash
cd ../stadium
oc apply -f <(istioctl kube-inject -f ./Deployment-Smaller.yml)
```

But... we now have two stadiums. Both produce data, sent to UI. Both are queried by players to know where they are.
So we must introduce routing rules. (TBC)

## Run from IDE

- Run UI's main
- Open browser to localhost:8080
- Run Ball's main
- Run Stadium's main
- Start game: ```curl http://localhost:8082/start```
- Run Visitors and Locals AIs
