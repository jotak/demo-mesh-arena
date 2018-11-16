# demo-mesh-arena

## Slides

This demo was presented at [DevopsDday](http://2018.devops-dday.com/) in the Velodrome, Marseilles' famous stadium.
[Here are the slides](https://docs.google.com/presentation/d/1PzRD3BquEI3Al6y2_vSrZqUY0AlJF54_uuWYhr81t5g), in French.

## Pre-requisite

- Kubernetes or OpenShift cluster running (ex: minikube 0.27+ / minishift)
- Istio 1.0+ installed

Ex: pickup [a release](https://github.com/istio/istio/releases/tag/1.0.3), unzip and from there apply istio-demo.yml to a running kube cluster:
```bash
kubectl apply -f install/kubernetes/istio-demo.yaml
# We'll also use istioctl
export PATH=$PATH:`pwd`/bin
```

- Clone this repo locally, `cd` to it.

```bash
git clone git@github.com:jotak/demo-mesh-arena.git
cd demo-mesh-arena
```

- Kiali installed

If you don't have it already installed, refer to [the official doc](https://www.kiali.io/gettingstarted/) or for a quicker start just run:

```bash
kubectl create -f kiali/kiali-configmap.yaml -n istio-system
kubectl create -f kiali/kiali-secrets.yaml -n istio-system
kubectl create -f kiali/kiali.yaml -n istio-system
```

As a general note for this demo, some docker images will have to be downloaded while we're deploying the stuff.
First time you run the demo, we don't expect things to come up immediately. You can run at anytime:

```bash
kubectl get pods
# or (for istio/kiali):
kubectl get pods -n istio-system
```

to see when deployed pods are ready.

Note, for OpenShift users, you may have to grant extended permissions:
```bash
oc adm policy add-scc-to-user privileged -z default
```

## Open Kiali

For simplicity we'll use kube's port-forward.
If you wish to share access to Kiali from other hosts, you would have to setup an ingress or an OpenShift route instead.

Note, for some reason, I found port-forward not reliable when applied quickly after setting up the service.
Maybe wait up to ~one minute before running the command.

In a new terminal:

```bash
kubectl port-forward svc/kiali 20001:20001 -n istio-system
```

Open http://localhost:20001 in a browser.

## Deploy micro-service UI

```bash
kubectl apply -f <(istioctl kube-inject -f ./services/ui/Deployment.yml)
kubectl create -f ./services/ui/Service.yml
kubectl apply -f mesh-arena-gateway.yaml 
```

## Open in browser

(Wait a little bit because port-forward?)

```bash
kubectl port-forward svc/istio-ingressgateway 8080:80 -n istio-system
```

Open http://localhost:8080 in a browser.

## Deploy stadium & ball
```bash
kubectl apply -f <(istioctl kube-inject -f ./services/stadium/Deployment-Smaller.yml)
kubectl create -f ./services/stadium/Service.yml
kubectl apply -f <(istioctl kube-inject -f ./services/ball/Deployment.yml)
kubectl create -f ./services/ball/Service.yml
```

## Deploy 2x2 players
```bash
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-2-locals.yml)
kubectl create -f ./services/ai/Service-locals.yml
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-2-visitors.yml)
kubectl create -f ./services/ai/Service-visitors.yml
```

## Second ball
```bash
kubectl apply -f <(istioctl kube-inject -f ./services/ball/Deployment-v2.yml)
````

## Apply weight on balls
```bash
istioctl create -f ./services/ball/destrule.yml
istioctl create -f ./services/ball/virtualservice-75-25.yml
```

## Messi / Mbappé
```bash
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Messi.yml)
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Mbappe.yml)
```

## Each his ball
```bash
istioctl replace -f ./services/ball/virtualservice-by-label.yml
```

## Reset
```bash
kubectl delete -f ./services/ai/Deployment-Messi.yml
kubectl delete -f ./services/ai/Deployment-Mbappe.yml
istioctl delete -f ./services/ball/virtualservice-by-label.yml
```

## Deploying burst ball (500 errors) with shadowing
```bash
istioctl create -f ./services/ball/virtualservice-mirrored.yml
kubectl apply -f <(istioctl kube-inject -f ./services/ball/Deployment-burst.yml)
```

## Remove shadowing, put circuit breaking
```bash
istioctl delete -f ./services/ball/virtualservice-mirrored.yml
istioctl replace -f ./services/ball/destrule-outlier.yml
````

## El Clasico, Caramba!

**D-I-S-C-L-A-I-M-E-R**
This is going to deploy 20 players on the field, it's quite CPU intensive, it is NOT recommended to run on a PC / single-node cluster, or your cluster may suffer.

```bash
kubectl delete -f ./services/ball/Deployment-v2.yml
kubectl delete -f ./services/ai/Deployment-2-locals.yml
kubectl delete -f ./services/ai/Deployment-2-visitors.yml
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-OM.yml)
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-PSG.yml)
```

## To clean up everything at any time (but Istio/Kiali)
```bash
kubectl delete deployments ai-locals-om
kubectl delete deployments ai-visitors-psg
kubectl delete deployments ai-locals-basic
kubectl delete deployments ai-visitors-basic
kubectl delete deployments ai-visitors-messi
kubectl delete deployments ai-locals-mbappe
kubectl delete deployments ball
kubectl delete deployments ball-v2
kubectl delete deployments stadium-small
kubectl delete deployments ui
kubectl delete svc ai-locals
kubectl delete svc ai-visitors
kubectl delete svc ball
kubectl delete svc stadium
kubectl delete svc ui
kubectl delete virtualservices mesh-arena
kubectl delete destinationrules ball-dr
```


## To run the microservices only from the IDE, without Kube / Istio / Kiali:

- Run UI's main
- Open browser to localhost:8080
- Run Ball's main
- Run Stadium's main
- Start game: ```curl http://localhost:8082/start```
- Run Visitors and Locals AIs
