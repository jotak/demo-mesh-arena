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

Below instructions are given for OpenShift, but this is almost the same with standard Kubernetes.
Just replace `oc` with `kubectl`, ignore `oc expose`, ignore the `oc adm policy` stuff.

## OpenShift

For OpenShift users, you may have to grant extended permissions for Istio, logged as admin:

```bash
oc new-project mesh-arena
oc adm policy add-scc-to-user privileged -z default
```

### Deploy all

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

### Expose route

```bash
oc expose service ui
```

### Clean up everything

```bash
oc delete deployments -l project=mesh-arena
oc delete svc -l project=mesh-arena
oc delete virtualservices -l project=mesh-arena
oc delete destinationrules -l project=mesh-arena
```

## Build the demo

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

## Generate the full-* templates
```bash
./gentemplate.sh
```
