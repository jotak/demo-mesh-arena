Devops D-Day

export KUBECONFIG=/home/jotak/devopsdday/devopsdday.cfg 
kubectl get nodes

# Istio
kubectl apply -f ~/istio-demo-1.0.2.yaml

# Kiali
cd /home/jotak/devopsdday
kubectl create -f kiali-configmap.yaml -n istio-system
kubectl create -f kiali-secrets.yaml -n istio-system
kubectl create -f kiali.yaml -n istio-system

# (new term)
KUBECONFIG=/home/jotak/devopsdday/devopsdday.cfg kubectl port-forward svc/kiali 20001:20001 -n istio-system

# Demo
cd demo-mesh-arena

# UI
kubectl apply -f <(istioctl kube-inject -f ./services/ui/Deployment.yml)
kubectl create -f ./services/ui/Service.yml
kubectl apply -f mesh-arena-gateway.yaml 

# (new term)
KUBECONFIG=/home/jotak/devopsdday/devopsdday.cfg kubectl port-forward svc/istio-ingressgateway 8080:80 -n istio-system

# Stadium & ball
kubectl apply -f <(istioctl kube-inject -f ./services/stadium/Deployment-Smaller.yml)
kubectl create -f ./services/stadium/Service.yml
kubectl apply -f <(istioctl kube-inject -f ./services/ball/Deployment.yml)
kubectl create -f ./services/ball/Service.yml

# Players
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-2-locals.yml)
kubectl create -f ./services/ai/Service-locals.yml
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-2-visitors.yml)
kubectl create -f ./services/ai/Service-visitors.yml

# Second ballon
kubectl apply -f <(istioctl kube-inject -f ./services/ball/Deployment-v2.yml)

# Pondération
istioctl create -f ./services/ball/destrule.yml
istioctl create -f ./services/ball/virtualservice-75-25.yml

# Messi / Mbappé
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Messi.yml)
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-Mbappe.yml)

# Chacun son ballon
istioctl replace -f ./services/ball/virtualservice-by-label.yml

# Reset
kubectl delete -f ./services/ai/Deployment-Messi.yml
kubectl delete -f ./services/ai/Deployment-Mbappe.yml
istioctl delete -f ./services/ball/virtualservice-by-label.yml

# Ballon crevé, shadowing
istioctl create -f ./services/ball/virtualservice-mirrored.yml
kubectl apply -f <(istioctl kube-inject -f ./services/ball/Deployment-burst.yml)

# CB
istioctl delete -f ./services/ball/virtualservice-mirrored.yml
istioctl replace -f ./services/ball/destrule-outlier.yml

# Classico
kubectl delete -f ./services/ball/Deployment-v2.yml
kubectl delete -f ./services/ai/Deployment-2-locals.yml
kubectl delete -f ./services/ai/Deployment-2-visitors.yml
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-OM.yml)
kubectl apply -f <(istioctl kube-inject -f ./services/ai/Deployment-PSG.yml)


