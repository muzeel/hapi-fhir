# Демонстрация Batch2 Monitoring Lab — HAPI FHIR
# Для преподавателя

## Что было реализовано

### 1. Exponential Backoff для повторов чанков
- Файл: `JpaJobPersistenceImpl.java`
- Формула: `delay = baseDelay * 2^(errorCount - 1)`, максимум 5 минут
- Класс: `RetryChunkLaterException.calculateExponentialBackoff()`

### 2. Graceful Shutdown
- Файлы: `JobMaintenanceServiceImpl.java`, `WorkChannelMessageListener.java`
- `@PreDestroy` + atomic flags для корректной остановки
- Таймаут ожидания завершения текущих задач: 120 секунд

### 3. Audit Journal (Журнал аудита)
- Entity: `Batch2JobAuditEntity` (таблица `BT2_JOB_AUDIT`)
- Repository: `IBatch2JobAuditRepository`
- Service: `IBatch2JobAuditSvc` / `Batch2JobAuditSvcImpl`
- Логирует: cancel, pause, resume операции с пользователем, статусами и сообщением
- Использует `REQUIRES_NEW` транзакцию для надёжности

### 4. Notification Pointcut
- Pointcut: `BATCH2_JOB_STATUS_CHANGE` в `Pointcut.java`
- Вызывается при cancel, pause, resume операциях
- Параметры: `(String instanceId, String defId, StatusEnum prior, StatusEnum newStatus, String message)`
- Позволяет подписаться на изменения статуса Batch2 jobs через interceptor

### 5. REST Endpoint `$hapi.fhir.batch2-job-history`
- Файл: `JpaSystemProvider.java`
- Фильтрация по jobId, operation, датам, пагинация

### 6. Кэширование статусов активных заданий
- Интерфейс: `IActiveJobStatusCacheSvc`
- Реализация: `ActiveJobStatusCacheSvcImpl`
- Автоматическое обновление каждые 30 секунд + scheduled job каждые 5 минут
- Кэширует статусы: QUEUED, IN_PROGRESS, FINALIZE

### 7. Пакетное обновление статусов chunks
- Repository: `IBatch2WorkChunkRepository.updateChunkStatusesInBatch()`
- Interface: `IWorkChunkPersistence.updateChunkStatusesInBatch()`
- Один SQL UPDATE для множества chunks вместо отдельных запросов
- JPQL: `UPDATE Batch2WorkChunkEntity e SET e.myStatus = :newStatus WHERE e.myId IN (:ids) AND e.myStatus IN (:oldStatuses)`

---

## Как запустить демонстрацию

### Шаг 1: Запустить все Batch2 тесты
```bash
cd C:\hapi-fhir
mvn test -Dtest=SystemProviderR4Test -pl hapi-fhir-jpaserver-test-r4 -f pom.xml
```

Результат: **34 tests passed, 2 skipped**

### Шаг 2: Показать конкретные тесты

#### Тест списка и деталей задач:
```bash
mvn test -Dtest=SystemProviderR4Test#testBatch2JobListAndDetailsOperations -pl hapi-fhir-jpaserver-test-r4 -f pom.xml
```

#### Тест pause/resume/cancel + audit:
```bash
mvn test -Dtest=SystemProviderR4Test#testBatch2PauseResumeCancelOperations -pl hapi-fhir-jpaserver-test-r4 -f pom.xml
```

#### Тест audit записей в БД:
```bash
mvn test -Dtest=SystemProviderR4Test#testBatch2AuditRecordsCreatedOnOperations -pl hapi-fhir-jpaserver-test-r4 -f pom.xml
```

#### Тест истории заданий (history endpoint):
```bash
mvn test -Dtest=SystemProviderR4Test#testBatch2JobHistoryEndpoint -pl hapi-fhir-jpaserver-test-r4 -f pom.xml
```

#### Тест уведомлений (notifications):
```bash
mvn test -Dtest=SystemProviderR4Test#testBatch2StatusChangeNotificationFired -pl hapi-fhir-jpaserver-test-r4 -f pom.xml
```

#### Тест пакетного обновления chunks:
```bash
mvn test -Dtest=SystemProviderR4Test#testBatchChunkStatusesUpdatedInBatch -pl hapi-fhir-jpaserver-test-r4 -f pom.xml
```

#### Тест кэширования статусов:
```bash
mvn test -Dtest=SystemProviderR4Test#testActiveJobStatusCache -pl hapi-fhir-jpaserver-test-r4 -f pom.xml
```

### Шаг 3: Показать исходный код

Открыть в IDE ключевые файлы:

1. **Audit Entity**: `hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/entity/Batch2JobAuditEntity.java`
2. **Audit Repository**: `hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/dao/data/IBatch2JobAuditRepository.java`
3. **Audit Service**: `hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/batch2/Batch2JobAuditSvcImpl.java`
4. **System Provider**: `hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/provider/JpaSystemProvider.java` (метод `batch2JobHistory`)
5. **Backoff + Audit + Notifications**: `hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/batch2/JpaJobPersistenceImpl.java` (методы `cancelInstance`, `pauseInstance`, `resumeInstance`, `fireStatusChangeEvent`)
6. **Graceful Shutdown**: `hapi-fhir-storage-batch2/src/main/java/ca/uhn/fhir/batch2/maintenance/JobMaintenanceServiceImpl.java`
7. **Cache Service**: `hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/batch2/cache/ActiveJobStatusCacheSvcImpl.java`
8. **Batch Chunk Update**: `hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/dao/data/IBatch2WorkChunkRepository.java` (метод `updateChunkStatusesInBatch`)
9. **Integration Tests**: `hapi-fhir-jpaserver-test-r4/src/test/java/ca/uhn/fhir/jpa/provider/r4/SystemProviderR4Test.java`

