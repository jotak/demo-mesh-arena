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
- `yq` is needed, grab it from there: https://github.com/mikefarah/yq/releases (Last version known to work here: 3.1.1)


## Man

Most doc is contained there:

```bash
make help
```

This README will only cover a little.

## Deploy all

Quick start, using latest version:

```bash
make deploy-latest
```

This deployment method includes runtime metrics.

## Build the demo

For the first build, it is necessary to get the JS dependencies on your filesystem:

```bash
make prepare
```

Then build everything, with images tagged for local usage with Minikube, and deploy:

```bash
make build images deploy
```
