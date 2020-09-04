# demo-mesh-arena

## Slides

This demo was presented at [DevopsDday](http://2018.devops-dday.com/) in the Velodrome, Marseilles' famous stadium.
[Here are the slides](https://docs.google.com/presentation/d/1PzRD3BquEI3Al6y2_vSrZqUY0AlJF54_uuWYhr81t5g), in French. Or a similar [English version](https://docs.google.com/presentation/d/1WZDmIcfzKC9GMqz8Cvcb0_mJK_hIH-JxEDROZLnEnng).

## Step-by-step

For a step-by-step walk-through, [read this](./STEP-BY-STEP.md) (outdated - you might run through the "scenario" steps instead, read `make man`).

## Pre-requisite

- Kubernetes cluster running
- Istio installed (much better with Kiali!)
- `yq` might be needed at some point, if you use deploy targets; grab it from there: https://github.com/mikefarah/yq/releases (Last version known to work here: 3.1.1)

## Quick start

This quick start doesn't require you to clone the repo, but offers less interactivity.

If not already done, enable istio injection:
```bash
kubectl label namespace default istio-injection=enabled
```

Run one of the commands below:

```bash
# With app metrics enabled:
kubectl apply -f <(curl -L https://raw.githubusercontent.com/jotak/demo-mesh-arena/master/quickstart-metrics.yml) -n default

# With app traces enabled:
kubectl apply -f <(curl -L https://raw.githubusercontent.com/jotak/demo-mesh-arena/master/quickstart-tracing.yml) -n default

# With both enabled:
kubectl apply -f <(curl -L https://raw.githubusercontent.com/jotak/demo-mesh-arena/master/quickstart-both.yml) -n default

# With none enabled:
kubectl apply -f <(curl -L https://raw.githubusercontent.com/jotak/demo-mesh-arena/master/quickstart-naked.yml) -n default
```

## Advanced

- Clone this repo
- Read the manual!

```bash
make man
```

It covers a bunch of make targets, deployment options, Istio scenario, with or without Kafka, etc.
