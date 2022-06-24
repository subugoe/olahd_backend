cp -n src/main/resources/application.properties.template src/main/resources/application.properties
cp -n src/main/resources/application-development.properties.template src/main/resources/application-development.properties
docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml down -v
docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml pull
docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml build --no-cache # olahds_search
docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml up -d olahds_search olahds_redis olahds_indexer olahds_web_notifier olahds_kibana olahds_mongo
