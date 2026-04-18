run:
	cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local

test:
	cd backend && ./mvnw test

build:
	cd backend && ./mvnw package -DskipTests