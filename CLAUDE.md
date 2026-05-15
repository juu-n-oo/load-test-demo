# load-test Project Guide

## 프로젝트 개요

**load-test**는 쇼핑사이트 백엔드 서버와 Gatling 기반 부하 테스트 앱으로 구성된 멀티모듈 Spring Boot 프로젝트입니다.

### 목표
- 쇼핑사이트의 기본 기능(사용자, 상품, 주문, 게시판, 댓글)을 제공하는 RESTful API 서버 구축
- Gatling을 활용한 성능 측정 및 부하 테스트

### 기술 스택
- **언어**: Java 17
- **Framework**: Spring Boot 3.2.5
- **Database**: H2 (개발), PostgreSQL (선택)
- **ORM**: Spring Data JPA + QueryDSL
- **부하 테스트**: Gatling 3.10.3
- **Build**: Maven
- **VCS**: Git

---

## 멀티모듈 구조

```
load-test/
├── backend/                    # Spring Boot 백엔드 서버
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/io/ten1010/loadtest/backend/
│   │   │   │   ├── domain/     # 도메인 모델 (User, Product, Order 등)
│   │   │   │   ├── api/        # REST Controller
│   │   │   │   ├── service/    # 비즈니스 로직
│   │   │   │   └── config/     # 설정 클래스
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/         # DB 초기화 스크립트
│   │   └── test/
│   └── pom.xml
│
├── gatling-test/               # Gatling 부하 테스트
│   ├── src/
│   │   └── test/
│   │       ├── java/io/ten1010/loadtest/gatling/
│   │       │   └── simulation/  # Gatling 시뮬레이션
│   │       └── resources/
│   └── pom.xml
│
├── docs/
│   └── requirements.md          # 요구사항 정의 문서
│
├── pom.xml                      # 루트 pom (멀티모듈 관리)
├── .gitignore
└── CLAUDE.md                    # 이 파일
```

### 모듈별 책임

#### 1. backend (Spring Boot Application)
- **역할**: 쇼핑사이트 백엔드 API 서버
- **주요 기능**:
  - User 관리 (가입, 조회, 수정, 삭제)
  - Product 관리 (조회, 등록, 수정, 삭제)
  - Order 관리 (주문 생성, 조회, 상태 변경)
  - Post 관리 (게시글 CRUD)
  - Comment 관리 (댓글 CRUD)
- **주요 의존성**:
  - spring-boot-starter-web
  - spring-boot-starter-data-jpa
  - querydsl-jpa
  - h2 (또는 postgresql)
  - lombok

#### 2. gatling-test (Gatling Simulation)
- **역할**: 백엔드 서버의 성능 및 부하 테스트
- **주요 시뮬레이션**:
  - UserRegistrationSimulation: 사용자 가입 및 상품 조회
  - OrderFlowSimulation: 주문 프로세스 테스트
  - PostCommentSimulation: 게시판 및 댓글 기능 테스트
- **주요 의존성**:
  - io.gatling:gatling-app
  - io.gatling.highcharts:gatling-charts-highcharts

---

## 도메인 모델

### User (사용자)
```
User
├── id: Long (PK)
├── username: String (UNIQUE)
├── email: String (UNIQUE)
├── password: String (암호화)
├── active: Boolean
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

### Product (상품)
```
Product
├── id: Long (PK)
├── name: String
├── description: String
├── price: BigDecimal
├── stock: Long
├── category: String
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

### Order (주문)
```
Order
├── id: Long (PK)
├── user: User (FK)
├── totalPrice: BigDecimal
├── shippingAddress: String
├── status: OrderStatus (PENDING, SHIPPED, DELIVERED)
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

### OrderItem (주문 상품)
```
OrderItem
├── id: Long (PK)
├── order: Order (FK)
├── product: Product (FK)
├── quantity: Long
└── purchasePrice: BigDecimal
```

### Post (게시글)
```
Post
├── id: Long (PK)
├── user: User (FK)
├── title: String
├── content: String
├── viewCount: Long
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

