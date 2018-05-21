# demo-mesh-arena

## How-to

- Run UI's main
- Open browser to localhost:8080
- Run Ball's main
- Run Stadium's main
- Start game: ```curl -H "Content-Type: application/json" -X PUT http://localhost:8082/start```
- Shoot in the ball: ```curl -H "Content-Type: application/json" -X PUT -d '{"dx":200,"dy":10}' http://localhost:8081/shoot```
