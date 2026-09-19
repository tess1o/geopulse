package org.github.tess1o.geopulse.shared.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ApiViolationCodeTest {

    @Test
    void keysAreDottedLowerCamelCase() {
        assertThat(ApiViolationCode.NOT_BLANK.messageKey()).isEqualTo("validation.notBlank");
        assertThat(ApiViolationCode.SIZE.messageKey()).isEqualTo("validation.size");
        assertThat(ApiViolationCode.DECIMAL_MAX.messageKey()).isEqualTo("validation.decimalMax");
        assertThat(ApiViolationCode.POSITIVE_OR_ZERO.messageKey()).isEqualTo("validation.positiveOrZero");
        assertThat(ApiViolationCode.VALID_AREA_BOUNDS.messageKey()).isEqualTo("validation.validAreaBounds");
        assertThat(ApiViolationCode.INVALID_VALUE.messageKey()).isEqualTo("validation.invalidValue");
    }

    @Test
    void standardJakartaConstraintsAreMappedBySimpleName() throws Exception {
        assertThat(codeOf("negative")).isEqualTo(ApiViolationCode.NEGATIVE);
        assertThat(codeOf("negativeOrZero")).isEqualTo(ApiViolationCode.NEGATIVE_OR_ZERO);
        assertThat(codeOf("positiveOrZero")).isEqualTo(ApiViolationCode.POSITIVE_OR_ZERO);
        assertThat(codeOf("past")).isEqualTo(ApiViolationCode.PAST);
        assertThat(codeOf("futureOrPresent")).isEqualTo(ApiViolationCode.FUTURE_OR_PRESENT);
        assertThat(codeOf("digits")).isEqualTo(ApiViolationCode.DIGITS);
    }

    @Test
    void constraintOutsideTheVocabularyCollapsesToInvalidValue() throws Exception {
        assertThat(codeOf("custom")).isEqualTo(ApiViolationCode.INVALID_VALUE);
    }

    private static ApiViolationCode codeOf(String fieldName) throws Exception {
        Field field = Sample.class.getDeclaredField(fieldName);
        return ApiViolationCode.from(field.getAnnotations()[0]);
    }

    @Retention(RUNTIME)
    @Target(FIELD)
    @interface SomethingCustom {
    }

    @SuppressWarnings("unused")
    private static final class Sample {
        @SomethingCustom
        String custom;

        @jakarta.validation.constraints.Negative
        Integer negative;

        @jakarta.validation.constraints.NegativeOrZero
        Integer negativeOrZero;

        @jakarta.validation.constraints.PositiveOrZero
        Integer positiveOrZero;

        @jakarta.validation.constraints.Past
        java.time.Instant past;

        @jakarta.validation.constraints.FutureOrPresent
        java.time.Instant futureOrPresent;

        @jakarta.validation.constraints.Digits(integer = 3, fraction = 2)
        java.math.BigDecimal digits;
    }
}
