package shop.sgmarket.sgmarketbackend.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long price;

    private Status status;

    /* 같은 PG 승인 건(imp_uid)이 두 결제에 매핑되는 것을 DB 차원에서 차단하는 최후의 방어선 */
    @Column(unique = true)
    private String paymentUid;

    @Builder
    private Payment(Long price, Status status) {
        this.price = price;
        this.status = status;
    }

    public void updateStatus(Status status, String paymentUid) {
        this.status = status;
        this.paymentUid = paymentUid;
    }
}
