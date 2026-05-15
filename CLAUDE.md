# load-test Project Guide

## 프로젝트 개요

**load-test**는 쇼핑사이트 백엔드 서버와 Gatling 기반 부하 테스트 앱으로 구성된 멀티모듈 Spring Boot 프로젝트입니다.

### 목표
- 쇼핑사이트의 기본 기능(사용자, 상품, 주문, 게시판, 댓글)을 제공하는 RESTful API 서버 구축
- Gatling을 활용한 성능 측정 및 부하 테스트

### 기술 스택
- **언어**: Java 25
- **Framework**: Spring Boot 3.4.5
- **Database**: H2 (개발), PostgreSQL (선택)
- **ORM**: Spring Data JPA + QueryDSL 5.1.0
- **부하 테스트**: Gatling 3.12.0
- **Build**: Gradle 8.14 (Kotlin DSL)
- **VCS**: Git

---

## 멀티모듈 구조

```
load-test/
├── backend/                    # Spring Boot 백엔드 서버
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/io/ten1010/loadtest/backend/
│   │   │   │   ├── domain/           # 도메인 모델 및 Repository
│   │   │   │   │   ├── user/         # User 엔티티, Repository
│   │   │   │   │   ├── product/      # Product 엔티티, Repository
│   │   │   │   │   ├── order/        # Order, OrderItem 엔티티, Repository
│   │   │   │   │   ├── post/         # Post 엔티티, Repository
│   │   │   │   │   └── comment/      # Comment 엔티티, Repository
│   │   │   │   ├── api/              # REST Controller + DTO + 예외
│   │   │   │   │   ├── user/         # UserController, UserRequest, UserResponse
│   │   │   │   │   ├── product/      # ProductController, ProductRequest, ProductResponse
│   │   │   │   │   ├── order/        # OrderController, OrderRequest, OrderResponse
│   │   │   │   │   ├── post/         # PostController, PostRequest, PostResponse
│   │   │   │   │   ├── comment/      # CommentController, CommentRequest, CommentResponse
│   │   │   │   │   ├── exception/    # ResourceNotFoundException, BusinessException
│   │   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │   ├── service/          # 비즈니스 로직 (UserService, ProductService 등)
│   │   │   │   └── config/           # JpaConfig (EnableJpaAuditing)
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/
│   └── build.gradle.kts
│
├── gatling-test/               # Gatling 부하 테스트
│   ├── src/
│   │   └── test/
│   │       └── java/io/ten1010/loadtest/gatling/
│   │           └── simulation/       # Gatling 시뮬레이션 (구현 예정)
│   └── build.gradle.kts
│
├── docs/
│   └── requirements.md          # 요구사항 정의 문서
│
├── build.gradle.kts             # 루트 Gradle 빌드 설정
├── settings.gradle.kts          # 멀티모듈 설정
├── gradlew / gradlew.bat        # Gradle Wrapper
├── .gitignore
└── CLAUDE.md                    # 이 파일
```

### 모듈별 책임

#### 1. backend (Spring Boot Application)
- **역할**: 쇼핑사이트 백엔드 API 서버 (포트 8080)
- **구현 완료 기능**:
  - User 관리 (가입, 조회, 수정, 비활성화)
  - Product 관리 (조회/페이징/필터링, 등록, 수정, 삭제)
  - Order 관리 (주문 생성+재고 차감, 조회, 상태 변경)
  - Post 관리 (게시글 CRUD, 조회수 자동 증가)
  - Comment 관리 (댓글 CRUD)
- **주요 의존성**:
  - spring-boot-starter-web
  - spring-boot-starter-data-jpa
  - querydsl-jpa:5.1.0:jakarta
  - h2 (런타임), postgresql (런타임)
  - lombok

#### 2. gatling-test (Gatling Simulation)
- **역할**: 백엔드 서버의 성능 및 부하 테스트
- **구현 예정 시뮬레이션**:
  - UserRegistrationSimulation: 사용자 가입 및 상품 조회
  - OrderFlowSimulation: 주문 프로세스 테스트
  - PostCommentSimulation: 게시판 및 댓글 기능 테스트
