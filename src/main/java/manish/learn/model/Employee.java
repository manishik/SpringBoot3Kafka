package manish.learn.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;

@Setter
@Getter
@ToString
@Component
public class Employee {
    int employeeId;
    String firstName;
    String lastName;
    double empSalary;
}
