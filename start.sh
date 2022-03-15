docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml down -v
docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml build --no-cache olahds_kibana olahds_search
docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml up -d olahds_kibana olahds_search