- **주요 의존성**:
  - io.gatling:gatling-app:3.12.0
  - io.gatling.highcharts:gatling-charts-highcharts:3.12.0

---

## 도메인 모델

### User (사용자)
```
User extends BaseEntity
├── id: Long (PK, AUTO_INCREMENT)
├── username: String (UNIQUE, max 50)
├── email: String (UNIQUE, max 100)
├── password: String
├── active: boolean (default true)
├── createdAt: LocalDateTime (자동 관리)
└── updatedAt: LocalDateTime (자동 관리)
```

### Product (상품)
```
Product extends BaseEntity
├── id: Long (PK)
├── name: String (max 200)
├── description: String (TEXT)
├── price: BigDecimal (precision 15, scale 2)
├── stock: Long
├── category: String (max 100)
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

### Order (주문) + OrderItem (주문 상품)
```
Order extends BaseEntity
├── id: Long (PK)
├── user: User (LAZY FK)
├── totalPrice: BigDecimal
├── shippingAddress: String (max 300)
├── status: OrderStatus [PENDING, SHIPPED, DELIVERED, CANCELLED]
├── items: List<OrderItem> (CASCADE ALL)
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime

OrderItem
├── id: Long (PK)
├── order: Order (LAZY FK)
├── product: Product (LAZY FK)
├── quantity: Long
└── purchasePrice: BigDecimal (주문 당시 가격 스냅샷)
```

### Post (게시글)
```
Post extends BaseEntity
├── id: Long (PK)
├── user: User (LAZY FK)
├── title: String (max 300)
├── content: String (TEXT)
├── viewCount: Long (default 0, 조회 시 자동 증가)
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

