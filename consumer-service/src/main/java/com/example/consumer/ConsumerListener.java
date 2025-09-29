package com.example.consumer;

import java.util.logging.Logger;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConsumerListener {

    Logger logger = Logger.getLogger(getClass().getName());

    @Value("${rabbitmq.queue.name}")
    private String queueName;

    @RabbitListener(queues = "${rabbitmq.queue.name}")
    public void listen(String message) {
        logger.fine("Received on " + queueName + ": " + message);
    }
}
