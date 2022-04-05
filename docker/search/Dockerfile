FROM docker.elastic.co/elasticsearch/elasticsearch:6.8.23
#FROM docker.elastic.co/elasticsearch/elasticsearch:7.17.0

LABEL maintainer="panzer@sub.uni-goettingen.de"

ENV TMP_ES_PATH_CONF=/tmp/es-config

COPY ./config/stopwords.txt /usr/share/elasticsearch/config/stopwords.txt

COPY config/mapping_meta.dz_phys.json config/mapping_meta.dz_log.json /

RUN set -x && \
    bin/elasticsearch-plugin install analysis-icu && \
    curl https://raw.githubusercontent.com/vishnubob/wait-for-it/ed77b63706ea721766a62ff22d3a251d8b4a6a30/wait-for-it.sh --output /usr/local/bin/wait-for-it.sh && \
    chmod +x /usr/local/bin/wait-for-it.sh && \
    for ELASTIC_PORT in $(seq 9200 9300); do nc -v -z -w 1 0.0.0.0 $ELASTIC_PORT 2&> /dev/null && echo "Port $ELASTIC_PORT unuseable, skipping" || echo "Using $ELASTIC_PORT" && export ELASTIC_PORT && break ; done && \
    mkdir -m 777 -p $TMP_ES_PATH_CONF && \
    cp /usr/share/elasticsearch/config/* $TMP_ES_PATH_CONF/ && \
    echo "http.port: $ELASTIC_PORT" >> $TMP_ES_PATH_CONF/elasticsearch.yml && \
    ES_PATH_CONF=$TMP_ES_PATH_CONF /usr/local/bin/docker-entrypoint.sh elasticsearch -p /tmp/es.pid -d && \
    /usr/local/bin/wait-for-it.sh -s -t 120 0.0.0.0:$ELASTIC_PORT -- curl -X PUT http://0.0.0.0:$ELASTIC_PORT/meta.dz_phys/ -H 'Content-Type: application/json' --data-binary @/mapping_meta.dz_phys.json && \
    /usr/local/bin/wait-for-it.sh -s -t 120 0.0.0.0:$ELASTIC_PORT -- curl -X PUT http://0.0.0.0:$ELASTIC_PORT/meta.dz_log/ -H 'Content-Type: application/json' --data-binary @/mapping_meta.dz_log.json && \
    /usr/local/bin/wait-for-it.sh -s -t 120 0.0.0.0:$ELASTIC_PORT -- curl -X PUT  http://0.0.0.0:$ELASTIC_PORT/_all/_settings -H 'Content-Type: application/json' -d '{"index.blocks.read_only_allow_delete": null}' && \
    find $TMP_ES_PATH_CONF  -name "*phys*.json"  -type f |while IFS= read -r line ; do /usr/local/bin/wait-for-it.sh -s -t 120 0.0.0.0:$ELASTIC_PORT -- curl -X POST http://0.0.0.0:$ELASTIC_PORT/meta.phys/_doc -H 'Content-Type: application/json' --data-binary @$line ; done && \
    find $TMP_ES_PATH_CONF  -name "*log*.json"  -type f |while IFS= read -r line ; do /usr/local/bin/wait-for-it.sh -s -t 120 0.0.0.0:$ELASTIC_PORT -- curl -X POST http://0.0.0.0:$ELASTIC_PORT/meta.log/_doc -H 'Content-Type: application/json' --data-binary @$line ; done && \
    kill $(cat /tmp/es.pid) && \
    rm /mapping_meta.dz_phys.json /mapping_meta.dz_log.json
