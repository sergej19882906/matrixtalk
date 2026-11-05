FROM halfshot/matrix-hookshot:latest

COPY entrypoint.sh /usr/local/bin/matrix-talk-entrypoint.sh
RUN chmod +x /usr/local/bin/matrix-talk-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/matrix-talk-entrypoint.sh"]
# Wrapper supplies config paths; do not append inherited upstream CMD again.
CMD []
