FROM dock.mau.dev/mautrix/whatsapp:latest

COPY entrypoint.sh /usr/local/bin/matrix-talk-entrypoint.sh
RUN chmod +x /usr/local/bin/matrix-talk-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/matrix-talk-entrypoint.sh"]
