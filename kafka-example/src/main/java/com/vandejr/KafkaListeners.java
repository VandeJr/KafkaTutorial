package com.vandejr;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaListeners {

    @KafkaListener(topics = "newtopic", groupId = "group1")
    void listener(String data) {
        System.out.println("Listener received: " + data);
    }
}
