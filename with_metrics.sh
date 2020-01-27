sed -n '1h;1!H;${;g;s/\(METRICS_ENABLED\s*value: \)"0"/\1"1"/g;p;}'
