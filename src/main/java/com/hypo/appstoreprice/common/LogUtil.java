package com.hypo.appstoreprice.common;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import lombok.experimental.UtilityClass;
import org.slf4j.Logger;

/**
 * 日誌記錄工具, 格式化輸出
 *
 * @author hypo
 * @date 2022-03-05
 */
@UtilityClass
public class LogUtil {

    private static final String LOGGER_FORMAT = "[{}] → {}";

    private static final String LOGGER_FORMAT_WITHOUT_PARAMS = "[{}]";

    /**
     * INFO 日誌記錄
     *
     * @param logger logger
     * @param title  title
     * @param params params
     */
    public void info(final Logger logger, String title, Object... params) {
        if (logger.isInfoEnabled()) {
            if (ArrayUtil.isEmpty(params)) {
                logger.info(LOGGER_FORMAT_WITHOUT_PARAMS, title);
            } else {
                logger.info(LOGGER_FORMAT, title, formatLogMessage(params));
            }
        }
    }

    /**
     * ERROR 日誌記錄
     *
     * @param logger logger
     * @param title  title
     * @param params params
     */
    public void error(final Logger logger, String title, Object... params) {
        if (logger.isErrorEnabled()) {
            if (ArrayUtil.isEmpty(params)) {
                logger.error(LOGGER_FORMAT_WITHOUT_PARAMS, title);
            } else {
                logger.error(LOGGER_FORMAT, title, formatLogMessage(params));
            }
        }
    }

    /**
     * ERROR 日誌記錄
     *
     * @param logger logger
     * @param title  title
     * @param params params
     * @param e      e
     */
    public void error(final Logger logger, String title, Exception e, Object... params) {
        if (logger.isErrorEnabled()) {
            if (ArrayUtil.isEmpty(params)) {
                logger.error(LOGGER_FORMAT_WITHOUT_PARAMS, title, e);
            } else {
                logger.error(LOGGER_FORMAT, title, formatLogMessage(params), e);
            }
        }
    }

    /**
     * WARN 日誌記錄
     *
     * @param logger logger
     * @param title  title
     * @param params params
     */
    public void warn(final Logger logger, String title, Object... params) {
        if (logger.isWarnEnabled()) {
            if (ArrayUtil.isEmpty(params)) {
                logger.warn(LOGGER_FORMAT_WITHOUT_PARAMS, title);
            } else {
                logger.warn(LOGGER_FORMAT, title, formatLogMessage(params));
            }
        }
    }

    /**
     * DEBUG 日誌記錄
     *
     * @param logger logger
     * @param title  title
     * @param params params
     */
    public void debug(final Logger logger, String title, Object... params) {
        if (logger.isDebugEnabled()) {
            if (ArrayUtil.isEmpty(params)) {
                logger.debug(LOGGER_FORMAT_WITHOUT_PARAMS, title);
            } else {
                logger.debug(LOGGER_FORMAT, title, formatLogMessage(params));
            }
        }
    }

    /**
     * 格式化日誌記錄資訊，奇位參數作為 KEY，偶位作為 VALUE
     * 當傳入參數不是偶數個時，最後一個參數組的 VALUE 預設為 ""
     *
     * @param params params
     * @return String
     */
    private String formatLogMessage(Object... params) {
        StringBuilder sb = new StringBuilder();
        if (ArrayUtil.isEmpty(params)) {
            return StrUtil.EMPTY;
        }
        for (int index = 0; index < params.length; index += 2) {
            Object key = params[index];
            Object value;
            if (index == params.length - 1) {
                value = "NULL-日誌調用方未傳入參數值";
            } else {
                value = params[index + 1];
            }
            if (index == params.length - 2) {
                sb.append(key).append(":").append(value);
            } else {
                sb.append(key).append(":").append(value).append(", ");
            }
        }
        return sb.toString();
    }

}
