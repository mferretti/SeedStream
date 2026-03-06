# US-037: REST API Interface (Future)

**Status**: ⏸️ Not Started  
**Priority**: P3 (Low - Future Enhancement)  
**Phase**: 10 - Future Enhancements  
**Dependencies**: All core modules

---

## User Story

As a **DevOps engineer**, I want **a REST API for triggering generation jobs** so that **I can integrate the tool into CI/CD pipelines, web applications, and automation systems**.

---

## ⚠️ **FUTURE ENHANCEMENT** ⚠️

This is a future feature, not part of the initial release. CLI interface covers most use cases.

---

## Acceptance Criteria

- ⏸️ REST API server with Javalin or Spring Boot
- ⏸️ POST /api/v1/generate endpoint for job submission
- ⏸️ GET /api/v1/jobs/{id} endpoint for status checking
- ⏸️ GET /api/v1/jobs endpoint for listing jobs
- ⏸️ DELETE /api/v1/jobs/{id} endpoint for cancellation
- ⏸️ Async job execution with status tracking
- ⏸️ Job status storage (H2 or Redis)
- ⏸️ OpenAPI/Swagger documentation
- ⏸️ API key authentication
- ⏸️ Rate limiting

---

## Implementation Notes (Future)

### Technology Stack
- **Web framework**: Javalin (lightweight) or Spring Boot
- **Job queue**: In-memory or Redis
- **Storage**: H2 (embedded) or PostgreSQL
- **API docs**: Swagger/OpenAPI

### API Endpoints
```
POST /api/v1/generate
  Request: { job, count, seed, format }
  Response: { job_id, status, started_at }

GET /api/v1/jobs/{job_id}
  Response: { job_id, status, records_generated, ... }

DELETE /api/v1/jobs/{job_id}
  Response: { cancelled: true }
```

### New Module
Create `api` module with:
- ApiServer.java
- GenerateController.java
- JobStatusService.java
- Security/authentication

---

## Testing Requirements (Future)

### API Tests
- Submit job via REST
- Check job status
- Cancel running job
- Authentication works
- Rate limiting works

### Integration Tests
- Full end-to-end via API
- Multiple concurrent jobs
- Job persistence across restarts

---

## Definition of Done (Future)

- [ ] REST API server implemented
- [ ] All endpoints working
- [ ] Job tracking and status
- [ ] OpenAPI documentation
- [ ] Authentication implemented
- [ ] API tests passing
- [ ] Documentation updated
- [ ] PR reviewed and approved

---

**Note**: This feature is deferred to Phase 10 as the CLI interface covers the primary use cases. REST API adds value for web integration and remote execution, but increases complexity significantly.
