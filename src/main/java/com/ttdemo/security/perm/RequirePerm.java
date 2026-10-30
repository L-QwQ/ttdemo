package com.ttdemo.security.perm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级权限点校验，由 PermAspect 拦截。
 * 用法：{@code @RequirePerm(Permissions.PREDICTION_AUDIT)}
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    String value();
}
