#!/bin/bash -e
cp localdev/application.properties.localdev src/main/resources/application.properties
cp localdev/application-localdev.properties src/main/resources/application-localdev.properties
docker-compose -f docker-compose.base.yaml -f docker-compose.localdev.yaml -f docker-compose.kibana.yaml down -v
docker-compose -f docker-compose.base.yaml -f docker-compose.localdev.yaml -f docker-compose.kibana.yaml build --no-cache # olahds_search
docker-compose -f docker-compose.base.yaml -f docker-compose.localdev.yaml -f docker-compose.kibana.yaml up -d olahds_search olahds_redis olahds_web_notifier olahds_kibana olahds_mongo olahds_s3 olahds_indexer
