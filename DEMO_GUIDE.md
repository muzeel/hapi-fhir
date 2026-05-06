# Демонстрация Batch2 Monitoring Lab — HAPI FHIR
# Для преподавателя

## Что было реализовано

### 1. Exponential Backoff для повторов чанков
- Файл: `JpaJobPersistenceImpl.java`
- Формула: `delay = baseDelay * 2^(errorCount - 1)`, максимум 5 минут
- Поле `retryCount` в `Batch2WorkChunkEntity` для отслеживания попыток

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
- Позволяет подписаться на изменения статуса Batch2 jobs

### 5. REST Endpoint `$hapi.fhir.batch2-job-history`
- Файл: `JpaSystemProvider.java`
- Фильтрация по jobId, operation, датам, пагинация

---

## Как запустить демонстрацию

### Шаг 1: Запустить все Batch2 тесты
```bash
cd C:\hapi-fhir
mvn test -Dtest=SystemProviderR4Test -pl hapi-fhir-jpaserver-test-r4 -f pom.xml
```

Результат: **28 tests passed, 2 skipped**

### Шаг 2: Показать конкретные тесты

#### Тест списка и деталей задач:
```bash
mvn test -Dtest=SystemProviderR4Test#testBatch2JobListAndDetailsOperations -pl hapi-fhir-jpaserver-test-r4 -f pom.xml
```

#### Тест pause/resume/cancel:
```bash
mvn test -Dtest=SystemProviderR4Test#testBatch2PauseResumeCancelOperations -pl hapi-fhir-jpaserver-test-r4 -f pom.xml
```

#### Тест graceful shutdown (paused):
```bash
mvn test -Dtest=JobMaintenanceServiceImplTest#testMaintenancePass_whenInstancePaused_readyChunksAreNotQueued -pl hapi-fhir-storage-batch2 -f pom.xml
```

### Шаг 3: Показать исходный код

Открыть в IDE ключевые файлы:

1. **Audit Entity**: `hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/entity/Batch2JobAuditEntity.java`
2. **Audit Service**: `hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/batch2/Batch2JobAuditSvcImpl.java`
3. **System Provider**: `hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/provider/JpaSystemProvider.java` (метод `batch2JobHistory`)
4. **Backoff**: `hapi-fhir-jpaserver-base/src/main/java/ca/uhn/fhir/jpa/batch2/JpaJobPersistenceImpl.java` (метод `scheduleChunkForRetryLater`)
5. **Graceful Shutdown**: `hapi-fhir-storage-batch2/src/main/java/ca/uhn/fhir/batch2/maintenance/JobMaintenanceServiceImpl.java`

### Шаг 4: Показать таблицу аудита

В тестах audit записи создаются при вызове cancel/pause/resume.
Можно показать в `JpaJobPersistenceImpl.java` метод `logAuditEntry`.

---

## Что показать преподавателю

1. **Все тесты проходят** — запустить `mvn test -Dtest=SystemProviderR4Test`
2. **Код работает** — тесты создают job instances, вызывают операции, проверяют статусы
3. **Audit endpoint** — метод `batch2JobHistory` в `JpaSystemProvider.java`
4. **Backoff** — формула задержки в `JpaJobPersistenceImpl.java`
5. **Graceful shutdown** — `@PreDestroy` методы с ожиданием завершения
