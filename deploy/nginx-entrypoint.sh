#!/bin/sh
set -eu

template_dir=/etc/nginx/templates
output=/etc/nginx/conf.d/default.conf
certificate=/etc/letsencrypt/live/pte-platform/fullchain.pem

if [ -s "$certificate" ] && [ -s "/etc/letsencrypt/live/pte-platform/privkey.pem" ]; then
    template="$template_dir/nginx.conf.template"
else
    template="$template_dir/nginx.bootstrap.conf.template"
fi

envsubst '${TENANT_DOMAIN} ${ADMIN_DOMAIN} ${MEDIA_DOMAIN}' \
    < "$template" > "$output"

nginx -t
exec nginx -g 'daemon off;'
