package com.astratech.external_library;

import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.astratech.external_library.exception.KpiNotFoundException;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

public class Kopi {


    @Configuration
    @AllArgsConstructor
    public static class RabbitMQConfig {

        @Bean
        Queue emailQueue() {
            return QueueBuilder.durable("TeguhQueue").build();
        }
        @Bean
        DirectExchange rabbitExchange() {
            return new DirectExchange("TeguhExchange");
        }
        @Bean
        Binding notifEmailBinding() {
            return BindingBuilder.bind(emailQueue()).to(rabbitExchange()).with("TeguhQueue");
        }

        @Bean
        public ConnectionFactory connectionFactory() {
            CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
            connectionFactory.setHost("54.219.62.176");
            connectionFactory.setPort(5672);
            connectionFactory.setUsername("polman");
            connectionFactory.setPassword("polman");
            connectionFactory.resetConnection();
            return connectionFactory;
        }
        @Bean
        MessageListenerAdapter listener(RabbitMQConsumer listener) {
            return new MessageListenerAdapter(listener, "receiveMessage");
        }

        @Bean
        SimpleMessageListenerContainer messageListenerContainer(RabbitMQConsumer listener) {
            SimpleMessageListenerContainer listenerContainer = new SimpleMessageListenerContainer();
            listenerContainer.setConnectionFactory(connectionFactory());
            listenerContainer.setMessageListener(listener(listener));
            listenerContainer.setDefaultRequeueRejected(false);
            listenerContainer.setQueues(emailQueue());
            return listenerContainer;
        }
    }


    @Entity
    @Data
    @Table(name = "bumbu")
    public static class BumbuModel{
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        private String name;
        private int quantity;
        private int price;
        private String type;
        private String supplier;
    }


    @Service
    public static interface BumbuServices{
        List<BumbuModel> getList();
        BumbuModel save(BumbuModel bumbuModel);
    }

    @Component
    @Data
    public static class BumbuUsecase implements BumbuServices{

        @Autowired
        private BumbuRepository bumbuRepository;

        public List<BumbuModel> getList() {
            return bumbuRepository.findAll();
        }

        @Transactional
        public BumbuModel save(BumbuModel bumbuModel) {
            return bumbuRepository.save(bumbuModel);
        }

    }

    @RestController
    @Slf4j
    public static class BumbuKopi{

        @Autowired
        private BumbuUsecase bumbuUsecase;

        @Autowired private RabbitTemplate rabbitTemplate;

        @PostMapping(value = "/tambah-kopi")
        @SneakyThrows
        public ResponseEntity<?> postData(@RequestBody  BumbuModel bumbuModel){
            try {
                BumbuModel data = bumbuUsecase.save(bumbuModel);
                return ResponseEntity.ok(data);
            }catch (Exception e){
               throw new KpiNotFoundException("Data Not Found");
            }
        }

        @GetMapping(value = "/ambil-kopi")
        public ResponseEntity<List<BumbuModel>> getAllData(){
            List<BumbuModel> datas = bumbuUsecase.getList();
            return ResponseEntity.ok(datas);
        }


        @PostMapping(value = "/publish")
        public void sendMessage(@RequestBody  String message)
        {
            rabbitTemplate.convertAndSend(
                    "TeguhExchange", "TeguhQueue", message);
        }

    }


    @Component
    public static class RabbitMQConsumer {

        @RabbitListener(queues = "TeguhQueue")
        public void receiveMessage(String message)
        {
            System.out.println("Received message: " + message);
        }
    }

    @Component
    @Slf4j
    public static class ScheduledTask {

        @Autowired
        private BumbuKopi bumbuKopi;

        @Scheduled(cron = "*/1 * * * * *") // Cron expression to run every second
        public void performTask() {
            bumbuKopi.sendMessage("anda berhasil consume message");
        }
    }

}
