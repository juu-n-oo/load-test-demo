package io.ten1010.loadtest.gatling.simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * ============================================================
 * [이 시뮬레이션이 측정하는 것]
 *
 * 주문 생성 시 발생하는 복합 트랜잭션 성능을 측정한다:
 *   - DB 쓰기 경합: 여러 사용자가 동시에 같은 상품의 재고를 차감
 *   - 상태 전이: PENDING → SHIPPED → DELIVERED 의 연속 UPDATE
 *   - 에러 처리: 재고 초과 주문 → 400 응답이 빠르게 반환되는지
 *
 * 두 개의 독립 시나리오를 동시에 실행한다:
 *   orderFlow      — 정상 주문 흐름 (30명 ramp-up + 초당 3명 steady)
 *   stockExhaustion — 재고 부족 상황 검증 (5초 후 10명 동시 투입)
 * ============================================================
 */
public class OrderFlowSimulation extends Simulation {

    private static final String BASE_URL = "http://localhost:8080";

    // ============================================================
    // [HttpProtocolBuilder 재사용 패턴]
    //
    // 같은 setUp 에 등록된 모든 시나리오가 .protocols(httpProtocol) 를 공유한다.
    // 시나리오마다 다른 프로토콜 설정이 필요하다면 setUp 에서 별도로 지정할 수 있다:
    //   setUp(scnA.injectOpen(...).protocols(protocolA),
    //         scnB.injectOpen(...).protocols(protocolB))
    // ============================================================
    HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .shareConnections();

    // ============================================================
    // [시나리오 A — 정상 주문 흐름]
    //
    // 각 가상 사용자는 자신만의 독립적인 사용자·상품 데이터를 생성한다.
    // 이렇게 하면:
    //   1) username/email 충돌 없음
    //   2) 각자 다른 상품에 주문하므로 재고 경합이 발생하지 않음
    //      → 순수한 쓰기 처리 성능을 측정
    //
    // [설계 포인트]
    // 실제 부하 테스트에서는 "공유 데이터"와 "독립 데이터"를 구분해야 한다.
    //   공유 데이터 테스트 예: 모든 VU가 동일한 인기 상품에 주문
    //     → 재고 차감 Lock 경합 발생 시나리오 (별도 시뮬레이션으로 설계)
    //   독립 데이터 테스트 예: 이 시뮬레이션처럼 VU마다 자기 상품 생성
    //     → DB 처리량(throughput) 자체를 측정
    // ============================================================
    ScenarioBuilder orderFlow = scenario("주문 전체 흐름")

