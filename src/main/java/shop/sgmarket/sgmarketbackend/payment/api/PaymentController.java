package shop.sgmarket.sgmarketbackend.payment.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shop.sgmarket.sgmarketbackend.global.response.ApiResponseTemplate;
import shop.sgmarket.sgmarketbackend.payment.api.dto.request.PaymentCallbackReqDto;
import shop.sgmarket.sgmarketbackend.payment.api.dto.response.PaymentResDto;
import shop.sgmarket.sgmarketbackend.payment.application.PaymentService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
@Tag(name = "결제 API", description = "Portone(아임포트) 결제 연동")
public class PaymentController {

    @Value("${imp.api.client-code}")
    private String clientCode;

    private final PaymentService paymentService;

    @PostMapping
    @Operation(
            summary     = "결제 정보 조회",
            description = "orderUid를 받아 결제창 호출에 필요한 clientCode·금액 등을 반환합니다."
    )
    public ApiResponseTemplate<Map<String, Object>> createPayment(
            @Parameter(description = "주문 UID(merchant_uid)", required = true)
            @RequestParam String orderUid
    ) {

        PaymentResDto paymentInfo = paymentService.getPaymentInfo(orderUid);

        Map<String, Object> result = new HashMap<>();
        result.put("clientCode", clientCode);
        result.put("paymentInfo", paymentInfo);

        return ApiResponseTemplate.ok("결제 정보 조회에 성공했습니다.", result);
    }

    /**
     * 결제창 승인 직후 브라우저가 호출하는 콜백 경로.
     * 클라이언트가 전달하는 값은 imp_uid·orderUid뿐이며, 실제 승인 여부·금액 검증은
     * 웹훅과 동일하게 서버가 PG에 재조회하는 processPayment 에서 수행한다.
     */
    @PostMapping("/callback")
    @Operation(
            summary     = "결제 콜백 검증",
            description = "결제 승인 직후 프론트가 전달한 imp_uid를 서버가 PG에 재조회하여 승인 상태·금액을 검증합니다."
    )
    public ApiResponseTemplate<Void> paymentCallback(
            @Parameter(description = "PG 결제 고유번호(imp_uid)", required = true)
            @RequestParam String paymentUid,
            @Parameter(description = "주문 UID(merchant_uid)", required = true)
            @RequestParam String orderUid
    ) {
        paymentService.processPayment(new PaymentCallbackReqDto(paymentUid, orderUid));
        return ApiResponseTemplate.ok("결제 검증이 완료되었습니다.");
    }
}
