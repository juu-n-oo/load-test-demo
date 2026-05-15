# 쇼핑사이트 백엔드 서버 요구사항

## 프로젝트 개요
- 멀티모듈 Spring Boot 프로젝트
- 쇼핑사이트 기능을 제공하는 RESTful API 서버
- Gatling을 사용한 부하 테스트 앱 포함

## 기술 스택
### Backend Module
- Java 17 (또는 21)
- Spring Boot 3.x
- Spring Data JPA
- QueryDSL
- PostgreSQL (또는 H2 for dev/test)
- Lombok
- Maven

### Gatling Module
- Gatling 3.x
- Java
- Maven

## 도메인 모델

### 1. User (사용자)
- ID (PK)
- Username (고유)
- Email (고유)
- Password (해시 처리)
- 생성일
- 수정일
- 계정 활성 여부

### 2. Product (상품)
- ID (PK)
- Name
- Description
- Price
- Stock (재고)
- Category
- 생성일
- 수정일

### 3. Order (주문)
- ID (PK)
- User (FK)
- 주문일
- 배송 상태 (PENDING, SHIPPED, DELIVERED)
- 총액
- 배송 주소
- 생성일
- 수정일

### 4. OrderItem (주문 상품)
- ID (PK)
- Order (FK)
- Product (FK)
- 수량
- 구매가 (주문 당시 가격)

### 5. Post (게시판 게시글)
- ID (PK)
- User (FK)
- Title
- Content
- View Count
- 생성일
- 수정일

### 6. Comment (덧글)
- ID (PK)
- Post (FK)
- User (FK)
- Content
- 생성일
- 수정일

## API 엔드포인트 (초안)

### User API
- POST /api/users - 사용자 가입
- GET /api/users/{id} - 사용자 조회
- PUT /api/users/{id} - 사용자 정보 수정
- DELETE /api/users/{id} - 사용자 삭제

### Product API
- GET /api/products - 상품 목록 조회 (페이징, 필터링)
- GET /api/products/{id} - 상품 상세 조회
- POST /api/products - 상품 등록 (관리자)
- PUT /api/products/{id} - 상품 수정 (관리자)
- DELETE /api/products/{id} - 상품 삭제 (관리자)

### Order API
- POST /api/orders - 주문 생성
- GET /api/orders - 내 주문 목록 조회
- GET /api/orders/{id} - 주문 상세 조회
- PUT /api/orders/{id}/status - 주문 상태 변경 (관리자)

### Post API
- GET /api/posts - 게시글 목록 조회 (페이징)
- GET /api/posts/{id} - 게시글 상세 조회
- POST /api/posts - 게시글 작성
- PUT /api/posts/{id} - 게시글 수정
- DELETE /api/posts/{id} - 게시글 삭제

### Comment API
- POST /api/posts/{postId}/comments - 댓글 작성
- PUT /api/comments/{id} - 댓글 수정
- DELETE /api/comments/{id} - 댓글 삭제

## Gatling 시뮬레이션 시나리오 (초안)

### 1. User Registration & Browse
- 사용자 가입
- 상품 목록 조회
- 상품 상세 조회

### 2. Order Flow
- 사용자 인증 (로그인 시뮬레이션)
- 상품 검색
- 주문 생성

### 3. Post & Comment
- 게시판 게시글 조회
- 댓글 작성

## 구현 우선순위

1. **Phase 1: 기본 구조**
   - 멀티모듈 Maven 설정
   - 데이터베이스 설정 (JPA, QueryDSL)
   - User, Product 엔티티 및 Repository

2. **Phase 2: 비즈니스 로직**
   - User, Product API 구현
   - Order, OrderItem API 구현
   - Post, Comment API 구현

3. **Phase 3: 고급 기능**
   - 검색/필터링 (QueryDSL 활용)
   - 페이징 처리
   - 에러 핸들링 및 검증

4. **Phase 4: 부하 테스트**
   - Gatling 시뮬레이션 작성
   - 성능 측정 및 분석

## 개발 환경
- IDE: IntelliJ IDEA 또는 VS Code
- Build: Maven
- VCS: Git
- 로컬 DB: H2 또는 PostgreSQL
