#!/usr/bin/env bash

echo "# Generated template (`date`)" > full.yml

function gen_service() {
    echo "# Source: services/$1/$2" >> full.yml
    cat services/$1/$2 >> full.yml
    echo "" >> full.yml
    echo "---" >> full.yml
    echo "# Source: services/$1/$3" >> full.yml
    cat services/$1/$3 >> full.yml
    echo "" >> full.yml
    echo "---" >> full.yml
}

gen_service "ui" "Deployment.yml" "Service.yml"
gen_service "stadium" "Deployment-Smaller.yml" "Service.yml"
gen_service "ball" "Deployment.yml" "Service.yml"
gen_service "ai" "Deployment-2-locals.yml" "Service-locals.yml"
gen_service "ai" "Deployment-2-visitors.yml" "Service-visitors.yml"

echo "Template generated: full.yml"