---

## Примеры REST API вызовов

### 1. Получить список заданий
```
POST [base]/$batch2-job-list
{
  "resourceType": "Parameters",
  "parameter": [
    {"name": "jobDefinitionId", "valueString": "my-export-job"},
    {"name": "status", "valueCode": "IN_PROGRESS"}
  ]
}
```

### 2. Получить детали задания
```
POST [base]/$batch2-job-get
{
  "resourceType": "Parameters",
  "parameter": [
    {"name": "jobId", "valueString": "abc-123"}
  ]
}
```

### 3. Получить статус chunks
```
POST [base]/$batch2-job-get-chunks
{
  "resourceType": "Parameters",
  "parameter": [
    {"name": "jobId", "valueString": "abc-123"}
  ]
}
```

### 4. Поставить задание на паузу
```
POST [base]/$batch2-job-pause
{
  "resourceType": "Parameters",
  "parameter": [
    {"name": "jobId", "valueString": "abc-123"}
  ]
}
```
**Response:** `{"resourceType":"Parameters","parameter":[{"name":"success","valueBoolean":true}]}`

### 5. Возобновить задание
```
POST [base]/$batch2-job-resume
{
  "resourceType": "Parameters",
  "parameter": [
    {"name": "jobId", "valueString": "abc-123"}
  ]
}
```

### 6. Отменить задание
```
POST [base]/$batch2-job-cancel
{
  "resourceType": "Parameters",
  "parameter": [
    {"name": "jobId", "valueString": "abc-123"}
  ]
}
```

### 7. Получить историю аудита задания
```
POST [base]/$hapi.fhir.batch2-job-history
{
  "resourceType": "Parameters",
  "parameter": [
    {"name": "jobId", "valueString": "abc-123"},
    {"name": "operation", "valueString": "pause"},
    {"name": "pageStart", "valueInteger": 0},
    {"name": "batchSize", "valueInteger": 20}
  ]
}
```
**Response:**
```json
{
  "resourceType": "Parameters",
  "parameter": [
    {"name": "total", "valueInteger": 3},
    {"name": "entry", "resource": {
      "instanceId": "abc-123",
      "definitionId": "my-export-job",
      "operation": "PAUSE",
      "priorStatus": "IN_PROGRESS",
      "newStatus": "PAUSED",
      "message": "Job instance successfully paused",
      "createTime": "2026-05-07T10:30:00Z"
    }}
  ]
}
```

---

## Подписка на уведомления (Interceptor)

```java
public class JobStatusNotificationInterceptor {
    @Hook(Pointcut.BATCH2_JOB_STATUS_CHANGE)
    public void onStatusChange(
            String theInstanceId,
            String theDefId,
            StatusEnum thePriorStatus,
            StatusEnum theNewStatus,
            String theMessage) {
        System.out.println("Job " + theInstanceId + " changed from "
                + thePriorStatus + " to " + theNewStatus + ": " + theMessage);
    }
}

// Регистрация
interceptorService.registerInterceptor(new JobStatusNotificationInterceptor());
```

---

## Кэширование статусов

```java
@Autowired
private IActiveJobStatusCacheSvc myCacheSvc;

// Получить кэшированный статус
StatusEnum status = myCacheSvc.getCachedStatus("abc-123");

// Обновить кэш
myCacheSvc.updateCachedStatus("abc-123", StatusEnum.IN_PROGRESS);

// Получить все активные статусы
Map<String, StatusEnum> allActive = myCacheSvc.getAllActiveStatuses();

// Очистить кэш
myCacheSvc.clear();
```

---

## Пакетное обновление chunks

```java
@Autowired
private IJobPersistence myJobPersistence;

Set<String> chunkIds = Set.of("chunk-1", "chunk-2", "chunk-3");
int updated = myJobPersistence.updateChunkStatusesInBatch(
        chunkIds,
        Set.of(WorkChunkStatusEnum.READY),
        WorkChunkStatusEnum.COMPLETED
);
// updated == 3
```

---

## Что показать преподавателю

1. **Все тесты проходят** — запустить `mvn test -Dtest=SystemProviderR4Test`
2. **Код работает** — тесты создают job instances, вызывают операции, проверяют статусы
3. **Audit endpoint** — метод `batch2JobHistory` в `JpaSystemProvider.java`
4. **Backoff** — формула задержки в `JpaJobPersistenceImpl.java`
5. **Graceful shutdown** — `@PreDestroy` методы с ожиданием завершения
6. **Notifications** — `BATCH2_JOB_STATUS_CHANGE` pointcut вызывается при cancel/pause/resume
7. **Cache** — `ActiveJobStatusCacheSvcImpl` с TTL и scheduled refresh
8. **Batch updates** — один SQL для множества chunks в `IBatch2WorkChunkRepository`
