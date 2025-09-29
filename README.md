# RabbitMQ Java Örneği

http://localhost:15672 # RabbitMQ Management UI
producer: http://localhost:8081/messages
consumer: http://localhost:8082

Send message to producer:

```sh
curl -vvv -X POST http://localhost:8081/messages \
    -H "Content-Type: application/json" \
    -d '{"message":"Hello, World!"}'
```

# Kuyruk

## RabbitMQ Kuyruğu

Genelde bir kuyruk yaratmak için Java'da şöyle bir komut kullanırız:

```java
// Kalıcı                (durable=true),
// herkes bağlanabilir   (exclusive=false),
// boş kalsa da silinmez (autoDelete=false)
new Queue("my-queue", true, false, false);
```

RabbitMQ kuyruk oluşturma parametreleri:

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

RabbitMQ’da **kuyruk/ekleme işlerinde** aslında 2 farklı tanımlama yöntemi vardır.

1.  **Passive Declare (`queueDeclarePassive`)** : Hiçbir şey oluşturmaz, sadece doğrulama yapar.
    _Kullanım yeri:_ Kuyruğun önceden tanımlı olmasının beklendiği senaryolar (ör. sistemler arası sıkı anlaşma/protokol).
    - “Ben böyle bir kuyruk var mı yok mu, sadece bir bakayım” der.
    - Eğer kuyruk yoksa (`404 NOT_FOUND`) hata fırlatır. `com.rabbitmq.client.ShutdownSignalException` içine gömülmüş `AMQP.Channel.Close` mesajıyla yakalarsın.
      > Caused by: **com.rabbitmq.client.ShutdownSignalException**: channel error; protocol method: #method<channel.close>(reply-code=404, reply-text=NOT_FOUND - no queue 'my-test-queue' in vhost '/', class-id=50, method-id=10)
                at com.rabbitmq.client.impl.ChannelN.asyncShutdown(ChannelN.java:528) ~[amqp-client-5.21.0.jar:5.21.0]
                at com.rabbitmq.client.impl.ChannelN.processAsync(ChannelN.java:349) ~[amqp-client-5.21.0.jar:5.21.0]

> Consumer, “bu kuyruk zaten önceden tanımlı” diye varsayıyor. Kuyruğun ismi, özellikleri (durable, autoDelete vs.), hatta yaşayıp yaşamadığı başka bir tarafa (genelde producer veya sistem yöneticisine) bağımlı hale geliyor. Consumer, kuyruğun daha önceden oluşturulmasına gereksinim duyar yani bağımsız çalışamaz yani yüksek bağımlı (highly coupled).

2.  **Active Declare (`queueDeclare`)** : Kuyruk zaten var ama parametreleri uyuşmazsa broker, `illegal redeclare` der ve `406 PRECONDITION_FAILED` döner
    - Eğer kuyruk varsa ama parametreleri (`durable`, `exclusive`, `autoDelete` gibi) uyumsuzsa yine hata fırlatır (`illegal redeclare` der ve `406 PRECONDITION_FAILED` döner). Bu yüzden hem producer hem consumer aynı parametrelerle declare yapıyorsa sorun olmaz.
    - Eğer kuyruk yoksa parametrelerle (queue adı, durable, exclusive, autoDelete) kuyruğu oluşturur.

> Her iki taraf da (producer/consumer) aynı kuyruk parametrelerini biliyor ve kuyruk tanımlanmamışsa kendisi oluşturuyor. Yani consumer “benim kuyruğum yoksa ben açarım” diyebiliyor. Producer ve consumer birbirinden bağımsız ayağa kalkabiliyor. Bu yaklaşım coupling’i düşürür, ama ortak protokol (aynı parametrelerle declare etme zorunluluğu) yaratır.

3. **Server-named + exchange binding** : Sunucunun isimlendirdiği kuyruklar diğer bir deyişle rastgele adlandırma.
   - Producer sadece bir exchange’e yazar.
   - Consumer kendi kuyruğunu server-named declare eder ve exchange’e bind eder.
   - Ama producer bu kuyruğun ismini bilmez, onun yerine “`exchange` + `routing key`” üstünden esnek bağlılık (loose coupling) meydana gelir.
   - Bu senaryoda consumer tamamen bağımsızdır.

İstisna oluştuğu zaman daima şunlar gerçekleşir:

- RabbitMQ bu durumlarda bağlı channel’ı kapatır.
- Channel kapanınca client kütüphanesi (com.rabbitmq.client) ShutdownSignalException fırlatır.
- İçindeki reply-code ve reply-text sana broker’ın nedenini anlatır.
