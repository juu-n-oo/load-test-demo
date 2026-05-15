package io.ten1010.loadtest.gatling.simulation;

// ============================================================
// [Gatling Java DSL 핵심 임포트]
//
// io.gatling.javaapi.core.*
//   - Simulation, ScenarioBuilder, FeederBuilder, Session 등
//     시나리오·시뮬레이션 구성에 필요한 모든 핵심 타입
//
// io.gatling.javaapi.http.*
//   - HttpProtocolBuilder, HttpRequestActionBuilder 등
//     HTTP 프로토콜 관련 타입
//
// CoreDsl.* / HttpDsl.*  (static import)
//   - scenario(), exec(), pause(), setUp(), rampUsers() 등
//     DSL 메서드를 클래스 접두사 없이 바로 쓸 수 있게 함
//     예) CoreDsl.scenario(...)  →  scenario(...)
// ============================================================
import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * ============================================================
 * [Simulation 클래스]
 *
 * 모든 Gatling 시뮬레이션은 반드시 Simulation 을 상속받아야 한다.
 * 한 파일(클래스) = 하나의 독립된 테스트 시나리오 묶음.
 *
 * 클래스 내부에서 세 가지를 선언하면 된다:
 *   1) HttpProtocolBuilder  — 공통 HTTP 설정 (baseUrl, 헤더 등)
 *   2) ScenarioBuilder      — 가상 사용자가 실행할 행동 시퀀스
 *   3) setUp { ... }        — 부하 주입 방식 + 성공/실패 기준 (assertion)
 * ============================================================
 *
 * 이 시뮬레이션의 목표:
 *   사용자 가입·조회·수정, 상품 목록 다중 조건 검색의 동시 처리 성능 측정
 *
 * 부하 패턴:
 *   - nothingFor(2s)               : 서버 워밍업 대기
 *   - rampUsers(50).during(30s)    : 30초에 걸쳐 50명 점진 증가
 *   - constantUsersPerSec(5) 30s   : 30초간 초당 5명씩 지속 유입
 */
public class UserRegistrationSimulation extends Simulation {

    private static final String BASE_URL = "http://localhost:8080";

    // ============================================================
    // [HttpProtocolBuilder — 공통 HTTP 프로토콜 설정]
    //
    // 모든 요청에 공통으로 적용되는 설정을 한 곳에 모아둔다.
    // setUp() 에서 .protocols(httpProtocol) 로 연결하면 적용된다.
    //
    // .baseUrl()
    //   - 이후 모든 요청의 URL 앞에 자동으로 붙는 기본 주소.
    //     exec(http("...").get("/api/users"))  →  실제 요청: GET http://localhost:8080/api/users
    //
    // .contentTypeHeader("application/json")
    //   - 모든 요청에 "Content-Type: application/json" 헤더 추가.
    //     POST/PUT body 를 JSON 으로 보낼 때 필수.
    //
    // .acceptHeader("application/json")
    //   - "Accept: application/json" 헤더 추가.
    //     서버에게 "JSON 으로 응답해 달라"고 요청.
    //
    // .shareConnections()
    //   - 가상 사용자들이 TCP 커넥션 풀을 공유.
    //     실제 브라우저처럼 커넥션을 재활용해 현실적인 시나리오를 만든다.
    //     (기본값은 가상 사용자마다 독립 커넥션)
    // ============================================================
    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .shareConnections();

    // ============================================================
    // [ScenarioBuilder — 시나리오 정의]
    //
    // scenario("이름")
    //   - 보고서(HTML 리포트)에 표시될 시나리오 이름.
    //   - .exec(...)를 체인으로 연결해 순차적 행동을 정의한다.
    //
    // 가상 사용자(Virtual User, VU)는 이 시나리오를 처음부터 끝까지
    // 한 번 실행한 뒤 종료된다(Open Model 기준).
    // ============================================================
    ScenarioBuilder userLifecycle = scenario("사용자 생애주기")

