# Présentation du jeu

## Mise en place du jeu
`
```
kubectl apply -f <(istioctl kube-inject -f ./services/ui/Deployment.yml)
kubectl apply -f ./services/ui/Service.yml
```

## Mise en place du stade

```
kubectl apply -f <(istioctl kube-inject -f ./services/stadium/Deployment-Smaller.yml)
kubectl apply -f ./services/stadium/Service.yml
```

# Mise en place du ballon
```
kubectl apply -f <(istioctl kube-inject -f ./services/ball/Deployment.yml)
kubectl apply -f ./services/ball/Service.yml
```

# Faites entrer les joueurs
```
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-2-locals.yml)
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-2-visitors.yml)

kubectl create -f ./services/ai/Service-locals.yml
kubectl create -f ./services/ai/Service-visitors.yml
```

# Second ballon
```
kubectl apply -f <(istioctl kube-inject -f ./services/ball/Deployment-v2.yml)
```

# Pondération des ballons
```
istioctl create -f ./services/ball/destrule.yml
istioctl create -f ./services/ball/virtualservice-75-25.yml
```

# Nouveaux joueurs de talent
```
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Messi.yml)
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Mbappe.yml)
