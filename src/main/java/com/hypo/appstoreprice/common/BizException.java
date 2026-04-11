package com.hypo.appstoreprice.common;

import cn.hutool.core.util.StrUtil;

/**
 * 業務異常
 *
 * @author hypo
 * @date 2022-01-08
 */
public class BizException extends RuntimeException {

    public BizException(String message, Object... params) {
        super(StrUtil.format(message, params));
    }

}
