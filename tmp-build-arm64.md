## Tmp / building for arm64

```bash
podman manifest create mesharena-builder
podman build --platform linux/amd64,linux/arm64 --manifest mesharena-builder -f ./k8s/builder.dockerfile .

podman manifest create quay.io/jotak/mesharena-player:multiarch
podman build --platform linux/amd64,linux/arm64/v8 --manifest quay.io/jotak/mesharena-player:multiarch -f ./k8s/player.dockerfile .
podman manifest push quay.io/jotak/mesharena-player:multiarch

podman manifest create quay.io/jotak/mesharena-ball:multiarch
podman build --platform linux/amd64,linux/arm64/v8 --manifest quay.io/jotak/mesharena-ball:multiarch -f ./k8s/ball.dockerfile .
podman manifest push quay.io/jotak/mesharena-ball:multiarch

podman manifest create quay.io/jotak/mesharena-stadium:multiarch
podman build --platform linux/amd64,linux/arm64/v8 --manifest quay.io/jotak/mesharena-stadium:multiarch -f ./k8s/stadium.dockerfile .
podman manifest push quay.io/jotak/mesharena-stadium:multiarch

podman manifest create quay.io/jotak/mesharena-ui:multiarch
podman build --platform linux/amd64,linux/arm64/v8 --manifest quay.io/jotak/mesharena-ui:multiarch -f ./k8s/ui.dockerfile .
podman manifest push quay.io/jotak/mesharena-ui:multiarch
```

