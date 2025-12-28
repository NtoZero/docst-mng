# P6Spy SQL 로깅 설정 가이드

> 작성일: 2025-12-28
> Spring Boot 3.5.8 (Hibernate 6) 기준

## 문제 상황

기본 Hibernate 로깅 설정으로는 Prepared Statement의 `?` 플레이스홀더만 출력되어, SQL을 복사해서 콘솔에 바로 실행할 수 없는 문제가 있었습니다.

**기존 로그 예시:**
```sql
DEBUG o.h.SQL - select u1_0.id, u1_0.email from dm_user u1_0 where u1_0.email=?
TRACE o.h.o.j.bind - binding parameter [1] as [VARCHAR] - [user@example.com]
```

**요구사항:**
- 파라미터가 실제 값으로 치환된 완전한 SQL 쿼리 출력
- 한 줄로 출력되어 드래그 후 SQL 콘솔에 바로 실행 가능

---

## 해결 방법: P6Spy 적용

### 2025 베스트 프랙티스 조사 결과

Spring Data JPA에서 실행 가능한 완전한 SQL을 로깅하는 방법을 조사한 결과:

1. **표준 Hibernate 로깅**: Prepared Statement와 파라미터가 별도 로그로 출력 ❌
2. **P6Spy**: 파라미터가 바인딩된 완전한 SQL을 한 줄로 출력 ✅
3. **Datasource-Proxy**: 상세 통계는 좋으나 파라미터가 별도 표시 △

👉 **SQL 콘솔 복사-붙여넣기 용도로는 P6Spy가 최적**

---

## 적용 단계

### 1. 의존성 추가

**backend/build.gradle.kts**
```kotlin
dependencies {
  // ... 기존 의존성

  // SQL Logging with actual parameter values (P6Spy)
  implementation("com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.11.0")
}
```

**버전 정보:**
- Spring Boot 3.x: `1.11.0` (최신 안정 버전)
- Spring Boot 2.x: `1.8.1` (최종 호환 버전)

### 2. application.yml 설정

**backend/src/main/resources/application.yml**
```yaml
logging:
  level:
    com.docst: DEBUG
    # Hibernate logging (replaced by P6Spy)
    # org.hibernate.SQL: DEBUG
    # org.hibernate.orm.jdbc.bind: TRACE

# P6Spy Configuration
decorator:
  datasource:
    p6spy:
      enable-logging: true
      multiline: false  # 한 줄 출력 (복사-붙여넣기 용이)
      logging: slf4j    # SLF4J 로깅 사용
```

**주요 설정 옵션:**
- `enable-logging`: P6Spy 로깅 활성화
- `multiline`: `false`로 설정하면 한 줄로 출력 (기본값: `true`)
- `logging`: `slf4j` (권장) 또는 `sysout`, `file`

### 3. 빌드 및 실행

```bash
# 의존성 다운로드 및 빌드
cd backend && ./gradlew build -x test

# 애플리케이션 실행
./gradlew bootRun
```

---

## 결과 확인

### 출력 예시

**변경 전 (Hibernate 기본 로깅):**
```
DEBUG o.h.SQL - select u1_0.id, u1_0.created_at, u1_0.email
                from dm_user u1_0
                where u1_0.email=?
TRACE o.h.o.j.bind - binding parameter [1] as [VARCHAR] - [user@example.com]
```
❌ 여러 줄로 나뉘어 있고, 파라미터가 별도로 표시됨

**변경 후 (P6Spy):**
```
INFO p6spy - select u1_0.id, u1_0.created_at, u1_0.email from dm_user u1_0 where u1_0.email='user@example.com'
```
✅ **한 줄로 파라미터가 바인딩되어 출력! 드래그해서 SQL 콘솔에 바로 실행 가능**

### 실행 시간 포함

P6Spy는 기본적으로 쿼리 실행 시간도 함께 출력합니다:
```
INFO p6spy - #1735356000 | took 3ms | statement | connection 0 |
select u1_0.id, u1_0.email from dm_user u1_0 where u1_0.email='user@example.com'
```