            // 1단계: 세션 초기화
            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                String pid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                return session
                        .set("username", "order_user_" + uid)
                        .set("email", "order_" + uid + "@loadtest.io")
                        .set("password", "orderpass1")
                        .set("productName", "부하테스트상품_" + pid);
            })

            // 2단계: 사용자 가입
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
                    // saveAs() — 이후 단계에서 주문 생성 시 userId 가 필요하므로 반드시 저장
                    .check(jsonPath("$.id").saveAs("userId")))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 3단계: 상품 등록
            .exec(http("상품 등록")
                    .post("/api/products")
                    .body(StringBody("""
                            {
                              "name": "#{productName}",
                              "description": "부하 테스트용 상품입니다.",
                              "price": 29900,
                              "stock": 1000,
                              "category": "test"
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("productId"))  // 주문 생성 시 productId 필요
                    // ============================================================
                    // [check 체이닝 — 한 요청에 여러 check를 걸 수 있다]
                    //
                    // 모든 check 가 통과해야 해당 요청이 성공으로 기록된다.
                    // 하나라도 실패하면 KO 처리되고 이후 exec 는 실행되지 않는다.
                    //
                    // jsonPath("$.stock").is("1000")
                    //   - is() 는 내부적으로 문자열 비교를 한다.
                    //     JSON 숫자 1000이라도 "1000" (문자열)으로 비교한다.
                    // ============================================================
                    .check(jsonPath("$.stock").is("1000")))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 4단계: 상품 상세 조회 (실제 사용자는 주문 전 상품 페이지를 확인한다)
            .exec(http("상품 상세 조회")
                    .get("/api/products/#{productId}")
                    .check(status().is(200))
                    .check(jsonPath("$.id").is("#{productId}")))

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            // 5단계: 주문 생성
            // ============================================================
            // [JSON Body 에서 숫자 vs 문자열 주의]
            //
            // "userId": #{userId}    ← 숫자형 Long (따옴표 없음)
            // "username": "#{username}" ← 문자열 (따옴표 있음)
            //
            // Gatling EL(#{}) 은 Session 에 저장된 값의 타입 그대로 삽입한다.
            //   saveAs() 로 저장된 jsonPath 추출값은 기본적으로 String 타입.
            //   따라서 숫자 필드에는 따옴표를 붙이지 않고 #{userId} 로 써야
            //   JSON 파싱 오류 없이 Long 으로 처리된다.
            //
            // "productQuantities": { "#{productId}": 2 }
            //   - Map<Long, Long> 형태의 JSON 오브젝트.
            //   - 키(productId)도 문자열로 전송되지만 Jackson이 Long 으로 파싱한다.
            // ============================================================
            .exec(http("주문 생성")
                    .post("/api/orders")
                    .body(StringBody("""
                            {
                              "userId": #{userId},
                              "shippingAddress": "서울시 강남구 테스트로 123",
                              "productQuantities": {
                                "#{productId}": 2
                              }
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("orderId"))
                    .check(jsonPath("$.status").is("PENDING"))
                    // exists() — totalPrice 가 계산되어 응답에 포함됐는지 확인
                    //            값 검증 없이 존재 여부만 체크
                    .check(jsonPath("$.totalPrice").exists()))

            .pause(Duration.ofMillis(200), Duration.ofMillis(500))

            // 6단계: 주문 상세 조회
            // ============================================================
            // [중첩 JSON 경로 — $.items[0].productId]
            //
            // JsonPath 문법:
            //   $.items          → items 배열 전체
            //   $.items[0]       → 첫 번째 요소 (0-based index)
            //   $.items[0].productId  → 첫 번째 요소의 productId 필드
            //   $.items.length() → 배열 길이 (Gatling은 size() 사용)
            //
            // 이 check 로 백엔드의 fetch join 이 올바르게 작동하는지 검증한다:
            //   Order → OrderItem → Product 까지 연관관계를 한 번에 로딩하는지 확인.
            // ============================================================
            .exec(http("주문 상세 조회")
                    .get("/api/orders/#{orderId}")
                    .check(status().is(200))
                    .check(jsonPath("$.id").is("#{orderId}"))
                    .check(jsonPath("$.items[0].productId").is("#{productId}"))
                    .check(jsonPath("$.items[0].quantity").is("2")))

            .pause(Duration.ofMillis(100), Duration.ofMillis(300))

            // 7단계: 사용자 주문 목록 조회
            // ============================================================
            // [페이지 응답의 content 배열 검증]
            //
            // Spring Page<T> 의 JSON 구조:
            //   { "content": [...], "totalElements": N, "totalPages": N, ... }
            //
            // $.content[0].id — 첫 번째 주문의 id 가 존재하는지 확인.
            // exists() 를 쓰면 값이 무엇인지 상관없이 필드 존재 여부만 검증.
            // ============================================================
            .exec(http("주문 목록 조회")
                    .get("/api/orders")
                    .queryParam("userId", "#{userId}")
                    .queryParam("status", "PENDING")
                    .check(status().is(200))
                    .check(jsonPath("$.content[0].id").exists()))

            .pause(Duration.ofMillis(300), Duration.ofMillis(600))

            // 8단계: PENDING → SHIPPED 상태 전이
            .exec(http("주문 상태 변경 - SHIPPED")
                    .put("/api/orders/#{orderId}/status")
                    .body(StringBody("""
                            { "status": "SHIPPED" }
                            """))
                    .check(status().is(200))
                    .check(jsonPath("$.status").is("SHIPPED")))

            .pause(Duration.ofMillis(200), Duration.ofMillis(400))

            // 9단계: SHIPPED → DELIVERED 상태 전이
            .exec(http("주문 상태 변경 - DELIVERED")
                    .put("/api/orders/#{orderId}/status")
                    .body(StringBody("""
                            { "status": "DELIVERED" }
                            """))
                    .check(status().is(200))
                    .check(jsonPath("$.status").is("DELIVERED")));

    // ============================================================
    // [시나리오 B — 에러 케이스 검증: 재고 부족]
    //
    // 부하 테스트는 성공 경로만 측정하면 안 된다.
    // 에러 처리 경로(예: 400, 404, 409)도 빠르게 응답하는지 확인해야 한다.
    //
    // 이 시나리오의 목적:
    //   1) 재고 부족 시 서버가 빠르게 400을 반환하는지 확인
    //   2) 에러 응답이 성공 응답보다 지나치게 느리지 않은지 측정
    //   3) 에러 폭증 상황에서 서버가 안정적으로 동작하는지 검증
    //
    // [check(status().is(400))]
    //   - 기대값이 400 이므로 서버가 400을 반환하면 이 요청은 '성공'으로 기록된다.
    //   - 에러 케이스를 정상적으로 처리했다는 의미.
    //   - setUp 의 assertions 에서 성공률 기준을 80% 로 낮춘 이유:
    //     이 시나리오의 '재고 초과 주문 시도' 요청은 의도적으로 400을 받는데,
    //     check(status().is(400)) 을 달지 않으면 KO 가 되어 전체 성공률을 낮춘다.
    //     현재는 check 를 달아서 400도 성공으로 집계되므로 80% 기준은 안전 마진.
    // ============================================================
    ScenarioBuilder stockExhaustion = scenario("재고 부족 주문 시도")

            .exec(session -> {
                String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                String pid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                return session
                        .set("username", "stock_user_" + uid)
                        .set("email", "stock_" + uid + "@loadtest.io")
                        .set("password", "stockpass1")
                        .set("productName", "재고1개상품_" + pid);
            })

            .exec(http("사용자 가입 (재고테스트)")
                    .post("/api/users")
                    .body(StringBody("""
                            {
                              "username": "#{username}",
                              "email": "#{email}",
                              "password": "#{password}"
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("userId")))

            // stock: 1 — 수량 1개만 허용되는 한정 상품
            .exec(http("재고 1개 상품 등록")
                    .post("/api/products")
                    .body(StringBody("""
                            {
                              "name": "#{productName}",
                              "description": "재고가 1개뿐인 상품",
                              "price": 9900,
                              "stock": 1,
                              "category": "limited"
                            }
                            """))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("productId")))

            // 재고(1)보다 많은 수량(2)으로 주문 → BusinessException → 400 응답 기대
            .exec(http("재고 초과 주문 시도")
                    .post("/api/orders")
                    .body(StringBody("""
                            {
                              "userId": #{userId},
                              "shippingAddress": "서울시 종로구 재고없음로 1",
                              "productQuantities": {
                                "#{productId}": 2
                              }
                            }
                            """))
                    // ============================================================
                    // [에러 응답을 '성공'으로 처리하는 패턴]
                    //
                    // check(status().is(400))
                    //   - 서버가 400을 반환하면 이 요청은 KO 가 아닌 OK 로 집계된다.
                    //   - "이 상황에서 400을 받는 것이 올바른 동작이다"를 명시하는 것.
                    //   - 만약 서버가 실수로 200을 반환하면 오히려 이 check 가 실패한다.
                    //
                    // [주의] check 없이 그냥 두면 400은 자동으로 KO 처리된다.
                    // ============================================================
                    .check(status().is(400)));

    // ============================================================
    // [setUp — 복수 시나리오 동시 실행]
    //
    // setUp() 에 여러 시나리오를 나열하면 모두 동시에(병렬로) 실행된다.
    //
    //   orderFlow      : 2초 대기 → 20초간 30명 ramp-up → 30초간 초당 3명 steady
    //   stockExhaustion: 5초 대기 → 10명 동시 투입
    //
    // 타임라인:
    //   0s  : 두 시나리오 모두 시작
    //   2s  : orderFlow 사용자 투입 시작
    //   5s  : stockExhaustion 10명 동시 투입
    //   22s : orderFlow ramp-up 완료
    //   22s~52s: orderFlow steady state (초당 3명)
    //   총 실행 시간 ≈ 55초
    // ============================================================
    {
        setUp(
                orderFlow.injectOpen(
                        nothingFor(Duration.ofSeconds(2)),
                        rampUsers(30).during(Duration.ofSeconds(20)),
                        constantUsersPerSec(3).during(Duration.ofSeconds(30))
                ),
                // ============================================================
                // [atOnceUsers(N) — 스파이크(Spike) 테스트]
                //
                // N명을 동시에(즉시) 투입한다.
                // 실제 서비스에서 이벤트·프로모션 시작 시 트래픽이 급증하는 상황 모사.
                // 서버의 순간 동시 처리 한계와 에러 처리를 검증하기에 적합.
                //
                // rampUsers 와의 비교:
                //   rampUsers(30).during(20s)  → 0.67초마다 1명 (점진적)
                //   atOnceUsers(30)            → 동시에 30명 (스파이크)
                // ============================================================
                stockExhaustion.injectOpen(
                        nothingFor(Duration.ofSeconds(5)),
                        atOnceUsers(10)
                )
        )
                .protocols(httpProtocol)
                .assertions(
                        // p95 응답시간 < 2s
                        // (주문 생성은 재고 차감 트랜잭션이 포함되어 상품 조회보다 느릴 수 있음)
                        global().responseTime().percentile(95.0).lt(2000),
                        // 성공률 >= 80% (재고 부족 400 응답을 성공으로 처리했으므로 여유 있는 기준)
                        global().successfulRequests().percent().gte(80.0)
                );
    }
}
