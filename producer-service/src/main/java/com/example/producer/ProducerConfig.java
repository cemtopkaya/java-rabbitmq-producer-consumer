package com.example.producer;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProducerConfig {

    // “Benim application.properties dosyamdan rabbitmq.queue.name değerini al,
    // queueName değişkene ata.”
    @Value("${rabbitmq.queue.name}")
    private String queueName;

    /**
     * @Bean Queue → “ben bir kuyruk istiyorum”.
     * Queue, Exchange, Binding gibi nesneleri @Bean olarak tanımlarsan,
     * Spring Boot açılışta bunları toplayıp, RabbitMQ broker’a gidip declare ediyor.
     * Spring Boot’un AMQP starter’ı (spring-boot-starter-amqp) açılışta
     * bütün Queue, Exchange, Binding bean’lerini topluyor.
     * Daha sonra broker’a bağlanıp, bu nesnelerin hepsini queueDeclare, exchangeDeclare vb. komutlarla gerçekte RabbitMQ üzerinde oluşturuyor.
     * Yani sen kuyruk yaratmak için doğrudan channel.queueDeclare(...) çağırmıyorsun, Spring senin yerine yapıyor.
     */
    @Bean
    public Queue queue() {
        return new Queue(queueName, true);
    }
}
