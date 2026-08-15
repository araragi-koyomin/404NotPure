package com.example.tomatomall.po;

import com.example.tomatomall.vo.StockPileVO;
import lombok.*;

import javax.persistence.*;

@Entity
@Table(
    name = "stockpile",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_stockpile_product_id",
        columnNames = "product_id"
    )
)
@Getter
@Setter
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "product_id", nullable = false)
    private int productId;
    private int amount;
    private int frozen;

    public StockPileVO toVO() {
        StockPileVO vo = new StockPileVO();
        vo.setProductId(productId);
        vo.setAmount(amount);
        vo.setFrozen(frozen);
        return vo;
    }
}