            // ============================================================
            // [Session — 가상 사용자 전용 저장소]
            //
            // exec(session -> { ... return session.set("key", value); })
            //   - 람다 형태의 exec 는 HTTP 요청이 아닌 Java 코드를 실행한다.
            //   - Session 은 가상 사용자 1명에게 귀속된 Map 형태의 저장소다.
            //   - session.set("key", value) 로 값을 저장하면
            //     이후 요청 Body, URL, 검증에서 #{key} 로 꺼내 쓸 수 있다.
            //   - Session 은 불변(immutable)이므로 반드시 새 Session 을 반환해야 한다.
            //
            // [왜 UUID를 쓰는가?]
            //   - 부하 테스트는 같은 시나리오를 수십~수백 명이 동시에 실행한다.
            //   - username / email 이 중복되면 가입 API가 409 충돌로 실패한다.
            //   - UUID 앞 10자리로 충분한 고유성을 확보한다.
            // ============================================================
            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                return session
                        .set("username", "user_" + uid)
                        .set("email", "user_" + uid + "@loadtest.io")
                        .set("password", "pass_" + uid);
            })

            // ============================================================
            // [HTTP 요청 — exec(http("이름").메서드(경로)...)]
            //
            // http("이름")
            //   - 보고서에서 이 요청을 식별하는 레이블. 의미 있게 지어야
            //     나중에 어느 API가 느린지 한눈에 파악할 수 있다.
            //
            // .post("/api/users")
            //   - HTTP POST 요청. baseUrl 이 자동으로 앞에 붙는다.
            //
            // .body(StringBody("..."))
            //   - 요청 Body를 문자열로 지정한다.
            //   - #{username} 처럼 #{변수명} 으로 Session 값을 참조한다.
            //     (Gatling EL = Expression Language)
            //   - Java 텍스트 블록(""" """)으로 가독성 좋게 JSON 작성 가능.
            //
            // .check(status().is(201))
            //   - 응답 HTTP 상태 코드가 201 인지 검증.
            //   - 실패하면 이 요청은 'KO'로 기록되고 시나리오가 중단된다.
            //
            // .check(jsonPath("$.id").saveAs("userId"))
            //   - 응답 JSON 에서 $.id 값을 추출해 Session 에 "userId" 로 저장.
            //   - 이후 요청에서 #{userId} 로 참조할 수 있다.
            //   - jsonPath 는 JsonPath 라이브러리 문법을 따른다.
            //     예) $.items[0].productId  →  첫 번째 items 요소의 productId
            //
            // .check(jsonPath("$.username").is("#{username}"))
            //   - 응답 JSON 의 username 필드가 Session 의 #{username} 과 일치하는지 검증.
            //   - is() 는 equals 검증, exists() 는 필드 존재 여부만 확인한다.
            // ============================================================
            .exec(http("사용자 가입")
                    .post("/api/users")
                    .body(StringBody("""
                            {
                              "username": "#{username}",
                              "email": "#{email}",
                              "password": "#{password}"
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("userId"))       // 응답의 id를 세션에 저장
                    .check(jsonPath("$.username").is("#{username}"))) // 요청한 username과 응답 일치 확인

            // ============================================================
            // [pause() — 생각 시간(Think Time) 시뮬레이션]
            //
            // pause(min, max)
            //   - 실제 사용자는 클릭 후 즉시 다음 행동을 하지 않는다.
            //   - 최솟값~최댓값 사이 무작위 시간 동안 대기해 현실적인 부하를 모사한다.
            //   - 대기 시간이 없으면 모든 요청이 연속 발사되어 비현실적인 부하 패턴이 된다.
            //   - 단위: Duration.ofMillis(ms) 또는 Duration.ofSeconds(s)
            // ============================================================
            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            // ============================================================
            // [이전 요청에서 저장한 세션 변수를 URL에 사용]
            //
            // .get("/api/users/#{userId}")
            //   - #{userId} 는 위에서 saveAs("userId") 로 저장한 값으로 치환된다.
            //   - 예) userId = 42  →  GET /api/users/42
            //
            // .check(jsonPath("$.id").is("#{userId}"))
            //   - 응답의 id 가 요청에 사용한 userId 와 동일한지 교차 검증한다.
            //   - is() 에도 #{} EL 표현식을 쓸 수 있다.
            // ============================================================
            .exec(http("사용자 조회")
                    .get("/api/users/#{userId}")
                    .check(status().is(200))
                    .check(jsonPath("$.id").is("#{userId}")))

            .pause(Duration.ofMillis(300), Duration.ofMillis(700))

            // ============================================================
            // [쿼리 파라미터 — .queryParam()]
            //
            // .queryParam("key", "value")
            //   - URL 에 ?key=value 를 추가한다.
            //   - 여러 번 호출하면 &로 연결된다.
            //     예) ?page=0&size=20
            //
            // .check(jsonPath("$.totalElements").exists())
            //   - exists() : 해당 필드가 JSON 에 존재하기만 하면 통과.
            //     값이 무엇인지 상관없이 필드 자체의 존재 여부만 확인한다.
            // ============================================================
            .exec(http("상품 목록 조회")
                    .get("/api/products")
                    .queryParam("page", "0")
                    .queryParam("size", "20")
                    .check(status().is(200))
                    .check(jsonPath("$.totalElements").exists()))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // QueryDSL 기반 다중 조건 검색 — 카테고리 + 재고 있음 + 가격 오름차순 정렬
            .exec(http("상품 검색 - 카테고리")
                    .get("/api/products")
                    .queryParam("category", "electronics")
                    .queryParam("inStock", "true")
                    .queryParam("sort", "price,asc") // Spring Data Pageable 정렬 규칙: {필드},{방향}
                    .check(status().is(200)))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 가격 범위 필터 + 내림차순 정렬
            .exec(http("상품 검색 - 가격 범위")
                    .get("/api/products")
                    .queryParam("minPrice", "10000")
                    .queryParam("maxPrice", "500000")
                    .queryParam("sort", "price,desc")
                    .check(status().is(200)))

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            // ============================================================
            // [연속 exec() — 세션 변수 업데이트 후 즉시 다음 요청에 사용]
            //
            // exec(session -> ...) 와 exec(http(...)) 를 연속으로 체인하면
            // 첫 번째 exec 에서 세션에 값을 저장하고,
            // 바로 다음 exec 에서 그 값을 사용할 수 있다.
            // ============================================================
            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                return session.set("newEmail", "updated_" + uid + "@loadtest.io");
            })
            .exec(http("사용자 정보 수정")
                    .put("/api/users/#{userId}")
                    .body(StringBody("""
                            {
                              "email": "#{newEmail}",
                              "password": "newpass123"
                            }
                            """))
                    .check(status().is(200))
                    .check(jsonPath("$.email").is("#{newEmail}")));

    // ============================================================
    // [setUp { } — 인스턴스 초기화 블록: 시뮬레이션의 핵심 설정]
    //
    // Java 의 인스턴스 초기화 블록 { } 안에 setUp() 을 호출한다.
    // Gatling 이 Simulation 을 로드할 때 이 블록이 실행되어 시뮬레이션을 등록한다.
    //
    // setUp(시나리오1.injectOpen(...), 시나리오2.injectOpen(...), ...)
    //   - 하나의 setUp 에 여러 시나리오를 동시에 등록할 수 있다.
    //   - 각 시나리오는 독립적으로 부하를 주입받아 동시에 실행된다.
    //
    // .protocols(httpProtocol)
    //   - 위에서 정의한 HttpProtocolBuilder 를 이 setUp 에 연결한다.
    //
    // .assertions(...)
    //   - 테스트 성공/실패 기준을 정의한다.
    //   - CI/CD 파이프라인에서 assertions 위반 시 빌드를 실패시킬 수 있다.
    // ============================================================
    {
        setUp(
                // ============================================================
                // [Open Injection Model — injectOpen()]
                //
                // Open Model: 진행 중인 사용자 수와 무관하게 새 사용자를 계속 투입한다.
                //             실제 웹 서비스의 트래픽 패턴(누군가 오면 처리)과 유사.
                //
                // Closed Model (injectClosed): 동시 접속자 수를 일정하게 유지한다.
                //             한 명이 완료해야 다음 한 명을 투입. 커넥션 풀 테스트에 적합.
                //
                // ── 투입 단계 (순서대로 실행됨) ──────────────────────────────
                //
                // nothingFor(Duration)
                //   - 지정한 시간 동안 아무 사용자도 투입하지 않는다.
                //   - 용도: 서버 JVM 워밍업(JIT 컴파일 안정화) 대기.
                //           첫 요청부터 측정하면 Cold Start 로 인해 결과가 왜곡된다.
                //
                // rampUsers(N).during(Duration)
                //   - Duration 동안 총 N명을 균등한 간격으로 점진적으로 투입한다.
                //   - 예) rampUsers(50).during(30s) → 0.6초마다 1명씩 50명 투입
                //   - 용도: 트래픽이 서서히 증가하는 출근 시간대 패턴 시뮬레이션.
                //           서버의 스케일 아웃·스케일 업 반응 시간 측정.
                //
                // constantUsersPerSec(N).during(Duration)
                //   - Duration 동안 매 초마다 N명씩 지속 투입한다.
                //   - rampUsers 는 총량 기준, constantUsersPerSec 는 속도 기준.
                //   - 용도: 특정 QPS(Queries Per Second) 수준에서의 안정성 측정.
                // ============================================================
                userLifecycle.injectOpen(
                        nothingFor(Duration.ofSeconds(2)),                       // 2초 워밍업 대기
                        rampUsers(50).during(Duration.ofSeconds(30)),            // 30초에 걸쳐 50명 점진 증가
                        constantUsersPerSec(5).during(Duration.ofSeconds(30))    // 30초간 초당 5명 지속 투입
                )
        )
                .protocols(httpProtocol)
                .assertions(
                        // ============================================================
                        // [Assertion — 성공/실패 기준 정의]
                        //
                        // global()
                        //   - 모든 시나리오, 모든 요청을 합산한 전체 통계에 기준 적용.
                        //   - forAll() 을 쓰면 요청별로 각각 기준 적용 가능.
                        //
                        // .responseTime().percentile(95.0).lt(1000)
                        //   - p95 응답시간 < 1000ms 기준.
                        //   - percentile(95.0): 전체 요청 중 느린 순서로 5%를 제외한
                        //     95번째 백분위수 응답 시간.
                        //   - 평균(mean) 대신 p95/p99를 쓰는 이유:
                        //     평균은 이상값(outlier)의 영향을 받는다.
                        //     p95는 "100명 중 95명은 이 시간 안에 받는다"는 의미로
                        //     실제 사용자 경험을 더 정확히 반영한다.
                        //   - lt(N): less than (미만). lte(N): 이하.
                        //
                        // .successfulRequests().percent().gte(95.0)
                        //   - 전체 요청 중 성공(2xx) 비율 >= 95%.
                        //   - gte(N): greater than or equal (이상). gt(N): 초과.
                        //   - check() 조건을 통과한 요청만 성공으로 간주.
                        // ============================================================
                        global().responseTime().percentile(95.0).lt(1000),
                        global().successfulRequests().percent().gte(95.0)
                );
    }
}
