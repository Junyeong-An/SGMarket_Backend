package shop.sgmarket.sgmarketbackend.payment.application;

import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.request.CancelData;
import com.siot.IamportRestClient.response.IamportResponse;
import java.io.IOException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shop.sgmarket.sgmarketbackend.global.error.ErrorCode;
import shop.sgmarket.sgmarketbackend.global.error.exception.CustomException;
import shop.sgmarket.sgmarketbackend.order.domain.Order;
import shop.sgmarket.sgmarketbackend.order.domain.repository.OrderRepository;
import shop.sgmarket.sgmarketbackend.payment.api.dto.request.PaymentCallbackReqDto;
import shop.sgmarket.sgmarketbackend.payment.api.dto.response.PaymentResDto;
import shop.sgmarket.sgmarketbackend.payment.domain.Payment;
import shop.sgmarket.sgmarketbackend.payment.domain.Status;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final OrderRepository orderRepository;
    private final IamportClient iamportClient;

    public PaymentResDto getPaymentInfo(String orderUid) {
        Order order = orderRepository.findOrderAndPaymentAndMember(orderUid)
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));

        return PaymentResDto.from(order);
    }

    /**
     * 결제 승인 결과 처리.
     * 콜백(브라우저 경유)과 웹훅(PG 서버 직접 호출) 두 경로가 모두 이 메서드로 수렴한다.
     * 어느 경로로 먼저 도착하든 동일한 검증을 거치며, 이미 종결된 결제는 다시 처리하지 않는다.
     */
    @Transactional
    public void processPayment(PaymentCallbackReqDto reqDto) {
        // orderUid 단위 비관적 락 — 콜백과 웹훅이 동시에 도착해도 한 건씩 직렬화된다
        Order order = orderRepository.findByOrderUidForUpdate(reqDto.orderUid())
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));

        Payment payment = order.getPayment();

        // 멱등 처리 — PENDING이 아니면 이미 처리된 결제이므로 중복 수신을 걸러낸다
        if (payment.getStatus() != Status.PENDING) {
            log.info("이미 처리된 결제 - 중복 수신 무시 (orderUid: {}, status: {})",
                    reqDto.orderUid(), payment.getStatus());
            return;
        }

        // 클라이언트가 전달한 값은 imp_uid뿐 — 승인 상태·금액은 PG에 재조회한 값만 신뢰한다
        IamportResponse<com.siot.IamportRestClient.response.Payment> pgResponse =
                findPgPayment(reqDto.paymentUid());

        validatePayment(pgResponse, payment);

        payment.updateStatus(Status.PAID, pgResponse.getResponse().getImpUid());
        order.complete();
    }

    private IamportResponse<com.siot.IamportRestClient.response.Payment> findPgPayment(String paymentUid) {
        try {
            return iamportClient.paymentByImpUid(paymentUid);
        } catch (IamportResponseException | IOException e) {
            throw new CustomException(ErrorCode.PAYMENT_PROCESSING_ERROR, e.getMessage());
        }
    }

    private void validatePayment(
            IamportResponse<com.siot.IamportRestClient.response.Payment> pgResponse,
            Payment payment
    ) {
        if (!"paid".equals(pgResponse.getResponse().getStatus())) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_COMPLETED);
        }

        long approvedAmount = pgResponse.getResponse().getAmount().longValue();
        long orderAmount = payment.getPrice();

        if (approvedAmount != orderAmount) {
            // 위변조 의심 건은 PG에 승인 취소를 요청하고, 주문·결제 기록은 삭제하지 않고 남긴다
            cancelPgPayment(pgResponse.getResponse().getImpUid(), approvedAmount);
            throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    private void cancelPgPayment(String impUid, long amount) {
        try {
            iamportClient.cancelPaymentByImpUid(new CancelData(impUid, true, BigDecimal.valueOf(amount)));
        } catch (IamportResponseException | IOException e) {
            log.error("승인 취소 요청 실패 (impUid: {})", impUid, e);
            throw new CustomException(ErrorCode.PAYMENT_PROCESSING_ERROR, e.getMessage());
        }
    }
}
