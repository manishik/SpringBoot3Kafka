package manish.learn.engine;

import manish.learn.model.Employee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class Producer {

    private static final Logger logger = LoggerFactory.getLogger(Producer.class);
    private static final String TOPIC = "kafka3-topic";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplateString;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplateObject;

    public void sendStringMessage(String stringMessage) {
        logger.info(String.format("#### -> Producing message -> %s", stringMessage));
        kafkaTemplateString.send(TOPIC, stringMessage);
    }

    public void sendObject(Employee employee) {
        // Send the object to the specified topic
        kafkaTemplateObject.send(TOPIC, employee);
        System.out.println("Producing (sending) object: " + employee + " to topic " + TOPIC);
    }
}
