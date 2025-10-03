# RabbitMQ Java Örneği

#### Web Arayüzleri:

RabbitMQ Management UI: http://localhost:15672

producer: http://localhost:8081/messages

consumer: http://localhost:8082

## Hızlı Komutlar

### Passive Declare Queue

Producer ayaklanır ve kendisine yapılan curl isteğiyle kuyruk yaratılır. Consumer ayaklandığında kuyruk yoksa 3 kez dener ve kapanır varsa bağlanır ve mesajı okur.

RabbitMQ hizmetini yeniden başlatalım, taze başlangıç için:

```sh
docker compose -f .devcontainer/docker-compose-dev.yml down rabbitmq
docker compose -f .devcontainer/docker-compose-dev.yml up -d rabbitmq
```

Projeyi derleyip producer ve consumer'ı ayaklandıralım:

```sh
mvn clean install

cd producer-service
mvn spring-boot:run
```

Kuyruk işlemleri yapalım:

```sh
ctr=rabbit

# Kuyrukları listele
docker exec -it $ctr rabbitmqctl list_queues

# Producer'ın uç noktasına istek yapalım arkada kuyruk oluşsun, mesaj yazılsın
curl -vvv -X POST http://localhost:8081/messages \
 -H "Content-Type: application/json" \
 -d '{"message":"Hello, World!"}'

# Kuyrukları listele
docker exec -it $ctr rabbitmqctl list_queues
```

Consumer ayaklandırılır ve mesajlar okunur:

```sh
cd ./consumer-service
mvn spring-boot:run
```

Kuyruktan mesajı CLI üzerinden oku:

```sh
docker exec -it $ctr rabbitmqadmin get queue=my-test-queue count=1 ackmode=ack_requeue_false
```

## Kuyruk

1. RabbitMQ Bağlantı Bilgileri

- spring.rabbitmq.host: RabbitMQ sunucusunun IP adresi veya host adı.
- spring.rabbitmq.port: RabbitMQ sunucusunun bağlantı portu (genellikle 5672).
- spring.rabbitmq.username: RabbitMQ sunucusuna bağlanmak için kullanılan kullanıcı adı.
- spring.rabbitmq.password: RabbitMQ sunucusuna bağlanmak için kullanılan şifre.

Bu bilgiler, RabbitMQ sunucusuna bağlanmak için gereklidir.

2. RabbitMQ Kuyruk ve Exchange Yapılandırması

- eqm.rabbitmq.queue: Uygulamanın mesajları okumak veya yazmak için kullanacağı kuyruğun adı.
- eqm.rabbitmq.exchange: Mesajların gönderileceği veya okunacağı exchange'in adı.
- eqm.rabbitmq.routingKey: Mesajların hangi kuyruğa yönlendirileceğini belirleyen anahtar.

Bu yapılandırma, RabbitMQ'daki kuyruk ve exchange yapısını tanımlar.

3. Dağıtım ve Bölümleme Yapılandırması

- simple.distribute: Mesajların basit bir şekilde dağıtılıp dağıtılmayacağını belirleyen bir bayrak (flag). false ise, mesajlar belirli bir mantığa göre dağıtılır. true ise, basit bir dağıtım stratejisi kullanılır.
- partition.number: Kuyrukların kaç parçaya bölüneceğini belirleyen sayı. Örneğin, 2 ise, mesajlar 2 farklı kuyruğa dağıtılır.

### RabbitMQ Konsol Komutları

```sh
ctr=rabbit  # RabbitMQ konteyner adı

# Kuyrukları listeleme
docker exec -it $ctr rabbitmqctl list_queues

# Kuyruk oluşturma
docker exec -it $ctr rabbitmqadmin declare queue name=benim-kuyruum durable=true

# Kuyruk detaylarını görmek için (durable, mesaj sayısı gibi) rabbitmqctl ile:
docker exec -it $ctr rabbitmqctl list_queues name durable messages
```

Kuyruk özelliklerini değiştirme (örnek: durable özelliği değiştirme için önce silip yeniden oluşturulmalı)

```sh
ctr=rabbit  # RabbitMQ konteyner adı

# RabbitMQ'da doğrudan özellik değiştirme yok, kuyruk silinir
docker exec -it $ctr rabbitmqadmin delete queue name=benim-kuyruum

# ve yeni özelliklerle tekrar oluşturulur:
docker exec -it $ctr rabbitmqadmin declare queue name=benim-kuyruum durable=false
```

