package com.example.tomatomall.po;


import lombok.*;

import javax.persistence.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
public class ChatMessage {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private Integer senderId;
        private Integer receiverId;

        private String content;
        private Long timestamp;

        private String roomId;

        private Boolean isRead = false;

}
