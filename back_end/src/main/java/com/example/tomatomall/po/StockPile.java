package com.example.tomatomall.po;

import com.example.tomatomall.vo.StockPileVO;
import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "stockpile")
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
