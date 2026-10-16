# Financ.IA — Backend

API REST de Financ.IA, una app móvil de finanzas personales para Argentina con IA.
Hecha con **Spring Boot 4** (Java 21), PostgreSQL y Maven.

La app móvil (frontend) vive en el repositorio `Financ.IA-Font` (Expo / React Native).

## Requisitos

- **Java 21** (por ejemplo: `brew install openjdk@21`)
- **Docker** (para la base de datos)
- **Maven** (no hace falta instalarlo: el proyecto trae el wrapper `./mvnw`)

## Cómo levantarlo

### 1. Base de datos (PostgreSQL en Docker)

```bash
# crear el archivo de credenciales (una sola vez)
echo "DB_PASSWORD=una-clave-cualquiera" > .env

# prender la base
docker compose up -d
```

La base queda en `localhost:5433` (base `financia`, usuario `financia`), solo
accesible desde esta computadora. Los datos persisten en un volumen de Docker.

Para apagarla: `docker compose down`.

### 2. Variables de entorno

```bash
export JWT_SECRET="una-clave-de-al-menos-32-caracteres"   # obligatoria (firma de tokens JWT)
export GROQ_API_KEY="gsk_..."                              # opcional (IA; sin ella la app usa lógica rule-based)
```

### 3. Backend

```bash
./mvnw spring-boot:run
```

Arranca en `http://localhost:8080/api/v1` (health check: `GET /api/v1/health`).

## Usuario de prueba

- Email: `admin123@gmail.com`
- Contraseña: `admin123`

(Si la base es nueva, registrá un usuario desde la app o con:
`POST /api/v1/auth/register` → `{"name":"...","email":"...","password":"..."}`.)

## Estructura del código

```
src/main/java/com/FinancIA/api/
├── controller/   endpoints REST (Auth, Markets, Crypto, MyMoney, IA, Users...)
├── service/      lógica de negocio, clientes de APIs externas (BCRA, Binance,
│                 Yahoo Finance, DolarAPI, Groq) y jobs programados (8:00 tasas, 8:15 cripto)
├── repository/   acceso a PostgreSQL (Spring Data JPA)
├── domain/       entidades (tablas)
├── dto/          objetos de entrada/salida de la API
├── security/     JWT (login, filtro, configuración)
├── config/       CORS
└── exception/    manejo centralizado de errores
```

Configuración en `src/main/resources/application.yaml` (credenciales de la base
en `.env.properties`, que no se versiona).

## Integraciones externas

| Fuente | Uso |
|---|---|
| BCRA | tasas BADLAR, dólar minorista, inflación (IPC) |
| Binance | precios y velas de BTC/ETH (+ criptos informativas) |
| Yahoo Finance | cotizaciones, fundamentales y noticias de acciones |
| DolarAPI | cotizaciones del dólar (oficial, blue, MEP, CCL) |
| Groq | IA: asesor virtual, explicaciones y recomendaciones |
| Expo Push | notificaciones de señales cripto y stop-loss |