### RabbitMQ Soyut Nesneleri

RabbitMQ’nun gerçek yapı taşlarının Java’daki nesne karşılıkları olan Queue, Exchange ve Binding kavramlarını birazdan göreceğiz ama önce Spring içinde nasıl tanımlandıklarına bakalım:

```java
@Bean
public Queue myQueue() {
    return new Queue("my-queue", true);
}

@Bean
public DirectExchange myExchange() {
    return new DirectExchange("my-exchange");
}

@Bean
public Binding myBinding(Queue myQueue, DirectExchange myExchange) {
    return BindingBuilder.bind(myQueue).to(myExchange).with("routing-key");
}
```

RabbitMQ’nun soyut nesneleri, yani broker üzerinde var olan yapıların Java karşılıkları:

1. **Queue (Kuyruk)**:
   Mesajların yazıldığı kutu (örn. email-queue, sms-queue). Tüketici uygulamalar gelip bu kutulardan mesajları çekerler.

1. **Exchange (Değişim noktası)**:
   Mesajı alır, kuralına göre bir veya birden fazla kuyruğa yönlendirir.

   Exchange Türleri:

   1. _direct_ : tam eşleşme (routing key ile).
   1. _fanout_ : herkese dağıt (broadcast).
   1. _topic_ : joker karakterlerle eşleşme (user.\*, order.#).
   1. _headers_ : mesajın header bilgisine göre.

1. **Binding (Bağlama)**:
   Exchange ile Queue arasındaki köprü (“Bu kuyruğu şu exchange’e şu routing key ile bağla”).

### RabbitMQ Kuyruğu

Genelde bir kuyruk yaratmak için Java'da şöyle bir komut kullanırız:

```java
// Kalıcı                (durable=true),
// herkes bağlanabilir   (exclusive=false),
// boş kalsa da silinmez (autoDelete=false)
new Queue("my-queue", true, false, false);
```

Biraz daha uzun bir örnekle tek bir java dosyasında uygulama çalışsın ve "kuyruk oluştur, mesajı bas" senaryosunu gör diye çirkin ama çalışan kod yazalım:

```java
package com.example.producer;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;

@SpringBootApplication
public class ProducerApplication implements CommandLineRunner {

    private final ApplicationContext context;

    public ProducerApplication(ApplicationContext context) {
        this.context = context;
    }

    public static void main(String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
    }

    @Override
    public void run(String... args) {
        // Spring IoC ile otomatik verebilecekken bu şekilde de olabilir:
        // Spring context içinden ihtiyacımız olan bean’leri çekiyoruz
        ConnectionFactory connectionFactory = context.getBean(ConnectionFactory.class);
        RabbitTemplate rabbitTemplate = context.getBean(RabbitTemplate.class);

        // Kuyruğu programatik olarak oluştur
        String queueName = "time-queue";
        Queue queue = new Queue(queueName, true); // durable = true

        // Kuyruğu broker’a declare et
        connectionFactory.createConnection()
                         .createChannel(false)
                         .queueDeclare(queue.getName(), true, false, false, null);

        // Kuyruğa mesaj gönder
        String message = "Current time: " + LocalDateTime.now();
        rabbitTemplate.convertAndSend(queueName, message);

        System.out.println(" [x] Sent: " + message);
    }
}
```

#### RabbitMQ kuyruk oluşturma parametreleri

- **`durable`** : Kuyruk broker restart’ında kalıcı mı?
  > "Durable kuyruk" ≠ "mesajların da kalıcı olduğu" anlamına gelmez. Mesajların da kalıcı olması için message `deliveryMode=2` (persistent) ayarlanmalı. Durable sadece kuyruğun yapısının korunması demek.
- **`exclusive`** : Sadece bu connection’a özel mi?
  - Bağlantı kapanınca kuyruk da silinir.
  - Başka uygulama bağlanamaz.
- **`autoDelete`** : Consumer kalmazsa silinsin mi?
  > Event-based veya Pub/Sub sistemlerde “dinleyici yoksa gerek yok, çöpe at” senaryosu.
- **`arguments`** : Kuyruğa özel davranışlar (TTL, max-length, DLX vs.).
  - `x-message-ttl` : mesajın ömrü (örn. 60 saniye sonra sil).
  - `x-max-length` : kuyruğun maksimum mesaj sayısı.
  - `x-dead-letter-exchange` : ölen mesajları yönlendireceği exchange.

RabbitMQ’da **kuyruk/ekleme işlerinde** aslında 3 farklı tanımlama yöntemi vardır.

- **Passive** en sıkı bağımlılık (önceden kuyruğu tanımlamak zorundasın).
- **Active** parametrelerde uzlaşma şartıyla orta seviye bağımlılık.
- **Server-named** en gevşek bağımlılık, producer kuyruğun adını bilmez, sadece exchange’e publish eder.

Tüm yöntemlerde istisna oluştuğu zaman daima şunlar gerçekleşir:

- RabbitMQ bu durumlarda bağlı channel’ı kapatır.
- Channel kapanınca client kütüphanesi (com.rabbitmq.client) ShutdownSignalException fırlatır.
- İçindeki reply-code ve reply-text sana broker’ın nedenini anlatır.

#### 1. **Passive Declare (`queueDeclarePassive`)**

Hiçbir şey oluşturmaz, sadece doğrulama yapar.
_Kullanım yeri:_ Kuyruğun önceden tanımlı olmasının beklendiği senaryolar (ör. sistemler arası sıkı anlaşma/protokol).

- “Ben böyle bir kuyruk var mı yok mu, sadece bir bakayım” der.
- Eğer kuyruk yoksa (`404 NOT_FOUND`) hata fırlatır. `com.rabbitmq.client.ShutdownSignalException` içine gömülmüş `AMQP.Channel.Close` mesajıyla yakalarsın.
  > Caused by: **com.rabbitmq.client.ShutdownSignalException**: channel error; protocol method: #method<channel.close>(reply-code=404, reply-text=NOT_FOUND - no queue 'my-test-queue' in vhost '/', class-id=50, method-id=10)
            at com.rabbitmq.client.impl.ChannelN.asyncShutdown(ChannelN.java:528) ~[amqp-client-5.21.0.jar:5.21.0]
            at com.rabbitmq.client.impl.ChannelN.processAsync(ChannelN.java:349) ~[amqp-client-5.21.0.jar:5.21.0]

> Consumer, “bu kuyruk zaten önceden tanımlı” diye varsayıyor. Kuyruğun ismi, özellikleri (durable, autoDelete vs.), hatta yaşayıp yaşamadığı başka bir tarafa (genelde producer veya sistem yöneticisine) bağımlı hale geliyor. Consumer, kuyruğun daha önceden oluşturulmasına gereksinim duyar yani bağımsız çalışamaz yani yüksek bağımlı (highly coupled).

#### 2. **Active Declare (`queueDeclare`)**

Kuyruk zaten var ama parametreleri uyuşmazsa broker, `illegal redeclare` der ve `406 PRECONDITION_FAILED` döner

- Eğer kuyruk varsa ama parametreleri (`durable`, `exclusive`, `autoDelete` gibi) uyumsuzsa yine hata fırlatır (`illegal redeclare` der ve `406 PRECONDITION_FAILED` döner). Bu yüzden hem producer hem consumer aynı parametrelerle declare yapıyorsa sorun olmaz.
- Eğer kuyruk yoksa parametrelerle (queue adı, durable, exclusive, autoDelete) kuyruğu oluşturur.

> Her iki taraf da (producer/consumer) aynı kuyruk parametrelerini biliyor ve kuyruk tanımlanmamışsa kendisi oluşturuyor. Yani consumer “benim kuyruğum yoksa ben açarım” diyebiliyor. Producer ve consumer birbirinden bağımsız ayağa kalkabiliyor. Bu yaklaşım coupling’i düşürür, ama ortak protokol (aynı parametrelerle declare etme zorunluluğu) yaratır.

#### 3. **Server-named + exchange binding**

Sunucunun isimlendirdiği kuyruklar diğer bir deyişle rastgele adlandırma.

- Producer sadece bir exchange’e yazar.
- Consumer kendi kuyruğunu server-named declare eder ve exchange’e bind eder.
- Ama producer bu kuyruğun ismini bilmez, onun yerine “`exchange` + `routing key`” üstünden esnek bağlılık (loose coupling) meydana gelir.
- Bu senaryoda consumer tamamen bağımsızdır.
