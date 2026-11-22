#!/usr/bin/env sh
set -eu

tls_dir=/etc/nginx/tls
tls_domain=${TLS_DOMAIN:-localhost}
letsencrypt_dir=/etc/letsencrypt/live/$tls_domain
mkdir -p "$tls_dir"

# A real certificate is copied from the read-only Let's Encrypt mount. If the
# mount is absent, local compose remains runnable with a clearly self-signed
# certificate; production should always set TLS_DOMAIN and mount Let's Encrypt.
if [ -r "$letsencrypt_dir/fullchain.pem" ] && [ -r "$letsencrypt_dir/privkey.pem" ]; then
    cp "$letsencrypt_dir/fullchain.pem" "$tls_dir/fullchain.pem"
    cp "$letsencrypt_dir/privkey.pem" "$tls_dir/privkey.pem"
    chmod 600 "$tls_dir/privkey.pem"
elif [ ! -s "$tls_dir/fullchain.pem" ] || [ ! -s "$tls_dir/privkey.pem" ]; then
    openssl req -x509 -nodes -newkey rsa:2048 -days 30 \
        -keyout "$tls_dir/privkey.pem" \
        -out "$tls_dir/fullchain.pem" \
        -subj "/CN=$tls_domain" \
        -addext "subjectAltName=DNS:$tls_domain,IP:127.0.0.1" \
        >/dev/null 2>&1
    chmod 600 "$tls_dir/privkey.pem"
fi

# The checked-in config is valid by itself with a documentation-only allowlist.
# An operator can replace that single CIDR without rebuilding the image.
if [ -n "${INTRANET_ALLOWED_IP:-}" ]; then
    sed -i "s|allow 192.0.2.1/32;|allow ${INTRANET_ALLOWED_IP};|g" \
        /etc/nginx/conf.d/default.conf
fi

# HTTP is useful for the local production smoke test. The VPS sets true so all
# clear-text requests are redirected to the TLS listener.
if [ "${FORCE_HTTPS:-false}" != "true" ]; then
    sed -i 's|^[[:space:]]*if (\$scheme = http).*|    # HTTP is allowed for the local smoke test.|' \
        /etc/nginx/conf.d/default.conf
fi

exec /docker-entrypoint.sh "$@"
