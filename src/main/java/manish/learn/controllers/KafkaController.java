package manish.learn.controllers;

import manish.learn.consumers.Consumer;
import manish.learn.producers.Producer;
import manish.learn.model.Employee;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/kafka")
public class KafkaController {

    private final Producer producer;
    private final Consumer consumer;

    public KafkaController(Producer producer, Consumer consumer) {
        this.producer = producer;
        this.consumer = consumer;
    }

    @PostMapping("/publishMessage")
    public String sendMessageToKafkaTopic(@RequestParam("message") String message) {
        this.producer.sendStringMessage(message);
        return message + " successfully sent";
    }

    @PostMapping("/publishObject")
    public String publishUser(@RequestBody Employee employee) {
        producer.sendObject(employee);
        return "Object published to Kafka topic successfully";
    }

    @GetMapping("/getMessages")
    public List<String> getMessages() {
        return consumer.getMessages();
    }
}