### Comment (댓글)
```
Comment extends BaseEntity
├── id: Long (PK)
├── post: Post (LAZY FK)
├── user: User (LAZY FK)
├── content: String (TEXT)
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

---

## API 엔드포인트

자세한 내용은 `docs/requirements.md`를 참조하세요.

### User API (`/api/users`)
- `POST /` - 사용자 가입 → 201 Created
- `GET /{id}` - 사용자 조회 → 200 OK
- `PUT /{id}` - 사용자 정보 수정 (email, password) → 200 OK
- `DELETE /{id}` - 사용자 비활성화 → 204 No Content

### Product API (`/api/products`)
- `GET /` - 상품 목록 조회 (페이징, ?category=, ?name=, ?minPrice=, ?maxPrice=, ?inStock=, ?sort=price,asc) → 200 OK
- `GET /{id}` - 상품 상세 조회 → 200 OK
- `POST /` - 상품 등록 → 201 Created
- `PUT /{id}` - 상품 수정 → 200 OK
- `DELETE /{id}` - 상품 삭제 → 204 No Content

### Order API (`/api/orders`)
- `POST /` - 주문 생성 (재고 차감 포함) → 201 Created
- `GET /` - 주문 목록 조회 (?userId=, ?status=) → 200 OK
- `GET /{id}` - 주문 상세 조회 (items fetch join) → 200 OK
- `PUT /{id}/status` - 주문 상태 변경 → 200 OK

### Post API (`/api/posts`)
- `GET /` - 게시글 목록 조회 (페이징, ?title=, ?content=, ?userId=, ?sort=viewCount,desc) → 200 OK
- `GET /{id}` - 게시글 상세 조회 (viewCount++) → 200 OK
- `POST /` - 게시글 작성 → 201 Created
- `PUT /{id}` - 게시글 수정 → 200 OK
- `DELETE /{id}` - 게시글 삭제 → 204 No Content

### Comment API
- `POST /api/posts/{postId}/comments` - 댓글 작성 → 201 Created
- `GET /api/posts/{postId}/comments` - 댓글 목록 조회 (페이징) → 200 OK
- `PUT /api/comments/{id}` - 댓글 수정 → 200 OK
- `DELETE /api/comments/{id}` - 댓글 삭제 → 204 No Content

### 에러 응답 형식
```json
{ "status": 404, "message": "...", "timestamp": "..." }
{ "status": 400, "message": "Validation failed", "errors": {"field": "msg"}, "timestamp": "..." }
```

---

## 개발 순서 및 우선순위

### Phase 1: 기본 구조 ✅ 완료
1. ✅ 프로젝트 구조 및 요구사항 정의
2. ✅ Gradle 멀티모듈 설정 (Java 25, Spring Boot 3.4.5)
3. ✅ 도메인 엔티티 구현 (User, Product, Order, OrderItem, Post, Comment)
4. ✅ JPA Repository 구현
5. ✅ Service 레이어 구현
6. ✅ REST Controller + DTO 구현
7. ✅ GlobalExceptionHandler

### Phase 2: QueryDSL 고급 기능 ✅ 완료
8. ✅ QueryDSL Q 클래스 생성 및 활용
9. ✅ 복잡한 검색/필터링 (QueryDSL 기반)
10. ✅ 동적 정렬

### Phase 3: 품질 향상
11. ⬜ 통합 테스트 작성 (@SpringBootTest)
12. ⬜ spring.jpa.open-in-view=false 설정
13. ⬜ 비밀번호 암호화 (BCrypt)

### Phase 4: Gatling 부하 테스트
14. ⬜ Gatling 시뮬레이션 작성
15. ⬜ 성능 측정 및 분석

---

## 개발 가이드라인

### 패키징 규칙
- Base Package: `io.ten1010.loadtest`
- Backend: `io.ten1010.loadtest.backend.*`
  - `domain.{도메인명}` - Entity + Repository
  - `api.{도메인명}` - Controller + Request/Response DTO
  - `service` - Service 클래스
  - `config` - 설정 클래스
- Gatling: `io.ten1010.loadtest.gatling.simulation.*`

### 코드 스타일
- Java 25 features 활용 (Records for DTO, Pattern Matching 등)
- Lombok 활용 (@Getter, @Builder, @RequiredArgsConstructor)
- Entity는 `@NoArgsConstructor(access = PROTECTED)` + `@Builder`
- Service는 `@Transactional(readOnly = true)` 기본 + 쓰기 메서드에 `@Transactional` 명시
- Lazy Loading 기본, 필요한 경우 fetch join

### 데이터베이스
- 개발 환경: H2 메모리 DB (application.yml: `jdbc:h2:mem:testdb`)
- H2 콘솔: http://localhost:8080/h2-console
- 프로덕션: PostgreSQL (권장)
- DDL: `spring.jpa.hibernate.ddl-auto=create-drop` (개발)

### Git 커밋 규칙
```
[backend] {도메인}: {설명}
[gatling] {시뮬레이션}: {설명}
[docs] 요구사항 업데이트
[build] Gradle 설정 변경
```

---

## 로컬 개발 환경 설정

### 필수 요소
- Java 25 JDK
- Gradle (Wrapper 포함, `./gradlew` 사용 권장)
- Git

### 프로젝트 빌드
```bash
cd ~/workspace/load-test
./gradlew build
```

### backend 실행
```bash
./gradlew :backend:bootRun
# 또는
./gradlew :backend:bootRun --args='--server.port=8081'
```

### Gatling 테스트 실행
```bash
./gradlew :gatling-test:gatlingRun
```

### QueryDSL Q 클래스 생성 (엔티티 변경 시)
```bash
./gradlew :backend:compileJava
# → backend/build/generated/querydsl/ 에 Q클래스 생성됨
```

---

## 주의사항

1. **Lombok + Java 25**: `sun.misc.Unsafe` 관련 경고가 뜨지만 기능은 정상 동작함
2. **QueryDSL Q 클래스**: 엔티티 추가/수정 후 반드시 `./gradlew :backend:compileJava` 실행
3. **JPA Cascade**: OrderItem은 Order에 CASCADE ALL, orphanRemoval=true 설정됨
4. **N+1 Problem**: Order 조회 시 `findByIdWithItems`로 fetch join 사용
5. **재고 차감**: Order 생성 시 Product.decreaseStock() 호출, 재고 부족 시 BusinessException

---

**Last Updated**: 2026-05-16
**Status**: Phase 2 완료 (QueryDSL 기반 동적 검색/필터링/정렬 구현)
