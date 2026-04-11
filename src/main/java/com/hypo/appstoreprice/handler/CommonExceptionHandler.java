package com.hypo.appstoreprice.handler;

import cn.hutool.core.collection.CollUtil;
import com.hypo.appstoreprice.common.BizException;
import com.hypo.appstoreprice.common.LogUtil;
import com.hypo.appstoreprice.pojo.bean.R;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

/**
 * 統一異常處理
 *
 * @author hypo
 * @date 2022-01-08
 */
@Slf4j
@RestControllerAdvice
public class CommonExceptionHandler {

    /**
     * 自訂服務異常
     *
     * @param e e
     * @return {@link R}
     */
    @ExceptionHandler(value = BizException.class)
    public R bizExceptionHandler(BizException e) {
        return R.failed(e.getMessage());
    }

    /**
     * 校驗未通過異常處理
     *
     * @param e e
     * @return {@link R}
     */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public R methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e) {
        BindingResult result = e.getBindingResult();
        if (CollUtil.isNotEmpty(result.getAllErrors())) {
            return R.failed(result.getAllErrors().get(0).getDefaultMessage());
        }
        return R.failed("參數不正確");
    }

    /**
     * 系統異常
     *
     * @param e e
     * @return {@link R}
     */
    @ExceptionHandler(value = Exception.class)
    public R exceptionHandler(Exception e) {
        LogUtil.error(log, "系統異常", e);
        return R.failed("系統異常");
    }

    /**
     * 客戶端終止異常
     *
     * @param e e
     */
    @SuppressWarnings("all")
    @ExceptionHandler(value = ClientAbortException.class)
    public void clientAbortExceptionHandler(ClientAbortException e) {
        if (e.getCause().getClass().equals(IOException.class)) {
            // 寫操作 IO 異常幾乎總是由於客戶端主動關閉連接導致，忽略
        } else {
            LogUtil.error(log, "ClientAbortException", e);
        }
    }

}