---

## 고급 설정 (선택 사항)

필요시 `spy.properties` 파일을 생성하여 더 세밀한 제어가 가능합니다.

**backend/src/main/resources/spy.properties**
```properties
# 로그 메시지 포맷 커스터마이징
logMessageFormat=com.p6spy.engine.spy.appender.CustomLineFormat
customLogMessageFormat=%(executionTime)ms | %(category) | %(sql)

# 느린 쿼리만 로깅 (밀리초)
executionThreshold=1000

# 파일로 로깅 (선택)
appender=com.p6spy.engine.spy.appender.FileLogger
logfile=logs/spy.log

# 특정 쿼리 제외
excludecategories=info,debug,result,resultset,batch

# 날짜 포맷
dateformat=yyyy-MM-dd HH:mm:ss
```

**주요 설정 옵션:**

| 옵션 | 설명 | 기본값 |
|------|------|--------|
| `logMessageFormat` | 로그 포맷 클래스 | `SingleLineFormat` |
| `customLogMessageFormat` | 커스텀 포맷 문자열 | - |
| `executionThreshold` | 로깅 임계값 (ms) | `0` (모든 쿼리) |
| `excludecategories` | 제외할 카테고리 | - |
| `appender` | 로그 출력 대상 | `Slf4JLogger` |

**포맷 플레이스홀더:**
- `%(executionTime)`: 실행 시간 (ms)
- `%(category)`: 카테고리 (statement, commit, rollback 등)
- `%(sql)`: 실행된 SQL (파라미터 바인딩 완료)
- `%(connectionId)`: 커넥션 ID

---

## P6Spy vs Datasource-Proxy 비교

| 특징 | P6Spy | Datasource-Proxy |
|------|-------|------------------|
| **복사-붙여넣기 가능한 SQL** | ✅ 한 줄로 출력 | ❌ 파라미터 별도 표시 |
| **실행 시간 측정** | ✅ | ✅ |
| **N+1 쿼리 탐지** | ❌ | ✅ (카운터 제공) |
| **배치 쿼리 시각화** | ❌ | ✅ |
| **로깅 레벨** | INFO | DEBUG/WARN |
| **설정 복잡도** | 낮음 | 중간 |
| **성능 오버헤드** | 낮음 (~5%) | 낮음 (~5%) |
| **적합 용도** | 개발 디버깅, SQL 확인 | 성능 분석, N+1 탐지 |

**선택 기준:**
- 🎯 **SQL 콘솔 복사-붙여넣기**: P6Spy
- 📊 **N+1 쿼리 탐지/성능 분석**: Datasource-Proxy
- 🔧 **둘 다 필요**: 둘 다 적용 가능 (동일 라이브러리에서 제공)

---

## Hibernate 버전별 기본 로깅 설정

참고로, P6Spy 없이 기본 Hibernate 로깅만 사용하는 경우:

### Hibernate 6 (Spring Boot 3.x)

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG                # SQL 쿼리
    org.hibernate.orm.jdbc.bind: TRACE      # 파라미터 바인딩
```

### Hibernate 5 (Spring Boot 2.x)

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG                      # SQL 쿼리
    org.hibernate.type.descriptor.sql: TRACE      # 파라미터 바인딩
```

**주의사항:**
- ❌ `spring.jpa.show-sql=true` 사용 금지
  - `System.out`으로 직접 출력 (로깅 프레임워크 우회)
  - 로그 레벨 제어 불가
  - 성능 저하

---

## 프로덕션 고려사항

### 1. 프로파일별 설정

개발 환경에서만 P6Spy를 활성화하려면:

```yaml
# application.yml
---
spring:
  config:
    activate:
      on-profile: dev

decorator:
  datasource:
    p6spy:
      enable-logging: true
      multiline: false

---
spring:
  config:
    activate:
      on-profile: prod

# 프로덕션에서는 P6Spy 비활성화
decorator:
  datasource:
    p6spy:
      enable-logging: false
```

