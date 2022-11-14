istio-enable:
	kubectl label namespace ${NAMESPACE} ${ISTIO_LABEL} ; \
	kubectl -n ${NAMESPACE} delete pods -l "project=mesh-arena"

jaeger-service:
	kubectl apply -f ./istio/jaeger-collector.yml

istio-init-ui:
	./gentpl.sh ui-hotspot -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f -
	kubectl apply -f istio/mesh-arena-gateway.yml
	xdg-open http://localhost:8080/ && kubectl port-forward svc/istio-ingressgateway 8080:80 -n istio-system

istio-add-ball:
	kubectl apply -f ./istio/destrule.yml -n ${NAMESPACE} ; \
	./gentpl.sh ball-openj9 -v base -vo oopsie -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f -

istio-add-burst:
	kubectl apply -f ./istio/destrule-burst.yml -n ${NAMESPACE} ; \
	kubectl delete deployment ball-oopsie -n ${NAMESPACE} ; \
	./gentpl.sh ball-openj9 -v burst -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f -

istio-75-25:
	kubectl apply -f ./istio/virtualservice-75-25.yml -n ${NAMESPACE}

istio-add-players:
	./gentpl.sh ai-hotspot -v mbappe -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f - ; \
	./gentpl.sh ai-openj9 -v messi -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f - ; \
	kubectl delete -f ./istio/virtualservice-75-25.yml -n ${NAMESPACE}

istio-by-source-label:
	kubectl apply -f ./istio/virtualservice-by-label.yml -n ${NAMESPACE}

istio-reset:
	kubectl delete virtualservice ball-vs -n ${NAMESPACE} ; \
	kubectl delete deployment ai-mbappe -n ${NAMESPACE} ; \
	kubectl delete deployment ai-messi -n ${NAMESPACE}

istio-mirroring:
	kubectl apply -f ./istio/virtualservice-mirrored.yml -n ${NAMESPACE}

istio-outlier:
	kubectl delete -f ./istio/virtualservice-mirrored.yml -n ${NAMESPACE} ; \
  kubectl apply -f ./istio/destrule-outlier.yml -n ${NAMESPACE}
