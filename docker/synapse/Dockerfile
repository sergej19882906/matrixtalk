FROM matrixdotorg/synapse:latest

RUN pip install --no-cache-dir ruamel.yaml

COPY entrypoint.sh /usr/local/bin/matrix-talk-entrypoint.sh
COPY configure.py /usr/local/bin/configure.py

RUN chmod +x /usr/local/bin/matrix-talk-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/matrix-talk-entrypoint.sh"]
