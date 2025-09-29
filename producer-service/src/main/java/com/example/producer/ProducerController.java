package com.example.producer;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.javapoet.ClassName;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/messages")
public class ProducerController {
    private static final Logger LOGGER = Logger.getLogger( ClassName.class.getName() );

    // Tüm uç noktalarda kuyruğa veri basabilelim diye sınıf seviyesinde RabbitTemplate tanımlıyoruz
    private final RabbitTemplate rabbitTemplate;

    // application.properties dosyasından kuyruk adını alıyoruz
    @Value("${rabbitmq.queue.name}")
    private String queueName;

    // Spring bizim için IoC özelliğini kullanarak RabbitTemplate'i enjekte eder
    public ProducerController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping
    public String sendMessage(@RequestBody String message) {
        LOGGER.log( Level.FINE, "Sending message to queue: {}", message);

        rabbitTemplate.convertAndSend(queueName, message);
        return "Sent: " + message;
    }
}
