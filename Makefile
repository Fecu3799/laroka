run-back:
	cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

run-client:
	cd client && npm run dev

run-backoffice:
	cd backoffice && npm run dev

test:
	 make test-back && make test-front

test-back:
	cd backend && ./mvnw test

test-front:
	cd client && npm test && npm run test:e2e && cd ../backoffice && npm test && npm run test:e2e

test-e2e:
	cd client && npm run test:e2e && cd ../backoffice && npm run test:e2e

lint:
	make lint-back && make lint-front

lint-back:
	cd backend && ./mvnw checkstyle:check

lint-front:
	cd client && npm run lint && cd ../backoffice && npm run lint

build:
	cd backend && ./mvnw package -DskipTests