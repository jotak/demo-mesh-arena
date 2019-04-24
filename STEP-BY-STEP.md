# Mesh Arena

This is a step-by-step guide to run the demo.

## Slides

This demo was presented at [DevopsDday](http://2018.devops-dday.com/) in the Velodrome, Marseilles' famous stadium.
[Here are the slides](https://docs.google.com/presentation/d/1PzRD3BquEI3Al6y2_vSrZqUY0AlJF54_uuWYhr81t5g), in French. Or [in English](https://docs.google.com/presentation/d/1WZDmIcfzKC9GMqz8Cvcb0_mJK_hIH-JxEDROZLnEnng).

## Pre-requisite

- Kubernetes or OpenShift cluster running (ex: minikube 0.27+ / minishift)
- Istio with Kiali installed

## Get the yml files locally

- Clone this repo locally, `cd` to it.

```bash
git clone git@github.com:jotak/demo-mesh-arena.git
cd demo-mesh-arena
```

For OpenShift users, you may have to grant extended permissions for Istio, logged as admin:
```bash
oc new-project mesh-arena
oc adm policy add-scc-to-user privileged -z default
```

## Jaeger

Tracing data generated from microservices and Istio can be viewed in Jaeger by port-forwarding
`jaeger-query` service.

```bash
kubectl port-forward svc/jaeger-query 16686:16686 -n istio-system
```

AI service generates trace named `new_game` for each game. This way we are able to trace player's
movement on the stadium.

The other interesting trace is from `ui` service called `on-start` it captures all initialization
steps performed at the beginning of the game.

## Deploy microservice UI

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

## Deploying burst ball (500 errors) unused
```bash
istioctl create -f ./services/ball/virtualservice-all-to-v1.yml
kubectl apply -f <(istioctl kube-inject -f ./services/ball/Deployment-burst.yml)
```

## Burst ball with shadowing
```bash
istioctl replace -f ./services/ball/virtualservice-mirrored.yml
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

## To clean up everything

```bash
kubectl delete deployments -l project=mesh-arena
kubectl delete svc -l project=mesh-arena
kubectl delete virtualservices -l project=mesh-arena
kubectl delete destinationrules -l project=mesh-arena
```
