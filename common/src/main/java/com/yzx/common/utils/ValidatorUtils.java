package com.yzx.common.utils;

import com.yzx.model.utils.SpringUtils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.Set;
import javax.validation.Validator;

/**
 * Validator 校验框架工具
 *
 * @author Lion Li
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ValidatorUtils {

    private static final Validator VALID = SpringUtils.getBean(Validator.class);

    /**
     * 对给定对象进行参数校验，并根据指定的校验组进行校验
     *
     * @param object 要进行校验的对象
     * @param groups 校验组
     * @throws ConstraintViolationException 如果校验不通过，则抛出参数校验异常
     */
    public static <T> void validate(T object, Class<?>... groups) {
        Class<?>[] groupClasses = groups;
        Set<ConstraintViolation<T>> violationSet = VALID.validate(object, groupClasses);
        if (!violationSet.isEmpty()) {
            throw new ConstraintViolationException(violationSet);
        }
    }

}
