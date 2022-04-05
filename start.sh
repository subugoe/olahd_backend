docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml down -v
docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml pull 
docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml build --no-cache # olahds_search
docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml up -d # olahds_search
docker-compose -f docker-compose.base.yaml -f docker-compose.local.yaml -f docker-compose.kibana.yaml logs -f # olahds_search