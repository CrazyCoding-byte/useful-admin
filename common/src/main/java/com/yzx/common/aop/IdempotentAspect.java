package com.yzx.common.aop;

import com.yzx.model.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.SourceLocation;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @className: IdempotentAspect
 * @author: yzx
 * @date: 2026/1/9 6:04
 * @Version: 1.0
 * @description:
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotentAspect {
    private final StringRedisTemplate redisTemplate;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    @PostConstruct
    public void init() {
        System.out.println("====== IdempotentAspect 已加载 ======");
    }
    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 1. 获取注解属性
        String keyExpression = idempotent.key();
        long expireTime = idempotent.expireTime();
        TimeUnit timeUnit = idempotent.timeUnit();
        String errorMessage = idempotent.message();
        String keyPrefix = idempotent.prefix();
        //2解析spl表达式生成唯一的幂值
        String dynamicKey = parseSpel(keyExpression, joinPoint);
        if (!StringUtils.hasText(dynamicKey)) {
            throw new BusinessException("幂等键解析失败，请检查@Idempotent的key表达式");
        }
        // 拼接统一前缀，避免Key冲突
        String idempotentKey = keyPrefix + dynamicKey;
        log.info("解析幂等键成功，最终key：{}", idempotentKey);

        // 4. 生成分布式锁唯一Value（防误删）
        String lockValue = UUID.randomUUID().toString();
        Boolean isFirstExecute = false;
        try{

             isFirstExecute = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", expireTime, timeUnit);
        }catch (Exception e){
            // Redis异常降级：打印告警，放行业务（避免Redis宕机导致业务不可用）
            log.error("Redis操作异常，幂等校验降级，key：{}", idempotentKey, e);
            return joinPoint.proceed();
        }
        //幂等键已存在，说明已经执行过
        if (Boolean.FALSE.equals(isFirstExecute)) {
            log.warn("重复执行幂等方法，幂等键：{}", idempotentKey);
            throw new BusinessException(errorMessage);
        }
        // 5. 第一次执行：放行，执行原方法
        try {
            return joinPoint.proceed(); // 执行业务方法
        } catch (Exception e) {
            // 6. 业务执行失败：删除幂等键，允许重试
            log.error("业务方法执行失败，删除幂等键：{}", idempotentKey, e);
            deleteIdempotentKeySafely(idempotentKey, lockValue);
            throw e; // 抛出异常，让上层处理
        }
    }

    /** 安全删除幂等键（防误删） */
    private void deleteIdempotentKeySafely(String key, String value) {
        try {
            String currentValue = redisTemplate.opsForValue().get(key);
            if (value.equals(currentValue)) {
                redisTemplate.delete(key);
                log.info("安全删除幂等键成功：{}", key);
            } else {
                log.warn("幂等键值不匹配，跳过删除：{}", key);
            }
        } catch (Exception e) {
            log.error("删除幂等键失败：{}", key, e);
        }
    }

    /**
     * 解析SpEL表达式
     *
     * @param expression 表达式
     * @param joinPoint  连接点
     * @return 解析结果
     */
    private String parseSpel(String expression, ProceedingJoinPoint joinPoint) {
        //获取目标方法
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        // 构建SpEL上下文
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null && args != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        // 解析表达式，空值兜底
        Object result = expressionParser.parseExpression(expression).getValue(context);
        return result == null ? "" : result.toString().trim();
        /**
         * // 有3个参数的方法
         * @Idempotent(key = "'TEST_'+#userId+'_'+#lockDTO.orderNo+'_'+#productId", message = "重复操作")
         * public void testMethod(String userId, InventoryLockDTO lockDTO, String productId) {
         *     // userId = "u1001"
         *     // lockDTO = InventoryLockDTO(orderNo="20260109", quantity=5)
         *     // productId = "p2001"
         * }
         *
         * // parameterNames = ["userId", "lockDTO", "productId"]
         * // args = ["u1001", lockDTO对象, "p2001"]
         * for (int i = 0; i < parameterNames.length; i++) {
         *     context.setVariable(parameterNames[i], args[i]);
         *     // 执行后：
         *     // context里有：userId→u1001，lockDTO→lockDTO对象，productId→p2001
         * }
         * 会根据注解的属性拼接幂等键
         */
        // 解析SpEL表达式，返回字符串结果
    }
}
