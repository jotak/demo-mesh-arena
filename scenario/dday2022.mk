dday-init-ui: scen-init-ui
dday-init-stadium: scen-init-stadium
dday-init-ball: scen-init-ball
dday-init-players: scen-init-players

dday-22-players:
	kubectl scale deployment -n ${NAMESPACE} player-locals --replicas=11
	kubectl scale deployment -n ${NAMESPACE} player-visitors --replicas=11

dday-100-players:
	kubectl scale deployment -n ${NAMESPACE} player-locals --replicas=50
	kubectl scale deployment -n ${NAMESPACE} player-visitors --replicas=50

dday-hack-maradona:
	kubectl create namespace hacker || true && ./gentpl.sh player-hacker -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n hacker | kubectl -n hacker apply -f -

dday-unhack:
	kubectl delete namespace hacker

dday-setup-policy:
	kubectl apply -n ${NAMESPACE} -f ./scenario/dday2022/network-policy.yaml

dday-delete-policy:
	kubectl delete -n ${NAMESPACE} -f ./scenario/dday2022/network-policy.yaml

dday-undeploy: undeploy
	kubectl delete namespace hacker
