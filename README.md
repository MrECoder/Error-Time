# Error-Time

Sub-Tasks (Story Breakdown)
 
Subtask 1 — Core Exception Model
' Implement AppException
' Create base domain exceptions:
' ValidationException
 	ResourceNotFoundException
 	InternalServiceException
 
  Subtask 2 — Global Exception Handler
 	Implement @RestContr011erAdvice
 	Add handlers for:   AppException   generic Exception
 	Return ProblemDetail
 
 Subtask 3 — Validation Handling
  Handle MethodArgumentNotVa1idException
  Handle ConstraintVi01ationException
  Implement field-level error aggregation
 
P Subtask 4— Distributed Tracing
' Integrate OpenTelemetry
  Configure tracing exporter
' Inject traceld into responses
' Configure logging with traceld
 
Subtask 5 — Feign Error Mapping
' Implement custom ErrorDecoder
 	Map downstream errors * AppException
 	Add traceld propagation interceptor
 
  Subtask 6 — Retry Mechanism
' Enable Spring Retry
' Add @Retryab1e to downstream calls   Configure backoff strategy https://m365.cloud.microsoft/chat/conversation/b2d88906-0e2d-43d9-bf5f-860f9bfd38ac?auth=2 1/2 18/06/2026, 08:50	which Al engine do you run on? are you a Microsoft ' Implement @Recover
 
g Subtask 7 — AMQP Error Handling
' Implement consistent exception handling in listeners
' Define retry vs DLQ conditions
' Log errors with traceld
 
 Subtask 8 — Metrics Integration
 	Integrate Micrometer   Add counters:
 	app.errors
 	downstream.errors
' Ensure Prometheus compatibility
 
  Subtask 9 — Logging Improvements
' Update log format to include traceld
' Ensure errors logged centrally in handler
 
 Subtask 10 — Integration Testing
 	Test:
 	validation errors   domain exceptions
' downstream failures  	Verify:
' response format
 	traceld presence
' metrics increment https://m365.cloud.microsoft/chat/conversation/b2d88906-0e2d-43d9-bf5f-860f9bfd38ac?auth=2 2/2