### Comment (댓글)
```
Comment
├── id: Long (PK)
├── post: Post (FK)
├── user: User (FK)
├── content: String
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

---

## API 엔드포인트

자세한 내용은 `docs/requirements.md`를 참조하세요.

### User API
- `POST /api/users` - 사용자 가입
- `GET /api/users/{id}` - 사용자 조회
- `PUT /api/users/{id}` - 사용자 정보 수정
- `DELETE /api/users/{id}` - 사용자 삭제

### Product API
- `GET /api/products` - 상품 목록 조회 (페이징, 필터링)
- `GET /api/products/{id}` - 상품 상세 조회
- `POST /api/products` - 상품 등록
- `PUT /api/products/{id}` - 상품 수정
- `DELETE /api/products/{id}` - 상품 삭제

### Order API
- `POST /api/orders` - 주문 생성
- `GET /api/orders` - 내 주문 목록 조회
- `GET /api/orders/{id}` - 주문 상세 조회
- `PUT /api/orders/{id}/status` - 주문 상태 변경

### Post API
- `GET /api/posts` - 게시글 목록 조회 (페이징)
- `GET /api/posts/{id}` - 게시글 상세 조회
- `POST /api/posts` - 게시글 작성
- `PUT /api/posts/{id}` - 게시글 수정
- `DELETE /api/posts/{id}` - 게시글 삭제

### Comment API
- `POST /api/posts/{postId}/comments` - 댓글 작성
- `PUT /api/comments/{id}` - 댓글 수정
- `DELETE /api/comments/{id}` - 댓글 삭제

---

## 개발 순서 및 우선순위

### Phase 1: 기본 구조 (backend 모듈)
1. ✅ 프로젝트 구조 및 요구사항 정의
2. ⬜ backend 모듈의 pom.xml 생성
3. ⬜ 데이터베이스 설정 (JPA, QueryDSL)
4. ⬜ User, Product 엔티티 및 Repository 구현
5. ⬜ User, Product 기본 API 구현 (GET, POST)

### Phase 2: 비즈니스 로직
6. ⬜ Order, OrderItem 엔티티 및 Repository
7. ⬜ Order API 구현
8. ⬜ Post, Comment 엔티티 및 Repository
9. ⬜ Post, Comment API 구현

### Phase 3: 고급 기능
10. ⬜ 검색/필터링 (QueryDSL 활용)
11. ⬜ 페이징 처리
12. ⬜ 에러 핸들링 및 입력 검증
13. ⬜ 통합 테스트

### Phase 4: 부하 테스트
14. ⬜ gatling-test 모듈 설정
15. ⬜ Gatling 시뮬레이션 작성
16. ⬜ 성능 측정 및 분석
17. ⬜ 결과 리포트 작성

---

## 개발 가이드라인

### 패키징 규칙
- Base Package: `io.ten1010.loadtest`
- Backend: `io.ten1010.loadtest.backend.*`
- Gatling: `io.ten1010.loadtest.gatling.*`

### 코드 스타일
- Java 17 features 활용 (Records, Sealed Classes 등)
- Lombok 활용하여 보일러플레이트 코드 최소화
- JPA/Hibernate 베스트 프랙티스 준수
- QueryDSL을 통한 타입 안전한 쿼리 작성

### 테스트
- 각 모듈의 로직에 대해 JUnit 5 + Mockito 테스트 작성
- 통합 테스트는 @SpringBootTest 사용
- gatling-test는 성능 테스트 시뮬레이션만 포함

### 데이터베이스
- 개발 환경: H2 (메모리 또는 파일)
- 프로덕션: PostgreSQL (권장)
- 마이그레이션: Flyway 또는 Liquibase (선택)

### Git 커밋 규칙
```
[backend] 기능 또는 모듈 이름: 간단한 설명
[gatling] 시뮬레이션 추가: 간단한 설명
[docs] 요구사항 업데이트
[ci] CI/CD 설정
```

---

## 로컬 개발 환경 설정

### 필수 요소
- Java 17 JDK 설치
- Maven 3.8.0 이상
- Git

### 프로젝트 빌드
```bash
cd ~/workspace/load-test
mvn clean install
```

### backend 실행
```bash
cd backend
mvn spring-boot:run
```

### Gatling 테스트 실행
```bash
cd gatling-test
mvn gatling:test
```

---

## 주의사항

1. **QueryDSL Q 클래스 생성**: Maven build 시 `mvn clean compile`로 QueryDSL Q 클래스를 자동 생성
2. **JPA Cascade**: 관계 설정 시 cascade 설정 신중히 (특히 DELETE)
3. **N+1 Problem**: JPQL이나 QueryDSL 쿼리에서 fetch join 활용
4. **데이터 검증**: 입력 데이터는 API 레이어에서 검증 (Jakarta Validation)

---

## 다음 단계

1. `backend` 모듈의 pom.xml 생성
2. 엔티티 클래스 구현 (User, Product 시작)
3. Repository 인터페이스 정의
4. REST Controller 구현
5. 위의 순서로 진행 (docs/requirements.md 참조)

---

**Last Updated**: 2026-05-15  
**Status**: 초기 설정 단계 (Phase 1 준비 중)
