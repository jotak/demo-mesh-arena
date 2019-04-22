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

sed -n '1h;1!H;${;g;s/\(METRICS_ENABLED\s*value: \)"0"/\1"1"/g;p;}' full.yml > full-metrics.yml
sed -n '1h;1!H;${;g;s/\(TRACING_ENABLED\s*value: \)"0"/\1"1"/g;p;}' full.yml > full-tracing.yml
sed -n '1h;1!H;${;g;s/\(TRACING_ENABLED\s*value: \)"0"/\1"1"/g;p;}' full-metrics.yml > full-metrics-tracing.yml

echo "Templates generated: full.yml, full-metrics.yml, full-tracing.yml, full-metrics-tracing.yml"
