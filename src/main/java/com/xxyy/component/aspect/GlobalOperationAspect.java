package com.xxyy.component.aspect;

import com.xxyy.annotation.GlobalInterceptor;
import com.xxyy.annotation.VerifyParams;
import com.xxyy.entity.enums.ResponseCodeEnums;
import com.xxyy.utils.RedisConstants;
import com.xxyy.utils.StringTools;
import com.xxyy.utils.common.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * @author xy
 * @date 2024-09-18 16:21
 */

@Aspect
@Slf4j
@Component
public class GlobalOperationAspect {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    private static final String[] WRAPPER_CLASS_NAMES = {
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Double",
            "java.lang.String"
    };

    @Pointcut("@annotation(com.xxyy.annotation.GlobalInterceptor)")
    public void requestInterceptor() {}

    /**
     *  Object target = point.getTarget();
     *  获取被拦截的目标对象（即实际被代理的类实例）
     *  需要获得方法上的注解，需要获得Method类
     */
    @Around("requestInterceptor()")
    public Object interceptorDO(ProceedingJoinPoint point) throws AppException, NoSuchMethodException {
        // 获得参数
        Object[] args = point.getArgs();
        // 获得方法名称
        String methodName = point.getSignature().getName();
        // 获得方法参数
        Class<?>[] parameterTypes = ((MethodSignature) point.getSignature()).getMethod().getParameterTypes();
        // 获得方法
        Method method = point.getTarget().getClass().getMethod(methodName, parameterTypes);
        // 获得GlobalInterceptor注解
        GlobalInterceptor methodAnnotation = method.getAnnotation(GlobalInterceptor.class);
        try {
            // TODO: 2024/9/18 登录校验 只有登录才能是管理员
            if (methodAnnotation.checkLoing() || methodAnnotation.checkAdmin()) {
                checkLogin(methodAnnotation.checkAdmin());
            }
            // 参数校验
            if (methodAnnotation.checkParams()) {
                validateParams(method, args);
            }
            return point.proceed();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ResponseCodeEnums.CODE_500);
        } catch (Throwable e) {
            log.error("方法处理业务错误", e);
            throw new AppException(ResponseCodeEnums.CODE_500);
        }
    }

    private void checkLogin(boolean admin) {
        // 获取用户登录信息
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String token = requestAttributes.getRequest().getHeader("authorization");
        String str = (String) stringRedisTemplate.opsForHash().entries(RedisConstants.MYPAN_LOGIN_USER_KEY + token).get("admin");
        if (token == null || StringTools.isEmpty(str)) {
            throw new AppException(ResponseCodeEnums.CODE_901);
        }
        boolean isAdmin = Boolean.parseBoolean(str);
        if (admin && !isAdmin) {
            throw new AppException(ResponseCodeEnums.CODE_404);
        }
    }

    private void validateParams(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = args[i];
            VerifyParams verifyParams = parameter.getAnnotation(VerifyParams.class);
            if (verifyParams == null) {
                continue;
            }
            // 判断是对象还是基本类
            if (ArrayUtils.contains(WRAPPER_CLASS_NAMES, parameter.getParameterizedType().getTypeName())) {
                checkValue(arg, verifyParams);
            } else {
                // TODO: 2024/9/19 对象类型验证
                checkObject(arg, verifyParams);
            }
        }
    }

    private void checkObject(Object arg, VerifyParams verifyParams) {
        Field[] fields = arg.getClass().getDeclaredFields();
        try {
            for (Field field : fields) {
                field.setAccessible(true);
                Object obj = field.get(arg);
                if (obj == null) {
                    continue;
                }
                checkValue(obj, verifyParams);
            }
        } catch (IllegalAccessException e) {
            log.error("参数校验异常");
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
    }

    private void checkValue(Object arg, VerifyParams verifyParams) {
        // TODO 传入0 isEmpty是true
        boolean isEmpty = arg == null || !StringUtils.hasLength(String.valueOf(arg));
        int length = arg == null ? 0 : arg.toString().length();
        // 校验空
        if (isEmpty && verifyParams.required()) {
            log.error("参数校验异常");
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        //  校验长度
        if (!isEmpty && (verifyParams.min() != -1 && verifyParams.min() > length || verifyParams.max() != -1 && verifyParams.max() < length)) {
            log.error("参数校验异常");
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
        //  正则校验
        if (!isEmpty && (StringUtils.hasLength(verifyParams.regex().getPattern()) && !verifyParams.regex().validate(String.valueOf(arg)))) {
            log.error("参数校验异常");
            throw new AppException(ResponseCodeEnums.CODE_600);
        }
    }
}
