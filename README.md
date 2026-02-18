# TaskManager API

Production-style REST API for task and project management with JWT authentication, role-based authorization, ownership enforcement, file attachments, collaborator management, advanced filtering/sorting, and comprehensive automated tests.

## Table of Contents

- [Overview](#overview)
- [Core Features](#core-features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Domain Model](#domain-model)
- [API Surface](#api-surface)
- [Security Model](#security-model)
- [Configuration](#configuration)
- [Local Development](#local-development)
- [Testing](#testing)
- [Continuous Integration](#continuous-integration)
- [Error Handling](#error-handling)
- [Project Structure](#project-structure)
- [Roadmap](#roadmap)

## Overview

`taskmanager-api` is a backend service designed to demonstrate clean layered architecture and practical backend engineering patterns:

- API-first CRUD workflows for projects, tasks, files, users, and collaborators.
- Strong input validation and standardized error responses.
- Business-rule enforcement in services (ownership checks, role constraints, pagination/sorting validation).
- Real relational persistence with PostgreSQL and JPA.
- Combination of integration and unit tests for confidence at multiple levels.

## Core Features

- JWT authentication (`/auth/register`, `/auth/login`).
- Role model (`ADMIN`, `USER`) with route-level access rules.
- User self-service profile endpoints (`/users/me`).
- Admin user management (`/admin/users`) with sorting/filtering.
- Project and task lifecycle management with ownership protection.
- File upload and retrieval scoped to owned tasks.
- Collaborator assignment with responsible-collaborator business rules.
- Pagination + sorting + filtering on list endpoints.
- Global exception handling with consistent API error payloads.

## Architecture

Layered architecture with clear responsibilities:

- **Controller layer**: HTTP contracts, authentication principal extraction, request/response mapping.
- **Service layer**: business logic, authorization/ownership checks, validation of sort/filter/pagination constraints.
- **Repository layer**: persistence access via Spring Data JPA + Specifications.
- **Configuration layer**: security chain, JWT utilities/filtering, environment-driven datasource config.

Design principles used:

- Thin controllers and behavior-rich services.
- DTO boundaries for external payloads.
- Centralized exception translation (`GlobalExceptionHandler`).
- Explicit ownership checks to prevent cross-user data access.

## Technology Stack

- Java 17
- Spring Boot 4
- Spring Web MVC
- Spring Security
- Spring Data JPA (Hibernate)
- PostgreSQL
- Bean Validation (Jakarta Validation)
- Lombok
- JWT (`io.jsonwebtoken`)
- Maven
- JUnit 5 + Spring Test + Mockito

## Domain Model

Main entities:

- `User`: credentials, role, profile data, ownership root.
- `Project`: owned by one user, contains tasks.
- `Task`: belongs to one project, contains files and collaborations.
- `File`: attached to a task.
- `Collaborate`: join entity between task and user with `responsible` flag.

## API Surface

### Auth

- `POST /auth/register`
- `POST /auth/login`

### User

- `GET /users/me`
- `PATCH /users/me`
- `GET /users/search`

### Admin

- `GET /admin/users`
- `DELETE /admin/users/{id}`

### Projects

- `POST /projects`
- `GET /projects/page`
- `GET /projects/{id}`
- `PATCH /projects/{id}`
- `DELETE /projects/{id}`

### Tasks

- `POST /projects/{projectId}/tasks`
- `GET /projects/{projectId}/tasks/page`
- `GET /projects/{projectId}/tasks/{id}`
- `PATCH /projects/{projectId}/tasks/{id}`
- `DELETE /projects/{projectId}/tasks/{id}`

### Files

- `POST /tasks/{taskId}/files`
- `GET /tasks/{taskId}/files/page`
- `GET /tasks/{taskId}/files/{fileId}`
- `DELETE /tasks/{taskId}/files/{fileId}`

### Collaborators

- `POST /tasks/{taskId}/collaborators`
- `GET /tasks/{taskId}/collaborators`
- `GET /tasks/{taskId}/responsible`
- `DELETE /tasks/{taskId}/collaborators/{userId}`

## Security Model

- Stateless authentication with JWT.
- Public access limited to `/auth/**` and `/error`.
- Admin routes protected with `hasRole("ADMIN")`.
- All other routes require authentication.
- Service-level ownership checks ensure users access only their own resources.

## Configuration

Application configuration is environment-driven.

Key variables:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `DB_HOST`
- `DB_PORT`

`application.yml` imports local `.env` with:

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

This supports local IntelliJ/Maven runs without custom launcher env wiring.

## Local Development

### 1) Prepare environment

Use `.env.example` as template:

```bash
cp .env.example .env
```

### 2) Start PostgreSQL

```bash
docker compose up -d
```

### 3) Run the API

```bash
mvn spring-boot:run
```

Server starts on `http://localhost:8080` by default.

### 4) Stop services

```bash
docker compose down
```

## Testing

### Run all tests

```bash
mvn test
```

### Integration tests (controllers + DB)

Coverage includes:

- Auth flows and validation/error scenarios.
- User and admin behavior with authorization constraints.
- Project/task/file/collaborator ownership rules.
- Sorting/filtering/pagination behavior.
- Global error handlers (`400/404/405` and malformed payloads).

### Unit tests (services)

Dedicated service test classes:

- `UserServiceTest`
- `ProjectServiceTest`
- `TaskServiceTest`
- `FileServiceTest`
- `CollaborateServiceTest`

These validate business rules in isolation with mocked dependencies.

## Continuous Integration

GitHub Actions CI is configured in `.github/workflows/ci.yml`.

- Triggers on `push` to `main` and on all pull requests.
- Starts a PostgreSQL service container (`postgres:17`) for database-backed tests.
- Sets test environment variables (`DB_HOST`, `DB_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`).
- Runs `mvn -B test` with Maven dependency caching.

## Error Handling

`GlobalExceptionHandler` standardizes API error payloads via `ApiError`:

- Validation failures (`400`)
- Illegal arguments mapped by message semantics (`400/403/404/409`)
- Missing route (`404`)
- Method not allowed (`405`)
- Malformed body (`400`)
- Type mismatch/missing parameter (`400`)
- Upload size violation (`413`)
- Fallback unknown exceptions (`500`)

## Project Structure

```text
src/main/java/ch/backend/taskmanagerapi/
  config/
  error/
  user/
  project/
  task/
  file/
  collaborate/

src/test/java/ch/backend/taskmanagerapi/
  ... integration tests (*IT)
  ... service unit tests (*ServiceTest)
```

## Roadmap

Current implementation is feature-complete for core product scope and testing.

Next production-hardening steps:

- API documentation polish (OpenAPI/Swagger).
- Database hardening via explicit SQL migrations/constraints.
- Refresh token flow for authentication lifecycle.
- Optional: Postman collection for API demo/distribution.
