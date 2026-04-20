run-back:
	cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

run-client:
	cd client && npm run dev

run-backoffice:
	cd backoffice && npm run dev

test:
	cd backend && ./mvnw test

lint:
	cd backend && ./mvnw checkstyle:check

lint-front:
	cd client && npm run lint && cd ../backoffice && npm run lint

build:
	cd backend && ./mvnw package -DskipTests