### 2. 조건부 의존성

프로덕션에서 P6Spy를 완전히 제외하려면:

```kotlin
dependencies {
  // 개발 환경에서만 포함
  developmentOnly("com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.11.0")
}
```

### 3. 성능 영향

- P6Spy 오버헤드: ~5% (공식 벤치마크)
- 로깅 I/O가 더 큰 영향을 미칠 수 있음
- 프로덕션에서는 `executionThreshold`를 설정하여 느린 쿼리만 로깅 권장

---

## 문제 해결

### 로그가 출력되지 않는 경우

1. **Gradle 의존성 확인**
   ```bash
   ./gradlew dependencies | grep p6spy
   ```

2. **Auto-configuration 확인**
   ```yaml
   logging:
     level:
       com.github.gavlyukovskiy: DEBUG
   ```

   애플리케이션 시작 시 다음과 같은 로그가 보여야 함:
   ```
   INFO c.g.g.b.j.d.DataSourceDecoratorAutoConfiguration -
   Decorating DataSource with P6Spy
   ```

3. **로그 레벨 확인**
   ```yaml
   logging:
     level:
       p6spy: INFO  # 최소 INFO 레벨 필요
   ```

### 멀티라인 포맷이 필요한 경우

가독성을 위해 여러 줄로 출력하려면:

```yaml
decorator:
  datasource:
    p6spy:
      multiline: true
```

또는 `spy.properties`:
```properties
logMessageFormat=com.p6spy.engine.spy.appender.MultiLineFormat
```

---

## 참고 자료

### 공식 문서
- [P6Spy Documentation](https://p6spy.readthedocs.io/)
- [spring-boot-data-source-decorator GitHub](https://github.com/gavlyukovskiy/spring-boot-data-source-decorator)

### 튜토리얼 및 가이드
- [Intercept SQL Logging with P6Spy | Baeldung](https://www.baeldung.com/java-p6spy-intercept-sql-logging)
- [The best way to log SQL statements with Spring Boot - Vlad Mihalcea](https://vladmihalcea.com/log-sql-spring-boot/)
- [Spring Boot + P6Spy for SQL Debugging](https://vulinhjava.io.vn/blog/spring-boot-p6spy/)
- [Show Hibernate/JPA SQL Statements in Spring Boot | Baeldung](https://www.baeldung.com/sql-logging-spring-boot/)

### 관련 자료
- [Datasource-Proxy User Guide](https://jdbc-observations.github.io/datasource-proxy/docs/snapshot/user-guide/)
- [The best way to log SQL statements with JDBC - Vlad Mihalcea](https://vladmihalcea.com/the-best-way-to-log-jdbc-statements/)

---

## 체크리스트

설정 완료 후 다음 항목들을 확인하세요:

- [ ] `build.gradle.kts`에 `p6spy-spring-boot-starter` 의존성 추가
- [ ] `application.yml`에 P6Spy 설정 추가
- [ ] 애플리케이션 재시작 후 로그 확인
- [ ] SQL 쿼리가 한 줄로 출력되는지 확인
- [ ] 파라미터가 실제 값으로 치환되어 있는지 확인
- [ ] SQL을 복사해서 콘솔에 실행 가능한지 테스트
- [ ] 프로덕션 환경 설정 검토 (비활성화 또는 임계값 설정)

---

## 결론

P6Spy를 적용하면:
- ✅ 파라미터가 바인딩된 완전한 SQL 쿼리 확인 가능
- ✅ 한 줄 포맷으로 복사-붙여넣기 용이
- ✅ 쿼리 실행 시간 자동 측정
- ✅ 최소한의 설정으로 즉시 적용 가능
- ✅ 낮은 성능 오버헤드 (~5%)

개발 과정에서 SQL 디버깅과 성능 분석에 매우 유용한 도구입니다.