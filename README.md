# demo-mesh-arena

## Slides

This demo was presented at [DevopsDday](http://2018.devops-dday.com/) in the Velodrome, Marseilles' famous stadium.
[Here are the slides](https://docs.google.com/presentation/d/1PzRD3BquEI3Al6y2_vSrZqUY0AlJF54_uuWYhr81t5g), in French. Or a similar [English version](https://docs.google.com/presentation/d/1WZDmIcfzKC9GMqz8Cvcb0_mJK_hIH-JxEDROZLnEnng).

## Step-by-step

For a step-by-step walk-through, [read this](./STEP-BY-STEP.md).

## Pre-requisite

- Kubernetes or OpenShift cluster running (ex: minikube 0.27+ / minishift)
- Istio with Kiali installed
- Repo cloned locally (actually, only YML files are necessary)

## OpenShift

For OpenShift users, you may have to grant extended permissions for Istio, logged as admin:

```bash
oc new-project mesh-arena
oc adm policy add-scc-to-user privileged -z default
```

## Deploy services

Without runtimes metrics & tracing:

```bash
oc apply -f <(istioctl kube-inject -f full.yml)
```

With runtimes metrics:

```bash
oc apply -f <(istioctl kube-inject -f full-metrics.yml)
```

With tracing:

```bash
oc apply -f <(istioctl kube-inject -f full-tracing.yml)
```

With everything:

```bash
oc apply -f <(istioctl kube-inject -f full-metrics-tracing.yml)
```

## Expose route

```bash
oc expose service ui
```

## Second ball
```bash
oc apply -f <(istioctl kube-inject -f ./services/ball/Deployment-v2.yml)
````

## Weighting
```bash
oc apply -f ./services/ball/destrule.yml
oc apply -f ./services/ball/virtualservice-75-25.yml
```

## Messi / Mbappé
```bash
oc apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Messi.yml)
oc apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Mbappe.yml)
```

## 2 games
```bash
oc apply -f ./services/ball/virtualservice-by-label.yml
```

## Reset
```bash
oc delete -f ./services/ai/Deployment-Messi.yml
oc delete -f ./services/ai/Deployment-Mbappe.yml
oc delete -f ./services/ball/virtualservice-by-label.yml
```

## Deploying burst ball (500 errors) unused
```bash
oc apply -f ./services/ball/virtualservice-all-to-v1.yml
oc apply -f <(istioctl kube-inject -f ./services/ball/Deployment-burst.yml)
```

## Burst ball with shadowing
```bash
oc apply -f ./services/ball/virtualservice-mirrored.yml
```

## Remove shadowing, put circuit breaking
```bash
oc delete -f ./services/ball/virtualservice-mirrored.yml
oc apply -f ./services/ball/destrule-outlier.yml
```

## To clean up everything

```bash
oc delete deployments -l project=mesh-arena
oc delete svc -l project=mesh-arena
oc delete virtualservices -l project=mesh-arena
oc delete destinationrules -l project=mesh-arena
```

## To build the demo

For the first build, it is necessary to get the JS dependencies on your filesystem:

```bash
cd services/ui/src/main/resources/webroot/
npm install
# back to project root
cd -
```

Then build everything:

```bash
# Trigger maven build + docker builds
# Ex: for docker namespace "myname" and tag "dev"
./buildall.sh myname dev
```

Then update all the deployment YAML to have the correct docker tag on images

## To generate the full-* templates
```bash
./gentemplate.sh
```
