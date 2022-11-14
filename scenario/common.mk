scen-init-ui:
	kubectl create namespace ${NAMESPACE} || true && ./gentpl.sh ui-hotspot -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f -
	make expose

scen-init-stadium:
	./gentpl.sh stadium-hotspot -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f -

scen-init-ball:
	./gentpl.sh ball-hotspot -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f -

scen-init-players:
	./gentpl.sh player-locals -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f - ; \
	./gentpl.sh player-visitors -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f -

scen-init-rest:
	./gentpl.sh stadium-hotspot -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f - ; \
	./gentpl.sh ball-hotspot -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f - ; \
	./gentpl.sh ai-hotspot -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f - ; \
	./gentpl.sh ai-openj9 -v base -pp IfNotPresent -d "quay.io" -u jotak -t ${LATEST} -n ${NAMESPACE} | kubectl -n ${NAMESPACE} apply -f -
