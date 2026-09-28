# AWS Academy Learner Lab: despliegue del backend

RepairMatch es solo una API Spring Boot. El recorrido propuesto para el laboratorio es PostgreSQL en RDS, imagen en ECR y servicio ECS Fargate detrás de un Application Load Balancer (ALB). No se necesita frontend. Esta guía prepara el despliegue; **no afirma que ya se haya desplegado en AWS**. La colección del recorrido completo está integrada en [Postman MVP](../postman/RepairMatch-MVP.postman_collection.json).

## Antes de encender el Lab

Desde la raíz del repositorio, ejecutar `./mvnw.cmd --batch-mode --no-transfer-progress verify` en Windows y `docker build -t repairmatch-backend:lab .`. La imagen utiliza Java 17 para compilar y ejecutar, y arranca con el perfil `prod`.

La aplicación escucha en el puerto 8080 y expone `GET /actuator/health` sin autenticación para el ALB. El perfil `prod` exige `DB_URL`, `DB_USER`, `DB_PASSWORD` y `JWT_SECRET`. La URL es JDBC (`jdbc:postgresql://HOST:5432/repairmatch`), no una URL `postgres://`. No guardar secretos ni credenciales AWS en Git, en capturas ni en un archivo compartido.

## Esquema de una base nueva

El perfil `prod` usa `spring.jpa.hibernate.ddl-auto=validate` y **no crea tablas**. El repositorio todavía no tiene migraciones versionadas. Para este laboratorio, sobre una base RDS **nueva y vacía solamente**, ejecutar una única task de inicialización con la misma imagen y variables de la task final, pero agregando temporalmente `SPRING_JPA_HIBERNATE_DDL_AUTO=update`. Verificar en CloudWatch que la task arrancó y que creó el esquema; detenerla. Registrar después una nueva revisión de la task definition **sin** esa variable y usarla para el service permanente. Nunca volver a ejecutar la inicialización sobre una base con datos: `update` no sustituye una migración y puede cambiar el esquema sin control.

Antes de usar esta aplicación fuera del Learner Lab o de preservar datos reales, crear migraciones SQL versionadas y probarlas contra PostgreSQL. No cambiar el perfil `prod` a `update` para evadir una falla de `validate`.

## Orden de la consola

1. Entrar al **AWS Academy Learner Lab desde Canvas**, iniciar el Lab y abrir la consola AWS desde allí. AWS Educate no es el punto de entrada descrito en la guía del curso.
2. Confirmar la región autorizada por el Lab (la guía usa `us-east-1`) y el presupuesto disponible antes de crear recursos.
3. Crear RDS PostgreSQL en la misma VPC que ECS, con acceso público desactivado. Su security group debe aceptar el puerto 5432 **solo desde el security group de las tasks ECS**.
4. Crear un repositorio ECR privado, etiquetar la imagen con un identificador de versión y subirla. No depender únicamente de `latest`.
5. Registrar una task definition Fargate para el contenedor `repairmatch-backend`, puerto 8080, con los roles que el Learner Lab permita. Usar [inyección de secretos de ECS](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/secrets-envvar-secrets-manager.html) si el Lab la permite; no poner contraseñas o claves JWT en JSON versionado ni en variables en texto claro de una task definition compartida.
6. Inicializar una vez el esquema de la RDS vacía como se indica arriba. Registrar la revisión final con el perfil `prod` y sin el override de Hibernate.
7. Crear cluster, target group de tipo **IP** con health check `GET /actuator/health` y ALB. [Fargate con red `awsvpc` requiere targets IP](https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_LoadBalancer.html). Configurar los security groups: Internet → ALB:80; ALB → ECS:8080; ECS → RDS:5432. Crear un service Fargate con una task y la revisión final.
8. Verificar `http://<ALB-DNS>/actuator/health` (`{"status":"UP"}`). En Postman, cambiar `baseUrl` al DNS del ALB y ejecutar el recorrido; no enviar tokens ni credenciales a terceros.

El ALB HTTP del laboratorio no ofrece TLS por sí mismo. Evitar datos personales y contraseñas reales durante la demostración; para exposición pública real hace falta HTTPS y una gestión de secretos y migraciones adecuada.

## Al terminar

Reducir el service a cero tasks y eliminar el ALB si ya no se usará. RDS puede seguir generando cargos aunque el Lab esté detenido; revisar recursos y créditos en la consola. No borrar una RDS con datos que se deban conservar sin una copia verificada.
