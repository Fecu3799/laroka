run:
	cd backend && mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

test:
	cd backend && mvnw.cmd test

build:
	cd backend && mvnw.cmd package -DskipTests