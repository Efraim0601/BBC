package com.bbc.sms.platform.common;

import com.bbc.sms.health.dto.HealthDtos.HealthRecordUpsert;
import com.bbc.sms.classkit.dto.ClassKitDtos.ItemUpsert;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OperationalInputValidationTest {
    @Test void measurementsAndClassSupplyAmountsCannotBeNegative(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();
            assertThat(validator.validate(new HealthRecordUpsert(null,null,null,null,null,null,-1,-5))).hasSize(2);
            assertThat(validator.validate(new HealthRecordUpsert(null,null,null,null,null,null,null,null))).isEmpty();
            assertThat(validator.validate(new ItemUpsert("QA notebook",-3,-100L,null,null,null,null))).hasSize(2);
            assertThat(validator.validate(new ItemUpsert("Free book",null,0L,null,null,null,true))).isEmpty();
        }
    }
}
