package com.zyj.asynctransaction;

/**
 * 异步任务执行结果枚举
 *
 * @author lulx
 * @date 2022-08-30 15:22
 **/
public enum AsyncTaskResult {
    /** 执行中 */
    RUNNING,
    /** 成功 */
    SUCCESS,
    /** 失败 */
    FAIL,
    /** 取消 */
    CANCELED,
    /** 超时 */
    TIMEOUT;
}
