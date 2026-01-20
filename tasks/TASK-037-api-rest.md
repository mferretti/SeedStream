# TASK-037: API - REST Interface

**Status**: ⏸️ Not Started (Future Enhancement)  
**Priority**: P3 (Low)  
**Phase**: 10 - Future Enhancements  
**Dependencies**: All core modules  
**Human Supervision**: MEDIUM

---

## Objective

Implement REST API for triggering data generation jobs programmatically via HTTP, enabling integration with CI/CD pipelines and web applications.

---

## API Design

### Endpoints

**POST /api/v1/generate**
```json
{
  "job": "config/jobs/kafka_address.yaml",
  "count": 10000,
  "seed": 12345,
  "format": "json"
}
```

Response:
```json
{
  "job_id": "uuid-here",
  "status": "running",
  "started_at": "2026-01-20T10:00:00Z"
}
```

**GET /api/v1/jobs/{job_id}**
```json
{
  "job_id": "uuid-here",
  "status": "completed",
  "records_generated": 10000,
  "started_at": "2026-01-20T10:00:00Z",
  "completed_at": "2026-01-20T10:00:05Z",
  "throughput": 2000
}
```

**GET /api/v1/jobs**
List all jobs (with pagination)

**DELETE /api/v1/jobs/{job_id}**
Cancel running job

---

## Implementation

### Technology Stack
- Javalin or Spring Boot
- Async job execution
- Job status storage (H2/Redis)
- OpenAPI/Swagger docs

### New Module: `api`
```
api/
├── src/main/java/com/datagenerator/api/
│   ├── ApiServer.java
│   ├── GenerateController.java
│   ├── JobStatusService.java
│   └── models/
```

---

## Acceptance Criteria

- ⏸️ REST API server
- ⏸️ Job submission endpoint
- ⏸️ Job status tracking
- ⏸️ Job cancellation
- ⏸️ OpenAPI documentation
- ⏸️ Authentication (API keys)

---

**Completion Date**: [Future Enhancement]
